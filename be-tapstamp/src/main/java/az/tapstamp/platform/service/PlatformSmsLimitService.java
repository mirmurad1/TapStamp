package az.tapstamp.platform.service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.customer.auth.OtpBudget;
import az.tapstamp.notification.sms.repository.SmsLimitMapper;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.security.admin.AdminRealms;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.registry.TenantDirectory;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Platforma panelindən SMS limitləri: şirkətin gündəlik limiti (yeni nömrələrə giriş kodları) — bu günkü istifadə ilə
 * baxış və dəyişmə; platformanın gündəlik büdcəsi və bu günkü istifadə. Dəyişikliklər platforma auditinə yazılır.
 */
@Service
public class PlatformSmsLimitService {

    public static final int MIN_LIMIT = 10;
    public static final int MAX_LIMIT = 100_000;

    /** {@code custom} — şirkət üçün fərdi limit təyin olunub (əks halda standart). */
    public record TenantSmsLimit(int limit, int defaultLimit, boolean custom, long usedToday) {
    }

    public record PlatformSmsBudget(long usedToday, long dailyBudget) {
    }

    private final OtpBudget budget;
    private final SmsLimitMapper limits;
    private final TenantDirectory directory;
    private final AdminRealms realms;
    private final Clock clock;

    public PlatformSmsLimitService(OtpBudget budget, SmsLimitMapper limits, TenantDirectory directory,
                                   AdminRealms realms, Clock clock) {
        this.budget = budget;
        this.limits = limits;
        this.directory = directory;
        this.realms = realms;
        this.clock = clock;
    }

    public TenantSmsLimit tenantLimit(UUID tenantId) {
        TenantRecord tenant = tenant(tenantId);
        Integer custom = limits.tenantLimit(tenantId).orElse(null);
        long used = tenant.status().outOfService() ? 0
                : TenantContext.callAs(tenant.toInfo(), () -> budget.tenantUsedToday(tenant.toInfo()));
        return new TenantSmsLimit(custom != null ? custom : budget.tenantDefault(), budget.tenantDefault(),
                custom != null, used);
    }

    /** {@code limit = null} — standart dəyərə qaytarmaq. */
    public TenantSmsLimit updateTenantLimit(AdminPrincipal principal, UUID tenantId, @Nullable Integer limit) {
        tenant(tenantId);
        if (limit != null && (limit < MIN_LIMIT || limit > MAX_LIMIT)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        TenantSmsLimit before = tenantLimit(tenantId);
        if (limit == null) {
            limits.deleteTenantLimit(tenantId);
        } else {
            limits.upsertTenantLimit(tenantId, limit, principal.userId(), clock.instant());
        }
        TenantSmsLimit after = tenantLimit(tenantId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("before", before.limit());
        details.put("after", after.limit());
        details.put("custom", after.custom());
        realms.audit(AdminRealm.PLATFORM, "TENANT_SMS_LIMIT_UPDATED", principal.userId(), "tenant", tenantId, details);
        return after;
    }

    public PlatformSmsBudget platformBudget() {
        return new PlatformSmsBudget(budget.platformUsedToday(), budget.platformDaily());
    }

    private TenantRecord tenant(UUID tenantId) {
        return directory.findById(tenantId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
