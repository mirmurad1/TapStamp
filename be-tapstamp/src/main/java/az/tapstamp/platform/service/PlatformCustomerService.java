package az.tapstamp.platform.service;

import java.util.UUID;

import az.tapstamp.customer.admin.CustomerDirectory;
import az.tapstamp.loyalty.repository.LoyaltySettingsMapper;
import az.tapstamp.loyalty.settings.LoyaltySettingsService.RewardSettings;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.registry.TenantDirectory;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.springframework.stereotype.Service;

/**
 * Platformada şirkətin müştəri siyahısı — şirkət panelindəki "Müştərilər" ekranı ilə eyni sətirlər. Telefon nömrələri
 * yalnız maskalanmış göstərilir (tam nömrə və CSV ixracı platformaya verilmir).
 */
@Service
public class PlatformCustomerService {

    /** {@code stampsRequired} — aktiv hədiyyə qaydasının möhür sayı (kartdakı zolaq üçün); qayda yoxdursa 0. */
    public record CustomerList(CustomerDirectory.Page customers, int stampsRequired) {
    }

    private final TenantDirectory directory;
    private final CustomerDirectory customers;
    private final LoyaltySettingsMapper loyalty;

    public PlatformCustomerService(TenantDirectory directory, CustomerDirectory customers, LoyaltySettingsMapper loyalty) {
        this.directory = directory;
        this.customers = customers;
        this.loyalty = loyalty;
    }

    public CustomerList list(AdminPrincipal principal, UUID tenantId, CustomerDirectory.Filter filter, int page, int size) {
        TenantRecord tenant = directory.findById(tenantId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (tenant.status().outOfService()) {
            throw new ApiException(ErrorCode.TENANT_UNAVAILABLE);
        }
        return TenantContext.callAs(tenant.toInfo(), () -> new CustomerList(
                customers.list(principal, filter, page, size),
                loyalty.findActiveReward().map(RewardSettings::stampsRequired).orElse(0)));
    }
}
