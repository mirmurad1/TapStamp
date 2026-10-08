package az.cafeloyalty.customer.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import az.cafeloyalty.audit.AuditLogger;
import az.cafeloyalty.company.service.CompanySettings;
import az.cafeloyalty.customer.auth.CustomerSessionService;
import az.cafeloyalty.loyalty.stamp.StampLedger;
import az.cafeloyalty.security.admin.AccessPolicy;
import az.cafeloyalty.security.admin.AdminAuthService;
import az.cafeloyalty.security.admin.AdminPrincipal;
import az.cafeloyalty.security.admin.AdminRole;
import az.cafeloyalty.security.crypto.Aad;
import az.cafeloyalty.security.crypto.EnvelopeCipher;
import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import az.cafeloyalty.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Müştəri üzərində admin əməliyyatları: tam nömrənin göstərilməsi (sahib + step-up), möhürlərin əl ilə düzəlişi
 * (səbəb + {@code Idempotency-Key}), bloklama və fırıldaq işarələri. Hamısı auditə yazılır.
 */
@Service
public class CustomerAdminService {

    public static final int MAX_ADJUSTMENT = 50;
    static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    public record Adjustment(int delta, String reason, UUID branchId) {
    }

    public record AdjustmentResult(int currentStamps, int totalStamps, int giftCardsIssued, int giftCardsRevoked) {
    }

    public record FraudFlag(UUID id, String type, String severity, String details, Instant createdAt,
                            Instant resolvedAt, String resolutionNote) {
    }

    private final JdbcClient jdbc;
    private final CustomerDirectory directory;
    private final StampLedger ledger;
    private final CustomerSessionService customerSessions;
    private final AdminAuthService adminAuth;
    private final EnvelopeCipher cipher;
    private final CompanySettings company;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CustomerAdminService(@Qualifier("tenantJdbc") JdbcClient jdbc, CustomerDirectory directory,
                                StampLedger ledger, CustomerSessionService customerSessions,
                                AdminAuthService adminAuth, @Qualifier("tenantCipher") EnvelopeCipher cipher,
                                CompanySettings company, AuditLogger audit,
                                PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.directory = directory;
        this.ledger = ledger;
        this.customerSessions = customerSessions;
        this.adminAuth = adminAuth;
        this.cipher = cipher;
        this.company = company;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ------------------------------------------------------------------ Tam nömrə

    /** Tam telefon nömrəsi — yalnız sahib, son 10 dəqiqədə step-up ilə; hər baxış auditə yazılır. */
    public String revealPhone(AdminPrincipal principal, UUID customerId) {
        AccessPolicy.requireCompanyWide(principal);
        adminAuth.requireStepUp(principal);
        directory.get(principal, customerId);
        byte[] encrypted = jdbc.sql("SELECT phone_enc FROM customers WHERE id = :id")
                .param("id", customerId)
                .query(byte[].class)
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        String phone = cipher.decryptString(encrypted,
                Aad.tenantField(TenantContext.require().id(), "customers", "phone_enc", customerId));
        audit.tenant("CUSTOMER_PHONE_REVEALED", AuditLogger.ActorType.STAFF, principal.userId(), "customer", customerId,
                Map.of());
        return phone;
    }

    // ------------------------------------------------------------------ Əl ilə düzəliş

    /**
     * Möhürlərin əl ilə əlavəsi və ya silinməsi. Eyni {@code Idempotency-Key} ilə təkrar sorğu eyni nəticəni qaytarır,
     * fərqli məzmunla — {@code IDEMPOTENCY_KEY_REUSED}.
     *
     * <p>Əlavə: hər möhür təsdiqlənmiş MANUAL möhürdür; hədd dolanda hədiyyə kartı adi qaydada verilir. Silmə: son
     * təsdiqlənmiş möhürlər REVOKED olur; kartdakı saydan çox silinərsə, istifadə olunmamış son hədiyyə kartı da ləğv
     * edilir və kart əvvəlki vəziyyətinə qaytarılır. Təhvil verilmiş hədiyyəni geri almaq olmaz.
     */
    public AdjustmentResult adjust(AdminPrincipal principal, UUID customerId, Adjustment adjustment, String idempotencyKey) {
        String reason = adjustment.reason() == null ? "" : adjustment.reason().strip();
        if (adjustment.delta() == 0 || Math.abs(adjustment.delta()) > MAX_ADJUSTMENT || reason.length() < 5
                || reason.length() > 200 || idempotencyKey == null || !idempotencyKey.matches("^[A-Za-z0-9_-]{8,100}$")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (adjustment.delta() > 0 && adjustment.branchId() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (adjustment.branchId() != null) {
            AccessPolicy.requireBranch(principal, adjustment.branchId());
        }
        directory.get(principal, customerId);
        String scope = "stamp-adjustment:" + customerId;
        byte[] requestHash = sha256(adjustment.delta() + "|" + reason + "|" + adjustment.branchId());
        Instant now = clock.instant();

        AdjustmentResult result = tx.execute(status -> {
            ledger.lockAccount(customerId, now);
            Optional<AdjustmentResult> previous = previousResult(scope, idempotencyKey, requestHash);
            if (previous.isPresent()) {
                return previous.get();
            }
            AdjustmentResult done = adjustment.delta() > 0
                    ? add(principal, customerId, adjustment.delta(), reason, adjustment.branchId(), now)
                    : remove(principal, customerId, -adjustment.delta(), reason, now);
            jdbc.sql("""
                            INSERT INTO idempotency_keys (scope, key, request_hash, response_status, response_body,
                                                          created_at, expires_at)
                            VALUES (:scope, :key, :hash, 200, CAST(:body AS jsonb), :now, :expires)""")
                    .param("scope", scope)
                    .param("key", idempotencyKey)
                    .param("hash", requestHash)
                    .param("body", "{\"currentStamps\":%d,\"totalStamps\":%d,\"giftCardsIssued\":%d,\"giftCardsRevoked\":%d}"
                            .formatted(done.currentStamps(), done.totalStamps(), done.giftCardsIssued(), done.giftCardsRevoked()))
                    .param("now", CustomerDirectory.ts(now))
                    .param("expires", CustomerDirectory.ts(now.plus(IDEMPOTENCY_TTL)))
                    .update();
            audit.tenant("STAMPS_ADJUSTED", AuditLogger.ActorType.STAFF, principal.userId(), "customer", customerId,
                    Map.of("delta", adjustment.delta(), "reason", reason, "giftCardsIssued", done.giftCardsIssued(),
                            "giftCardsRevoked", done.giftCardsRevoked()));
            return done;
        });
        return result;
    }

    private Optional<AdjustmentResult> previousResult(String scope, String key, byte[] requestHash) {
        return jdbc.sql("""
                        SELECT request_hash, (response_body->>'currentStamps')::int AS cur,
                               (response_body->>'totalStamps')::int AS tot,
                               (response_body->>'giftCardsIssued')::int AS issued,
                               (response_body->>'giftCardsRevoked')::int AS revoked
                        FROM idempotency_keys WHERE scope = :scope AND key = :key""")
                .param("scope", scope)
                .param("key", key)
                .query((rs, n) -> {
                    if (!Arrays.equals(rs.getBytes("request_hash"), requestHash)) {
                        throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
                    }
                    return new AdjustmentResult(rs.getInt("cur"), rs.getInt("tot"), rs.getInt("issued"), rs.getInt("revoked"));
                })
                .optional();
    }

    private AdjustmentResult add(AdminPrincipal principal, UUID customerId, int count, String reason, UUID branchId,
                                 Instant now) {
        LocalDate day = now.atZone(company.current().timezone()).toLocalDate();
        int issued = 0;
        for (int i = 0; i < count; i++) {
            UUID stampId = ledger.insertManualStamp(branchId, customerId, principal.userId(), reason, day, now);
            if (ledger.applyConfirmed(customerId, stampId, now).giftCardId() != null) {
                issued++;
            }
        }
        ledger.touchCustomerVisit(customerId, branchId, now);
        return card(customerId, issued, 0);
    }

    private AdjustmentResult remove(AdminPrincipal principal, UUID customerId, int count, String reason, Instant now) {
        int revokedCards = 0;
        int current = currentStamps(customerId);
        while (current < count) {
            // Kartdakı saydan çox silinir: istifadə olunmamış son hədiyyə kartı ləğv edilir, kart geri qaytarılır
            Map<String, Object> reward = jdbc.sql("""
                            SELECT id, stamps_required_snapshot FROM rewards
                            WHERE customer_id = :customerId AND status = 'AVAILABLE'
                            ORDER BY earned_at DESC, id DESC LIMIT 1 FOR UPDATE""")
                    .param("customerId", customerId)
                    .query()
                    .listOfRows()
                    .stream().findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.STAMP_ADJUSTMENT_TOO_LARGE));
            UUID rewardId = (UUID) reward.get("id");
            int required = ((Number) reward.get("stamps_required_snapshot")).intValue();
            jdbc.sql("UPDATE rewards SET status = 'REVOKED' WHERE id = :id").param("id", rewardId).update();
            jdbc.sql("UPDATE stamps SET reward_id = NULL WHERE reward_id = :id").param("id", rewardId).update();
            jdbc.sql("""
                            UPDATE loyalty_accounts SET current_stamps = current_stamps + :required,
                                                        rewards_earned = rewards_earned - 1, updated_at = :now
                            WHERE customer_id = :customerId""")
                    .param("required", required)
                    .param("now", CustomerDirectory.ts(now))
                    .param("customerId", customerId)
                    .update();
            current += required;
            revokedCards++;
        }
        jdbc.sql("""
                        UPDATE stamps SET status = 'REVOKED', revoked_at = :now, revoked_by = :staff, revoke_reason = :reason
                        WHERE id IN (SELECT id FROM stamps WHERE customer_id = :customerId AND status = 'CONFIRMED'
                                       AND reward_id IS NULL ORDER BY created_at DESC, id DESC LIMIT :count)""")
                .param("now", CustomerDirectory.ts(now))
                .param("staff", principal.userId())
                .param("reason", reason)
                .param("customerId", customerId)
                .param("count", count)
                .update();
        jdbc.sql("""
                        UPDATE loyalty_accounts SET current_stamps = current_stamps - :count,
                                                    total_stamps = GREATEST(total_stamps - :count, 0),
                                                    updated_at = :now, version = version + 1
                        WHERE customer_id = :customerId""")
                .param("count", count)
                .param("now", CustomerDirectory.ts(now))
                .param("customerId", customerId)
                .update();
        return card(customerId, 0, revokedCards);
    }

    private int currentStamps(UUID customerId) {
        return jdbc.sql("SELECT current_stamps FROM loyalty_accounts WHERE customer_id = :id")
                .param("id", customerId).query(Integer.class).single();
    }

    private AdjustmentResult card(UUID customerId, int issued, int revoked) {
        return jdbc.sql("SELECT current_stamps, total_stamps FROM loyalty_accounts WHERE customer_id = :id")
                .param("id", customerId)
                .query((rs, n) -> new AdjustmentResult(rs.getInt("current_stamps"), rs.getInt("total_stamps"), issued, revoked))
                .single();
    }

    // ------------------------------------------------------------------ Bloklama

    /** Bloklanan müştərinin bütün sessiyaları dərhal bağlanır; yeni möhür ala və daxil ola bilmir. Yalnız sahib. */
    public void updateStatus(AdminPrincipal principal, UUID customerId, String status) {
        AccessPolicy.requireCompanyWide(principal);
        if (!List.of("ACTIVE", "BLOCKED").contains(status)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        CustomerDirectory.Row customer = directory.get(principal, customerId);
        if (customer.status().equals(status)) {
            return;
        }
        Instant now = clock.instant();
        jdbc.sql("UPDATE customers SET status = :status, updated_at = :now, version = version + 1 WHERE id = :id")
                .param("status", status)
                .param("now", CustomerDirectory.ts(now))
                .param("id", customerId)
                .update();
        if (status.equals("BLOCKED")) {
            customerSessions.revokeAll(customerId, "CUSTOMER_BLOCKED");
        }
        audit.tenant(status.equals("BLOCKED") ? "CUSTOMER_BLOCKED" : "CUSTOMER_UNBLOCKED", AuditLogger.ActorType.STAFF,
                principal.userId(), "customer", customerId, Map.of());
    }

    // ------------------------------------------------------------------ Fırıldaq işarələri

    public List<FraudFlag> fraudFlags(AdminPrincipal principal, UUID customerId) {
        directory.get(principal, customerId);
        return jdbc.sql("""
                        SELECT id, type, severity, details::text AS details, created_at, resolved_at, resolution_note
                        FROM fraud_flags WHERE customer_id = :id ORDER BY created_at DESC LIMIT 100""")
                .param("id", customerId)
                .query((rs, n) -> new FraudFlag(rs.getObject("id", UUID.class), rs.getString("type"),
                        rs.getString("severity"), rs.getString("details"), instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("resolved_at")), rs.getString("resolution_note")))
                .list();
    }

    /** İşarəni qeyd ilə bağlayır; artıq bağlanmış işarə dəyişmir (idempotent). */
    public void resolveFraudFlag(AdminPrincipal principal, UUID flagId, String note) {
        AccessPolicy.requireAnyRole(principal, AdminRole.COMPANY_OWNER, AdminRole.BRANCH_MANAGER);
        String trimmed = note == null ? "" : note.strip();
        if (trimmed.length() < 3 || trimmed.length() > 500) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        UUID customerId = jdbc.sql("SELECT customer_id FROM fraud_flags WHERE id = :id")
                .param("id", flagId)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        directory.get(principal, customerId);
        int updated = jdbc.sql("""
                        UPDATE fraud_flags SET resolved_at = :now, resolved_by = :staff, resolution_note = :note
                        WHERE id = :id AND resolved_at IS NULL""")
                .param("now", CustomerDirectory.ts(clock.instant()))
                .param("staff", principal.userId())
                .param("note", trimmed)
                .param("id", flagId)
                .update();
        if (updated == 1) {
            audit.tenant("FRAUD_FLAG_RESOLVED", AuditLogger.ActorType.STAFF, principal.userId(), "fraud_flag", flagId,
                    Map.of("customerId", customerId.toString()));
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
