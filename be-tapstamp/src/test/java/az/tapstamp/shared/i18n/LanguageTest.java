package az.tapstamp.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class LanguageTest {

    private final AppLocaleResolver resolver = new AppLocaleResolver(Language.AZ);

    @Test
    void parsesCommonTagVariants() {
        assertThat(Language.fromTag("az")).contains(Language.AZ);
        assertThat(Language.fromTag("AZ")).contains(Language.AZ);
        assertThat(Language.fromTag("az-Latn-AZ")).contains(Language.AZ);
        assertThat(Language.fromTag("ru_RU")).contains(Language.RU);
        assertThat(Language.fromTag(" en-GB ")).contains(Language.EN);
        assertThat(Language.fromTag("tr")).isEmpty();
        assertThat(Language.fromTag("")).isEmpty();
        assertThat(Language.fromTag(null)).isEmpty();
    }

    @Test
    void tagParsingIsNotAffectedByTurkicDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("az"));
            // "I" az lokalında kiçik hərfə "ı" kimi çevrilir — Locale.ROOT istifadə olunmasa, "IN" tanınmazdı
            assertThat(Language.fromTag("EN")).contains(Language.EN);
            assertThat(Language.RU.locale().getLanguage()).isEqualTo("ru");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void profileLanguageHasHighestPriority() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AppLocaleResolver.PROFILE_LANGUAGE_ATTRIBUTE, Language.RU);
        request.setCookies(new Cookie(AppLocaleResolver.LANG_COOKIE, "en"));
        request.addHeader("Accept-Language", "az");
        request.addPreferredLocale(Locale.of("az"));
        assertThat(resolver.resolveLanguage(request)).isEqualTo(Language.RU);
    }

    @Test
    void cookieBeatsAcceptLanguage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AppLocaleResolver.LANG_COOKIE, "en"));
        request.addHeader("Accept-Language", "ru");
        request.addPreferredLocale(Locale.of("ru"));
        assertThat(resolver.resolveLanguage(request)).isEqualTo(Language.EN);
    }

    @Test
    void invalidCookieFallsThroughToAcceptLanguage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AppLocaleResolver.LANG_COOKIE, "<script>"));
        request.addHeader("Accept-Language", "ru");
        request.addPreferredLocale(Locale.of("ru"));
        assertThat(resolver.resolveLanguage(request)).isEqualTo(Language.RU);
    }

    @Test
    void defaultsToAzerbaijani() {
        assertThat(resolver.resolveLanguage(new MockHttpServletRequest())).isEqualTo(Language.AZ);
    }
}
