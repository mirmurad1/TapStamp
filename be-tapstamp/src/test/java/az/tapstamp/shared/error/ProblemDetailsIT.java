package az.tapstamp.shared.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.tapstamp.support.CsrfSupport;
import az.tapstamp.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Problem Details formatı, xəta kodları və mesajların dilə görə seçilməsi. */
class ProblemDetailsIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void apiExceptionIsLocalizedAndCarriesStableCode() throws Exception {
        mvc.perform(get("/api/v1/public/test/api-error").header("Accept-Language", "ru-RU,ru;q=0.9"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.type").value("urn:tapstamp:problem:rate-limited"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.title").value("Подождите немного"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(30))
                .andExpect(jsonPath("$.traceId", matchesPattern("^[a-f0-9]{32}$")));
    }

    @Test
    void langCookieTakesPriorityOverAcceptLanguage() throws Exception {
        mvc.perform(get("/api/v1/public/test/api-error")
                        .cookie(new Cookie("lang", "en"))
                        .header("Accept-Language", "ru"))
                .andExpect(jsonPath("$.title").value("Please wait a moment"));
    }

    @Test
    void firstSupportedAcceptLanguageIsUsed() throws Exception {
        mvc.perform(get("/api/v1/public/test/api-error").header("Accept-Language", "de-DE, ru;q=0.5"))
                .andExpect(jsonPath("$.title").value("Подождите немного"));
    }

    @Test
    void unsupportedLanguageFallsBackToAzerbaijani() throws Exception {
        mvc.perform(get("/api/v1/public/test/api-error").header("Accept-Language", "fr-FR"))
                .andExpect(jsonPath("$.title").value("Bir az gözləyin"));
        mvc.perform(get("/api/v1/public/test/api-error").cookie(new Cookie("lang", "xx")))
                .andExpect(jsonPath("$.title").value("Bir az gözləyin"));
    }

    @Test
    void validationErrorsListFieldsWithStableCodesAndLocalizedMessages() throws Exception {
        CsrfSupport.Csrf csrf = CsrfSupport.obtain(mvc);
        mvc.perform(csrf.apply(post("/api/v1/public/test/echo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"code\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.title").value("Məlumatlar düzgün deyil"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].code").value(hasItem("NotBlank")))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value(hasItem("Bu sahəni doldurun")))
                .andExpect(jsonPath("$.errors[?(@.field == 'code')].message")
                        .value(hasItem("Uzunluq 2 ilə 5 arasında olmalıdır")));
    }

    @Test
    void validationMessagesFollowRequestLanguage() throws Exception {
        CsrfSupport.Csrf csrf = CsrfSupport.obtain(mvc);
        mvc.perform(csrf.apply(post("/api/v1/public/test/echo"))
                        .cookie(new Cookie("lang", "ru"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"code\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Заполните это поле"));
    }

    @Test
    void unknownJsonPropertiesAreRejected() throws Exception {
        CsrfSupport.Csrf csrf = CsrfSupport.obtain(mvc);
        mvc.perform(csrf.apply(post("/api/v1/public/test/echo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aysel\",\"code\":\"abc\",\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() throws Exception {
        mvc.perform(get("/api/v1/public/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("stackTrace"))))
                .andExpect(content().string(not(containsString("exception"))));
    }

    @Test
    void unknownPublicPathReturnsNotFound() throws Exception {
        mvc.perform(get("/api/v1/public/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        CsrfSupport.Csrf csrf = CsrfSupport.obtain(mvc);
        mvc.perform(csrf.apply(delete("/api/v1/csrf")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void everythingNotExplicitlyOpenRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/admin/customers").header("Accept-Language", "en"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.title").value("Please sign in"));
    }
}
