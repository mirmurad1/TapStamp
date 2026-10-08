package az.cafeloyalty.customer.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import az.cafeloyalty.audit.AuditLogger;
import az.cafeloyalty.security.token.SecureTokens;
import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import az.cafeloyalty.shared.id.Ids;
import az.cafeloyalty.shared.web.ClientInfo;
import az.cafeloyalty.shared.web.DeviceLabels;
import az.cafeloyalty.tenancy.TenantContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Müştəri sessiyaları: qısaömürlü access token (15 dəq) + uzunömürlü refresh token (180 gün, sürüşən, hər istifadədə
 * yenilənir).
 *
 * <p>Refresh rotasiyası:
 * <ul>
 *   <li>aktiv token istifadə olunur → yeni access və refresh tokenlər;</li>
 *   <li>token son 10 saniyədə artıq istifadə olunubsa — iki tab eyni anda yeniləyib: sessiya ləğv olunmur,
 *       {@code REFRESH_RACE} (klient sorğunu təkrarlayır, yeni cookie artıq brauzerdədir);</li>
 *   <li>token daha əvvəl istifadə olunubsa — oğurlanmış tokenin təkrar istifadəsi: bütün sessiya ləğv olunur.</li>
 * </ul>
 * Access token hər sorğuda yoxlanılır (ən çox 15 saniyəlik keşlə; ləğvetmədə bu instansiyanın keşi dərhal təmizlənir).
 */
@Service
public class CustomerSessionService {

    public record IssuedTokens(UUID sessionId, String accessToken, String refreshToken) {
        @Override
        public String toString() {
            return "IssuedTokens[sessionId=" + sessionId + "]";
        }
    }

    private record CachedSession(UUID tenantId, CustomerPrincipal principal, Instant accessExpiresAt,
                                 Instant lastSeenAt) {
    }

    private static final Logger log = LoggerFactory.getLogger(CustomerSessionService.class);

    private final CustomerSessionStore sessions;
    private final CustomerAuthProperties properties;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Cache<String, CachedSession> cache;

    public CustomerSessionService(CustomerSessionStore sessions, CustomerAuthProperties properties, AuditLogger audit,
                                  PlatformTransactionManager transactionManager, Clock clock) {
        this.sessions = sessions;
        this.properties = properties;
        this.audit = audit;
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
            Optional<CustomerSessionStore.ActiveSession> found = sessions.findByAccessHash(hash)
                    .filter(s -> s.revokedAt() == null && "ACTIVE".equals(s.customerStatus()));
            if (found.isEmpty()) {
                return Optional.empty();
            }
            CustomerSessionStore.ActiveSession session = found.get();
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

    /** Refresh token rotasiyası (bax: sinifin təsviri). */
    public IssuedTokens refresh(String refreshToken, ClientInfo client) {
        Instant now = clock.instant();
        Outcome outcome = tx.execute(status -> rotate(refreshToken, client, now));
        if (outcome.reuseDetectedSession() != null) {
            evictSession(outcome.reuseDetectedSession());
            log.warn("Refresh tokenin təkrar istifadəsi aşkarlandı: sessiya ləğv olundu ({})", outcome.reuseDetectedSession());
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }
        if (outcome.error() != null) {
            throw new ApiException(outcome.error());
        }
        return outcome.tokens();
    }

    private record Outcome(IssuedTokens tokens, ErrorCode error, UUID reuseDetectedSession) {
    }

    private Outcome rotate(String refreshToken, ClientInfo client, Instant now) {
        Optional<CustomerSessionStore.RefreshRow> found = sessions.lockRefreshToken(SecureTokens.hash(refreshToken));
        if (found.isEmpty()) {
            return new Outcome(null, ErrorCode.SESSION_EXPIRED, null);
        }
        CustomerSessionStore.RefreshRow row = found.get();
        if (row.sessionRevokedAt() != null || !row.expiresAt().isAfter(now)
                || !row.sessionRefreshExpiresAt().isAfter(now) || !"ACTIVE".equals(row.customerStatus())) {
            return new Outcome(null, ErrorCode.SESSION_EXPIRED, null);
        }
        if (row.consumedAt() != null) {
            if (row.consumedAt().plus(properties.refreshRaceWindow()).isAfter(now)) {
                return new Outcome(null, ErrorCode.REFRESH_RACE, null);
            }
            // Köhnə (artıq dəyişdirilmiş) token yenidən gəlib — oğurlanma əlaməti: sessiya bütünlüklə bağlanır
            sessions.revoke(row.sessionId(), "REFRESH_REUSE", now);
            audit.tenant("CUSTOMER_REFRESH_REUSE_DETECTED", AuditLogger.ActorType.SYSTEM, null, "session",
                    row.sessionId(), Map.of("customerId", row.customerId().toString()));
            return new Outcome(null, null, row.sessionId());
        }
        sessions.consumeRefreshToken(row.tokenId(), now);
        String access = SecureTokens.generate();
        String refresh = SecureTokens.generate();
        sessions.rotateAccess(row.sessionId(), SecureTokens.hash(access), now.plus(properties.accessTtl()),
                now.plus(properties.refreshTtl()), client.ip(), now);
        sessions.insertRefreshToken(Ids.newId(), row.sessionId(), SecureTokens.hash(refresh), now,
                now.plus(properties.refreshTtl()));
        evictSession(row.sessionId());
        return new Outcome(new IssuedTokens(row.sessionId(), access, refresh), null, null);
    }

    /** Çıxış: cari sessiya access token və ya (access token-in vaxtı keçibsə) refresh token ilə tapılır. */
    public void logout(Optional<String> accessToken, Optional<String> refreshToken) {
        Instant now = clock.instant();
        Optional<UUID> sessionId = accessToken.flatMap(token -> sessions.findByAccessHash(SecureTokens.hash(token)))
                .map(CustomerSessionStore.ActiveSession::sessionId);
        if (sessionId.isEmpty() && refreshToken.isPresent()) {
            sessionId = tx.execute(status -> sessions.lockRefreshToken(SecureTokens.hash(refreshToken.get()))
                    .map(CustomerSessionStore.RefreshRow::sessionId));
        }
        sessionId.ifPresent(id -> {
            sessions.revoke(id, "LOGOUT", now);
            evictSession(id);
        });
    }

    public List<CustomerSessionStore.SessionView> list(CustomerPrincipal principal) {
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
