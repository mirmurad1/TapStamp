package az.cafeloyalty.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import az.cafeloyalty.security.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Testlərdə real brauzer kimi CSRF cookie-sini alıb başlıqda göndərmək üçün köməkçi. */
public final class CsrfSupport {

    private CsrfSupport() {
    }

    public static Csrf obtain(MockMvc mvc) throws Exception {
        Cookie cookie = mvc.perform(get("/api/v1/csrf")).andReturn().getResponse().getCookie(SecurityConfig.CSRF_COOKIE);
        if (cookie == null) {
            throw new IllegalStateException("CSRF cookie was not issued");
        }
        return new Csrf(cookie);
    }

    public record Csrf(Cookie cookie) {
        public MockHttpServletRequestBuilder apply(MockHttpServletRequestBuilder request) {
            return request.cookie(cookie).header(SecurityConfig.CSRF_HEADER, cookie.getValue());
        }
    }
}
