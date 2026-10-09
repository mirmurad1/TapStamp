package az.tapstamp.platform.repository;

import java.util.List;

import az.tapstamp.platform.service.PlatformTenantService.OwnerView;
import az.tapstamp.platform.service.PlatformTenantService.TenantStats;
import az.tapstamp.shared.persistence.TenantMapper;

/**
 * Platforma panelinin şirkət haqqında baxışı — şirkətin öz bazasından (çağıran {@code TenantContext.callAs} ilə
 * şirkəti seçir). Yalnız saylar və sahiblər; müştərilərin şəxsi məlumatı oxunmur.
 * SQL: {@code mapper/tenant/platform/TenantOverviewMapper.xml}.
 */
@TenantMapper
public interface TenantOverviewMapper {

    TenantStats stats();

    List<OwnerView> owners();
}
