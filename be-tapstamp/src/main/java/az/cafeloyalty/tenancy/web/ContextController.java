package az.cafeloyalty.tenancy.web;

import az.cafeloyalty.tenancy.RequestScope;
import az.cafeloyalty.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Kontekst", description = "Sorğunun hansı şirkətə və ya platformaya aid olduğu")
public class ContextController {

    @GetMapping("/api/v1/context")
    @Operation(
            summary = "Cari domenin konteksti",
            description = """
                    Frontend bu cavaba görə rejimi seçir: şirkətin domenində (`TENANT`) müştəri və işçi tətbiqi, \
                    platformanın domenində (`PLATFORM`) platforma paneli işləyir. Gizli məlumat qaytarılmır.""")
    ContextResponse context(HttpServletRequest request) {
        RequestScope scope = RequestScope.of(request);
        return new ContextResponse(scope, TenantContext.current()
                .map(t -> new ContextResponse.Tenant(t.slug(), t.defaultLanguage().tag(), t.timezone().getId()))
                .orElse(null));
    }

    public record ContextResponse(RequestScope scope, Tenant tenant) {
        public record Tenant(String slug, String defaultLanguage, String timezone) {
        }
    }
}
