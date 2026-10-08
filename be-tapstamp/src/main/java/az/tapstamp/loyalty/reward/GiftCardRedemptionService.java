package az.tapstamp.loyalty.reward;

import static az.tapstamp.shared.persistence.SqlTime.instant;
import static az.tapstamp.shared.persistence.SqlTime.ts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.loyalty.reward.GiftCardService.Status;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.ratelimit.RateLimitPolicy;
import az.tapstamp.security.ratelimit.RateLimiter;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.LocalizedTextConverter;
import az.tapstamp.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
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
        public String toString() {
            return "GiftCardCheck[id=" + id + ", status=" + status + "]";
        }
    }

    private static final LocalizedTextConverter TEXT = new LocalizedTextConverter();
    private static final String SELECT = """
            SELECT r.id, r.code_nonce, r.status, r.reward_name_snapshot::text AS name, r.earned_at, r.expires_at,
                   r.redeemed_at, r.redeemed_by, r.redeemed_branch_id, c.name AS customer_name, c.phone_masked,
                   b.name::text AS branch_name, r.customer_id
            FROM rewards r
            JOIN customers c ON c.id = r.customer_id
            LEFT JOIN branches b ON b.id = r.redeemed_branch_id
            """;

    private record Row(GiftCardCheck check, UUID redeemedBy, UUID redeemedBranchId, UUID customerId) {
    }

    private final JdbcClient jdbc;
    private final GiftCardCodes codes;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public GiftCardRedemptionService(@Qualifier("tenantJdbc") JdbcClient jdbc, GiftCardCodes codes,
                                     RateLimiter rateLimiter, AuditLogger audit,
                                     PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.codes = codes;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Kodun yoxlanması (yan təsiri yoxdur): kart tapılırsa statusu ilə qaytarılır. */
    public GiftCardCheck check(AdminPrincipal principal, String rawCode) {
        byte[] lookup = lookup(principal, rawCode);
        return find(lookup, false).map(Row::check).orElseThrow(() -> notFound(principal));
    }

    /** Hədiyyənin təhvili: kart birdəfəlik REDEEMED olur, filial və işçi qeyd olunur. */
    public GiftCardCheck redeem(AdminPrincipal principal, String rawCode, UUID branchId) {
        AccessPolicy.requireBranch(principal, branchId);
        byte[] lookup = lookup(principal, rawCode);
        Instant now = clock.instant();
        Optional<GiftCardCheck> result = tx.execute(status -> {
            Optional<Row> found = find(lookup, true);
            if (found.isEmpty()) {
                return Optional.<GiftCardCheck>empty();
            }
            Row row = found.get();
            GiftCardCheck card = row.check();
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
            jdbc.sql("""
                            UPDATE rewards SET status = 'REDEEMED', redeemed_at = :now, redeemed_by = :staffId,
                                               redeemed_branch_id = :branchId
                            WHERE id = :id""")
                    .param("now", ts(now))
                    .param("staffId", principal.userId())
                    .param("branchId", branchId)
                    .param("id", card.id())
                    .update();
            jdbc.sql("""
                            UPDATE loyalty_accounts SET rewards_redeemed = rewards_redeemed + 1, updated_at = :now
                            WHERE customer_id = :customerId""")
                    .param("now", ts(now))
                    .param("customerId", row.customerId())
                    .update();
            audit.tenant("GIFT_CARD_REDEEMED", AuditLogger.ActorType.STAFF, principal.userId(), "reward", card.id(),
                    Map.of("branchId", branchId.toString()));
            return find(lookup, false).map(Row::check);
        });
        return result.orElseThrow(() -> notFound(principal));
    }

    /** Filialda son 24 saatda təhvil verilmiş kartlar (ən yenisi birinci). */
    public List<GiftCardCheck> recent(AdminPrincipal principal, UUID branchId) {
        AccessPolicy.requireBranch(principal, branchId);
        return jdbc.sql(SELECT + """
                        WHERE r.status = 'REDEEMED' AND r.redeemed_branch_id = :branchId AND r.redeemed_at > :since
                        ORDER BY r.redeemed_at DESC LIMIT 20""")
                .param("branchId", branchId)
                .param("since", ts(clock.instant().minus(Duration.ofDays(1))))
                .query((rs, n) -> map(rs).check())
                .list();
    }

    private byte[] lookup(AdminPrincipal principal, String rawCode) {
        UUID tenantId = TenantContext.require().id();
        rateLimiter.consume(RateLimitPolicy.GIFT_CARD_LOOKUP_PER_STAFF, "t:" + tenantId, principal.userId().toString());
        String code = GiftCardCodes.normalize(rawCode).orElseThrow(() -> notFound(principal));
        return codes.lookup(tenantId, code);
    }

    private Optional<Row> find(byte[] lookup, boolean forUpdate) {
        return jdbc.sql(SELECT + "WHERE r.code_lookup = :lookup" + (forUpdate ? " FOR UPDATE OF r" : ""))
                .param("lookup", lookup)
                .query((rs, n) -> map(rs))
                .optional();
    }

    private ApiException notFound(AdminPrincipal principal) {
        audit.tenant("GIFT_CARD_LOOKUP_FAILED", AuditLogger.ActorType.STAFF, principal.userId(), "reward", null,
                Map.of());
        return new ApiException(ErrorCode.GIFT_CARD_NOT_FOUND);
    }

    private Row map(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        Status status = Status.valueOf(rs.getString("status"));
        Instant expiresAt = instant(rs, "expires_at");
        if (status == Status.AVAILABLE && expiresAt != null && !expiresAt.isAfter(clock.instant())) {
            status = Status.EXPIRED;
        }
        String branch = rs.getString("branch_name");
        GiftCardCheck check = new GiftCardCheck(id,
                codes.code(TenantContext.require().id(), id, rs.getInt("code_nonce")), status,
                TEXT.convertToEntityAttribute(rs.getString("name")), rs.getString("customer_name"),
                rs.getString("phone_masked"), instant(rs, "earned_at"), expiresAt, instant(rs, "redeemed_at"),
                branch == null ? null : TEXT.convertToEntityAttribute(branch));
        return new Row(check, rs.getObject("redeemed_by", UUID.class), rs.getObject("redeemed_branch_id", UUID.class),
                rs.getObject("customer_id", UUID.class));
    }
}
