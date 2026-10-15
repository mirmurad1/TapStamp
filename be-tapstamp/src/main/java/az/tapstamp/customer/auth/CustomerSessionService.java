package az.tapstamp.customer.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.customer.repository.CustomerSessionMapper;
import az.tapstamp.security.token.SecureTokens;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.web.ClientInfo;
import az.tapstamp.shared.web.DeviceLabels;
import az.tapstamp.tenancy.TenantContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Müştəri sessiyaları: qısaömürlü access token (15 dəq) + uzunömürlü refresh token (180 gün, sürüşən).
 *
 * <p>Refresh token dəyişmir (rotasiya yoxdur): hər yeniləmədə yalnız yeni access token verilir, refresh tokenin müddəti
 * isə yenidən 180 gün olur. Müştəri özü çıxana, sessiya ləğv olunana və ya 180 gün tətbiqə girmədikdə sessiya bitir.
 * Rotasiyadan imtina edildi: telefon yeniləmə cavabını ala bilmədikdə (məs. iPhone tətbiqi sorğu zamanı arxa plana
 * keçib) köhnə tokenin yenidən gəlməsi oğurluq sayılır və müştəri səbəbsiz çıxarılırdı.
 * Access token hər sorğuda yoxlanılır (ən çox 15 saniyəlik keşlə; ləğvetmədə bu instansiyanın keşi dərhal təmizlənir).
 */
@Service
public class CustomerSessionService {

    public record IssuedTokens(UUID sessionId, String accessToken, String refreshToken) {
        @Override
        public @NonNull String toString() {
            return "IssuedTokens[sessionId=" + sessionId + "]";
        }
    }

    private record CachedSession(UUID tenantId, CustomerPrincipal principal, Instant accessExpiresAt,
                                 Instant lastSeenAt) {
    }

    private final CustomerSessionMapper sessions;
    private final CustomerAuthProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Cache<String, CachedSession> cache;

    public CustomerSessionService(CustomerSessionMapper sessions, CustomerAuthProperties properties,
                                  PlatformTransactionManager transactionManager, Clock clock) {
        this.sessions = sessions;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(properties.accessCacheTtl())
                .build();
    }

    /** Yeni sessiya (hər girişdə — session fixation mümkün deyil). Tranzaksiya daxilində və ya xaricində çağırıla bilər. */
    public IssuedTokens open(UUID customerId, ClientInfo client) {
        Instant now = clock.instant();
        UUID sessionId = Ids.newId();
        String access = SecureTokens.generate();
        String refresh = SecureTokens.generate();
        sessions.insertSession(sessionId, customerId, SecureTokens.hash(access), now.plus(properties.accessTtl()),
                now.plus(properties.refreshTtl()), DeviceLabels.describe(client.userAgent()), client.userAgent(),
                client.ip(), now);
        sessions.insertRefreshToken(Ids.newId(), sessionId, SecureTokens.hash(refresh), now,
                now.plus(properties.refreshTtl()));
        return new IssuedTokens(sessionId, access, refresh);
    }

    /** Access tokeni yoxlayır. Vaxtı keçmiş, ləğv olunmuş və ya müştərisi aktiv olmayan sessiya üçün boş qaytarır. */
    public Optional<CustomerPrincipal> authenticate(String accessToken, ClientInfo client) {
        UUID tenantId = TenantContext.require().id();
        byte[] hash = SecureTokens.hash(accessToken);
        String key = tenantId + ":" + HexFormat.of().formatHex(hash);
        Instant now = clock.instant();
        CachedSession cached = cache.getIfPresent(key);
        if (cached == null) {
            Optional<CustomerSessionMapper.ActiveSession> found = sessions.findByAccessHash(hash)
                    .filter(s -> s.revokedAt() == null && "ACTIVE".equals(s.customerStatus()));
            if (found.isEmpty()) {
                return Optional.empty();
            }
            CustomerSessionMapper.ActiveSession session = found.get();
            cached = new CachedSession(tenantId, new CustomerPrincipal(session.customerId(), session.sessionId(),
                    session.language(), session.stepUpAt()), session.accessExpiresAt(), session.lastSeenAt());
            cache.put(key, cached);
        }
        if (!cached.accessExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        if (cached.lastSeenAt() == null || cached.lastSeenAt().plusSeconds(60).isBefore(now)) {
            sessions.touch(cached.principal().sessionId(), client.ip(), now);
            cache.put(key, new CachedSession(tenantId, cached.principal(), cached.accessExpiresAt(), now));
        }
        return Optional.of(cached.principal());
    }

    /**
     * Yeni access token; refresh token eyni qalır, müddəti 180 gün uzanır (bax: sinifin təsviri). Cavabda refresh
     * cookie-si də yenidən yazılır ki, brauzerdəki müddəti də sürüşsün.
     */
    public IssuedTokens refresh(String refreshToken, ClientInfo client) {
        Instant now = clock.instant();
        IssuedTokens tokens = Objects.requireNonNull(tx.execute(status -> renew(refreshToken, client, now)));
        evictSession(tokens.sessionId());
        return tokens;
    }

    private IssuedTokens renew(String refreshToken, ClientInfo client, Instant now) {
        CustomerSessionMapper.RefreshRow row = sessions.lockRefreshToken(SecureTokens.hash(refreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
        if (row.sessionRevokedAt() != null || !row.expiresAt().isAfter(now)
                || !row.sessionRefreshExpiresAt().isAfter(now) || !"ACTIVE".equals(row.customerStatus())) {
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }
        Instant refreshExpiresAt = now.plus(properties.refreshTtl());
        sessions.extendRefreshToken(row.tokenId(), refreshExpiresAt);
        String access = SecureTokens.generate();
        sessions.rotateAccess(row.sessionId(), SecureTokens.hash(access), now.plus(properties.accessTtl()),
                refreshExpiresAt, client.ip(), now);
        return new IssuedTokens(row.sessionId(), access, refreshToken);
    }

    /** Çıxış: cari sessiya access token və ya (access token-in vaxtı keçibsə) refresh token ilə tapılır. */
    public void logout(Optional<String> accessToken, Optional<String> refreshToken) {
        Instant now = clock.instant();
        Optional<UUID> sessionId = accessToken.flatMap(token -> sessions.findByAccessHash(SecureTokens.hash(token)))
                .map(CustomerSessionMapper.ActiveSession::sessionId);
        if (sessionId.isEmpty() && refreshToken.isPresent()) {
            sessionId = Objects.requireNonNull(tx.execute(status -> sessions.lockRefreshToken(
                    SecureTokens.hash(refreshToken.get())).map(CustomerSessionMapper.RefreshRow::sessionId)));
        }
        sessionId.ifPresent(id -> {
            sessions.revoke(id, "LOGOUT", now);
            evictSession(id);
        });
    }

    public List<CustomerSessionMapper.SessionView> list(CustomerPrincipal principal) {
        return sessions.listActive(principal.customerId(), clock.instant());
    }

    public void revoke(CustomerPrincipal principal, UUID sessionId) {
        boolean own = list(principal).stream().anyMatch(session -> session.id().equals(sessionId));
        if (!own) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        sessions.revoke(sessionId, "REVOKED_BY_CUSTOMER", clock.instant());
        evictSession(sessionId);
    }

    public int revokeOthers(CustomerPrincipal principal) {
        List<UUID> revoked = sessions.revokeAll(principal.customerId(), "REVOKED_BY_CUSTOMER", clock.instant(),
                principal.sessionId());
        revoked.forEach(this::evictSession);
        return revoked.size();
    }

    public void revokeAll(UUID customerId, String reason) {
        sessions.revokeAll(customerId, reason, clock.instant(), null).forEach(this::evictSession);
    }

    public void markStepUp(CustomerPrincipal principal) {
        sessions.markStepUp(principal.sessionId(), clock.instant());
        evictSession(principal.sessionId());
    }

    public void requireStepUp(CustomerPrincipal principal) {
        Instant stepUpAt = principal.stepUpAt();
        if (stepUpAt == null || stepUpAt.plus(properties.stepUpValidity()).isBefore(clock.instant())) {
            throw new ApiException(ErrorCode.STEP_UP_REQUIRED);
        }
    }

    /** Profil dəyişəndə (məs. dil) keşdəki köhnə məlumat qalmasın. */
    public void evictCustomer(UUID customerId) {
        cache.asMap().values().removeIf(entry -> entry.principal().customerId().equals(customerId));
    }

    private void evictSession(UUID sessionId) {
        cache.asMap().values().removeIf(entry -> entry.principal().sessionId().equals(sessionId));
    }
}
