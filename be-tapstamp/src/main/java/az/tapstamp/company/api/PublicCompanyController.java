package az.tapstamp.company.api;

import java.time.Duration;

import az.tapstamp.company.service.CompanyLogoService;
import az.tapstamp.company.service.CompanyQueryService;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Kafe", description = "Kafenin ictimai məlumatı")
public class PublicCompanyController {

    private final CompanyQueryService companies;
    private final CompanyLogoService logos;

    public PublicCompanyController(CompanyQueryService companies, CompanyLogoService logos) {
        this.companies = companies;
        this.logos = logos;
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

    @GetMapping("/api/v1/public/company/logo")
    @Operation(summary = "Şirkətin loqosu (PNG)", description = """
            Ünvan versiyalıdır (`logoUrl`-dakı `?v=N`), ona görə uzun müddət keşlənir. Loqo yoxdursa 404.""")
    ResponseEntity<byte[]> logo() {
        TenantContext.require();
        return logos.logo()
                .map(found -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(found.png()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
