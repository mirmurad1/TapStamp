package az.tapstamp.catalog.web;

import java.time.Duration;
import java.util.Locale;

import az.tapstamp.catalog.CatalogService;
import az.tapstamp.company.api.PublicCompanyResponse;
import az.tapstamp.company.service.CompanyQueryService;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.MessageSource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

/**
 * Menyu linkinin önizləməsi (Open Graph): WhatsApp, Telegram, Viber, Facebook kimi tətbiqlər linkə toxunmadan HTML-dəki
 * {@code og:} teqlərini oxuyur, JavaScript işlətmir — ona görə SPA-nın yerinə bu qısa HTML verilir. Nginx {@code /menu}
 * ünvanına gələn link botlarını bura göndərir; səhifəni insan açarsa, dərhal əsl menyuya keçir.
 *
 * <p>Mətn şirkətin standart dilindədir (önizləmə hamı üçün eynidir), şəkil — şirkətin ikonu (loqo varsa, loqodan).
 */
@RestController
@Tag(name = "İctimai: menyu və xidmətlər")
public class MenuShareController {

    private final CatalogService catalog;
    private final CompanyQueryService company;
    private final MessageSource messages;

    public MenuShareController(CatalogService catalog, CompanyQueryService company, MessageSource messages) {
        this.catalog = catalog;
        this.company = company;
        this.messages = messages;
    }

    @GetMapping(value = "/api/v1/public/share/menu", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Menyu linkinin önizləməsi (HTML, Open Graph)", description = """
            Link önizləmə botları üçün: başlıq, izah və şəkil şirkətin standart dilində. Nginx `/menu` ünvanına gələn \
            botları bura göndərir. Menyu göstərilmirsə 404.""")
    ResponseEntity<String> menu() {
        TenantContext.require();
        String title = catalog.publicTitle().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        PublicCompanyResponse info = company.publicCompany();
        Language language = Language.fromTag(info.defaultLanguage()).orElse(Language.DEFAULT);
        Locale locale = language.locale();
        String name = info.name().resolve(language);
        String origin = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String url = origin + "/menu";
        String image = origin + "/api/v1/public/icons/512.png";
        String heading = messages.getMessage("share.menu.title." + title, new Object[] {name}, locale);
        String description = messages.getMessage("share.menu.description." + title, new Object[] {name}, locale);
        String html = """
                <!doctype html>
                <html lang="%1$s">
                <head>
                <meta charset="utf-8">
                <title>%2$s</title>
                <meta name="description" content="%3$s">
                <meta property="og:type" content="website">
                <meta property="og:site_name" content="%4$s">
                <meta property="og:title" content="%2$s">
                <meta property="og:description" content="%3$s">
                <meta property="og:url" content="%5$s">
                <meta property="og:image" content="%6$s">
                <meta property="og:image:width" content="512">
                <meta property="og:image:height" content="512">
                <meta property="og:locale" content="%7$s">
                <meta name="twitter:card" content="summary">
                <meta http-equiv="refresh" content="0; url=%5$s">
                </head>
                <body><a href="%5$s">%2$s</a></body>
                </html>
                """.formatted(language.tag(), escape(heading), escape(description), escape(name), escape(url),
                escape(image), ogLocale(language));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                .body(html);
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private static String ogLocale(Language language) {
        return switch (language) {
            case AZ -> "az_AZ";
            case EN -> "en_US";
            case RU -> "ru_RU";
        };
    }
}
