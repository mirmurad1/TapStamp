package az.tapstamp.customer.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.company.service.CompanySettings;
import az.tapstamp.customer.auth.CustomerSessionService;
import az.tapstamp.customer.repository.CustomerAdminMapper.CardTotals;
import az.tapstamp.customer.repository.CustomerAdminMapper.RevocableReward;
import az.tapstamp.customer.repository.CustomerAdminMapper;
import az.tapstamp.loyalty.stamp.StampLedger;
import az.tapstamp.notification.push.PushService;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminAuthService;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import org.springframework.beans.factory.annotation.Qualifier;
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

    private final CustomerAdminMapper customers;
    private final PushService push;
    private final CustomerDirectory directory;
    private final StampLedger ledger;
    private final CustomerSessionService customerSessions;
    private final AdminAuthService adminAuth;
    private final EnvelopeCipher cipher;
    private final CompanySettings company;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CustomerAdminService(CustomerAdminMapper customers, CustomerDirectory directory,
                                StampLedger ledger, CustomerSessionService customerSessions,
                                AdminAuthService adminAuth, @Qualifier("tenantCipher") EnvelopeCipher cipher,
                                CompanySettings company, AuditLogger audit,
                                PlatformTransactionManager transactionManager, Clock clock, PushService push) {
        this.push = push;
        this.customers = customers;
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

        return Objects.requireNonNull(tx.execute(status -> {
            ledger.lockAccount(customerId, now);
            Optional<AdjustmentResult> previous = previousResult(scope, idempotencyKey, requestHash);
            if (previous.isPresent()) {
                return previous.get();
            }
            AdjustmentResult done = adjustment.delta() > 0
                    ? add(principal, customerId, adjustment.delta(), reason, adjustment.branchId(), now)
                    : remove(principal, customerId, -adjustment.delta(), reason, now);
            customers.saveAdjustment(scope, idempotencyKey, requestHash,
                    "{\"currentStamps\":%d,\"totalStamps\":%d,\"giftCardsIssued\":%d,\"giftCardsRevoked\":%d}"
                            .formatted(done.currentStamps(), done.totalStamps(), done.giftCardsIssued(),
                                    done.giftCardsRevoked()),
                    now, now.plus(IDEMPOTENCY_TTL));
            audit.tenant("STAMPS_ADJUSTED", AuditLogger.ActorType.STAFF, principal.userId(), "customer", customerId,
                    Map.of("delta", adjustment.delta(), "reason", reason, "giftCardsIssued", done.giftCardsIssued(),
                            "giftCardsRevoked", done.giftCardsRevoked()));
            return done;
        }));
    }

    private Optional<AdjustmentResult> previousResult(String scope, String key, byte[] requestHash) {
        return customers.findAdjustment(scope, key).map(stored -> {
            if (!Arrays.equals(stored.requestHash(), requestHash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return new AdjustmentResult(stored.currentStamps(), stored.totalStamps(), stored.giftCardsIssued(),
                    stored.giftCardsRevoked());
        });
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
            RevocableReward reward = customers.lockLatestAvailableReward(customerId)
                    .orElseThrow(() -> new ApiException(ErrorCode.STAMP_ADJUSTMENT_TOO_LARGE));
            customers.revokeReward(reward.id());
            customers.unlinkStampsFromReward(reward.id());
            customers.restoreCardStamps(customerId, reward.stampsRequired(), now);
            current += reward.stampsRequired();
            revokedCards++;
        }
        customers.revokeLatestStamps(customerId, count, principal.userId(), reason, now);
        customers.deductCardStamps(customerId, count, now);
        return card(customerId, 0, revokedCards);
    }

    private int currentStamps(UUID customerId) {
        return customers.currentStamps(customerId);
    }

    private AdjustmentResult card(UUID customerId, int issued, int revoked) {
        CardTotals totals = customers.cardTotals(customerId);
        return new AdjustmentResult(totals.currentStamps(), totals.totalStamps(), issued, revoked);
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
        customers.updateStatus(customerId, status, now);
        if (status.equals("BLOCKED")) {
            customerSessions.revokeAll(customerId, "CUSTOMER_BLOCKED");
            push.removeAll(customerId);
        }
        audit.tenant(status.equals("BLOCKED") ? "CUSTOMER_BLOCKED" : "CUSTOMER_UNBLOCKED", AuditLogger.ActorType.STAFF,
                principal.userId(), "customer", customerId, Map.of());
    }

    // ------------------------------------------------------------------ Fırıldaq işarələri

    public PageResult<FraudFlag> fraudFlags(AdminPrincipal principal, UUID customerId, PageQuery page) {
        directory.get(principal, customerId);
        return page.result(customers.fraudFlags(customerId, page.limit(), page.offset()), customers.countFraudFlags(customerId));
    }

    /** İşarəni qeyd ilə bağlayır; artıq bağlanmış işarə dəyişmir (idempotent). */
    public void resolveFraudFlag(AdminPrincipal principal, UUID flagId, String note) {
        AccessPolicy.requireAnyRole(principal, AdminRole.COMPANY_OWNER, AdminRole.BRANCH_MANAGER);
        String trimmed = note == null ? "" : note.strip();
        if (trimmed.length() < 3 || trimmed.length() > 500) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        UUID customerId = customers.findFraudFlagCustomer(flagId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        directory.get(principal, customerId);
        int updated = customers.resolveFraudFlag(flagId, principal.userId(), trimmed, clock.instant());
        if (updated == 1) {
            audit.tenant("FRAUD_FLAG_RESOLVED", AuditLogger.ActorType.STAFF, principal.userId(), "fraud_flag", flagId,
                    Map.of("customerId", customerId.toString()));
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
