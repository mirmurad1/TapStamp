package az.tapstamp.loyalty.reward;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.loyalty.repository.GiftCardMapper.RedemptionRow;
import az.tapstamp.loyalty.repository.GiftCardMapper;
import az.tapstamp.loyalty.reward.GiftCardService.Status;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.ratelimit.RateLimitPolicy;
import az.tapstamp.security.ratelimit.RateLimiter;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.tenancy.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hədiyyə kartının işçi tərəfindən yoxlanılması və təhvili (plan §9.4, qərar 19).
 *
 * <ul>
 *   <li>Kodu yalnız daxil olmuş işçi yoxlaya bilər; işçi üzrə dəqiqədə 30 cəhd; tapılmayan kod auditə yazılır.
 *       Axtarış kodun HMAC-ı ilə aparılır — kodlar bazada açıq saxlanılmır. Başqa şirkətin kodu ayrı bazadadır.</li>
 *   <li>Təhvil: kart sətri kilidlənir; yalnız AVAILABLE və müddəti keçməmiş kart. Eyni işçinin eyni filialda təkrar
 *       basışı eyni nəticəni qaytarır (idempotent), fərqli təhvil cəhdi {@code GIFT_CARD_ALREADY_REDEEMED} alır.</li>
 * </ul>
 */
@Service
public class GiftCardRedemptionService {

    /** İşçinin ekranında göstərilən kart məlumatı (telefon yalnız maskalı). */
    public record GiftCardCheck(UUID id, String code, Status status, LocalizedText name, String customerName,
                                String phoneMasked, Instant earnedAt, Instant expiresAt, Instant redeemedAt,
                                LocalizedText redeemedBranchName) {
        @Override
        public @NonNull String toString() {
            return "GiftCardCheck[id=" + id + ", status=" + status + "]";
        }
    }

    private final GiftCardMapper giftCards;
    private final GiftCardCodes codes;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public GiftCardRedemptionService(GiftCardMapper giftCards, GiftCardCodes codes, RateLimiter rateLimiter,
                                     AuditLogger audit, PlatformTransactionManager transactionManager, Clock clock) {
        this.giftCards = giftCards;
        this.codes = codes;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Kodun yoxlanması (yan təsiri yoxdur): kart tapılırsa statusu ilə qaytarılır. */
    public GiftCardCheck check(AdminPrincipal principal, String rawCode) {
        byte[] lookup = lookup(principal, rawCode);
        return giftCards.findByLookup(lookup, false).map(this::toCheck).orElseThrow(() -> notFound(principal));
    }

    /** Hədiyyənin təhvili: kart birdəfəlik REDEEMED olur, filial və işçi qeyd olunur. */
    public GiftCardCheck redeem(AdminPrincipal principal, String rawCode, UUID branchId) {
        AccessPolicy.requireBranch(principal, branchId);
        byte[] lookup = lookup(principal, rawCode);
        Instant now = clock.instant();
        Optional<GiftCardCheck> result = tx.execute(status -> {
            Optional<RedemptionRow> found = giftCards.findByLookup(lookup, true);
            if (found.isEmpty()) {
                return Optional.<GiftCardCheck>empty();
            }
            RedemptionRow row = found.get();
            GiftCardCheck card = toCheck(row);
            switch (card.status()) {
                case REDEEMED -> {
                    if (principal.userId().equals(row.redeemedBy()) && branchId.equals(row.redeemedBranchId())) {
                        return Optional.of(card);
                    }
                    throw new ApiException(ErrorCode.GIFT_CARD_ALREADY_REDEEMED, new Object[0],
                            Map.of("redeemedAt", card.redeemedAt().toString()));
                }
                case EXPIRED -> throw new ApiException(ErrorCode.GIFT_CARD_EXPIRED);
                case REVOKED -> throw new ApiException(ErrorCode.GIFT_CARD_REVOKED);
                case AVAILABLE -> {
                    // davam
                }
            }
            giftCards.markRedeemed(card.id(), principal.userId(), branchId, now);
            giftCards.incrementRedeemedCount(row.customerId(), now);
            audit.tenant("GIFT_CARD_REDEEMED", AuditLogger.ActorType.STAFF, principal.userId(), "reward", card.id(),
                    Map.of("branchId", branchId.toString()));
            return giftCards.findByLookup(lookup, false).map(this::toCheck);
        });
        return result.orElseThrow(() -> notFound(principal));
    }

    /** Filialda son 24 saatda təhvil verilmiş kartlar (ən yenisi birinci). */
    public List<GiftCardCheck> recent(AdminPrincipal principal, UUID branchId) {
        AccessPolicy.requireBranch(principal, branchId);
        return giftCards.recentlyRedeemed(branchId, clock.instant().minus(Duration.ofDays(1))).stream()
                .map(this::toCheck)
                .toList();
    }

    private byte[] lookup(AdminPrincipal principal, String rawCode) {
        UUID tenantId = TenantContext.require().id();
        rateLimiter.consume(RateLimitPolicy.GIFT_CARD_LOOKUP_PER_STAFF, "t:" + tenantId, principal.userId().toString());
        String code = GiftCardCodes.normalize(rawCode).orElseThrow(() -> notFound(principal));
        return codes.lookup(tenantId, code);
    }

    private ApiException notFound(AdminPrincipal principal) {
        audit.tenant("GIFT_CARD_LOOKUP_FAILED", AuditLogger.ActorType.STAFF, principal.userId(), "reward", null,
                Map.of());
        return new ApiException(ErrorCode.GIFT_CARD_NOT_FOUND);
    }

    private GiftCardCheck toCheck(RedemptionRow row) {
        Status status = Status.valueOf(row.status());
        // Müddəti keçmiş kart fon işi onu yeniləməmişdən əvvəl də EXPIRED sayılır
        if (status == Status.AVAILABLE && row.expiresAt() != null && !row.expiresAt().isAfter(clock.instant())) {
            status = Status.EXPIRED;
        }
        return new GiftCardCheck(row.id(), codes.code(TenantContext.require().id(), row.id(), row.codeNonce()), status,
                row.name(), row.customerName(), row.phoneMasked(), row.earnedAt(), row.expiresAt(), row.redeemedAt(),
                row.branchName());
    }
}
