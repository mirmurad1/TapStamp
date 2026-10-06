package az.cafeloyalty.company.api;

import az.cafeloyalty.company.service.CompanyQueryService;
import az.cafeloyalty.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Kafe", description = "Kafenin ictimai məlumatı")
public class PublicCompanyController {

    private final CompanyQueryService companies;

    public PublicCompanyController(CompanyQueryService companies) {
        this.companies = companies;
    }

    @GetMapping("/api/v1/public/company")
    @Operation(
            summary = "Kafe haqqında ictimai məlumat",
            description = """
                    Domenə görə müəyyən olunan kafenin adı, təsviri, aktiv filialları və mükafat qaydası. \
                    Autentifikasiya tələb etmir. Platforma domenində 404 qaytarır.""")
    PublicCompanyResponse company() {
        TenantContext.require();
        return companies.publicCompany();
    }
}
