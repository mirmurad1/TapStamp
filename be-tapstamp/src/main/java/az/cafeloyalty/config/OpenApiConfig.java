package az.cafeloyalty.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI sənədinin ümumi təsviri. Swagger UI yalnız dev profilində açıqdır. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI cafeLoyaltyOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Kafe Loyallıq API")
                .version("v1")
                .description("""
                        Kafe loyallıq platformasının REST API-si.

                        * Tenant (şirkət) sorğunun domenindən müəyyən olunur.
                        * Autentifikasiya HttpOnly cookie-lərlə aparılır; dəyişdirici sorğular \
                        `X-XSRF-TOKEN` başlığı tələb edir.
                        * Xətalar RFC 7807 (Problem Details) formatındadır və dildən asılı olmayan \
                        `code` sahəsi daşıyır.
                        * Mətnlər `lang` cookie-si və ya `Accept-Language` başlığına görə az, en və ya ru dilində qaytarılır.
                        """));
    }
}
