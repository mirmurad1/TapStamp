package az.tapstamp.shared.i18n;

import java.util.Locale;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Sorğunun dilini prioritet sırası ilə müəyyən edir:
 * <ol>
 *   <li>daxil olmuş istifadəçinin profilindəki dil ({@link #PROFILE_LANGUAGE_ATTRIBUTE} atributu, sessiya filtri qoyur);</li>
 *   <li>{@code lang} cookie-si;</li>
 *   <li>{@code Accept-Language} başlığı (dəstəklənən ilk dil);</li>
 *   <li>şirkətin standart dili, o da yoxdursa platformanın standart dili ({@code az}).</li>
 * </ol>
 * Nəticə həmişə dəstəklənən dillərdən biridir.
 */
public class AppLocaleResolver implements LocaleResolver {

    public static final String LANG_COOKIE = "lang";
    public static final String PROFILE_LANGUAGE_ATTRIBUTE = AppLocaleResolver.class.getName() + ".PROFILE_LANGUAGE";
    /** Şirkətin standart dili (tenant filtri qoyur); istifadəçinin seçimi və brauzer dili olmayanda işlədilir. */
    public static final String TENANT_DEFAULT_LANGUAGE_ATTRIBUTE = AppLocaleResolver.class.getName() + ".TENANT_DEFAULT";

    private final Language defaultLanguage;

    public AppLocaleResolver(Language defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        return resolveLanguage(request).locale();
    }

    public Language resolveLanguage(HttpServletRequest request) {
        if (request.getAttribute(PROFILE_LANGUAGE_ATTRIBUTE) instanceof Language profileLanguage) {
            return profileLanguage;
        }
        return fromCookie(request)
                .or(() -> fromAcceptLanguage(request))
                .or(() -> request.getAttribute(TENANT_DEFAULT_LANGUAGE_ATTRIBUTE) instanceof Language tenantDefault
                        ? Optional.of(tenantDefault) : Optional.empty())
                .orElse(defaultLanguage);
    }

    /** Dil yalnız frontend tərəfindən {@code lang} cookie-si ilə dəyişdirilir; server tərəfində dəyişiklik yoxdur. */
    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("Dil 'lang' cookie-si və ya istifadəçi profili ilə dəyişdirilir");
    }

    private static Optional<Language> fromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (LANG_COOKIE.equals(cookie.getName())) {
                return Language.fromTag(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private static Optional<Language> fromAcceptLanguage(HttpServletRequest request) {
        if (request.getHeader("Accept-Language") == null) {
            return Optional.empty();
        }
        // Servlet konteyneri başlığı keyfiyyət (q) dəyərinə görə sıralayır
        var locales = request.getLocales();
        while (locales.hasMoreElements()) {
            Optional<Language> language = Language.fromLocale(locales.nextElement());
            if (language.isPresent()) {
                return language;
            }
        }
        return Optional.empty();
    }
}
