package az.tapstamp.company.service;

import az.tapstamp.company.api.PublicCompanyResponse;
import az.tapstamp.company.repository.CompanyMapper;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

/** Şirkətin ictimai məlumatı (müştəri tətbiqi və PWA manifesti üçün). */
@Service
public class CompanyQueryService {

    private final CompanyMapper companies;

    public CompanyQueryService(CompanyMapper companies) {
        this.companies = companies;
    }

    public PublicCompanyResponse publicCompany() {
        var profile = companies.findCompany().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return new PublicCompanyResponse(profile.name(), profile.description(), profile.timezone(),
                profile.defaultLanguage(), profile.brandColor(),
                companies.findLogoVersion().map(version -> "/api/v1/public/company/logo?v=" + version).orElse(null),
                companies.activeBranches(),
                companies.activeRewardRule().orElse(null), companies.companyStampRules().orElse(null));
    }
}
