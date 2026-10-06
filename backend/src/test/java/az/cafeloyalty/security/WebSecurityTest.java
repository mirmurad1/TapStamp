package az.cafeloyalty.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.cafeloyalty.security.config.SecurityConfig;
import az.cafeloyalty.support.CsrfSupport;
import az.cafeloyalty.support.TestApiController;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/** Təhlükəsizlik başlıqları, CSRF, Origin yoxlaması və actuator-un açıqlığı. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestApiController.class)
class WebSecurityTest {

    private static final String VALID_BODY = "{\"name\":\"Aysel\",\"code\":\"abc\"}";

    @Autowired
    MockMvc mvc;

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/actuator/health").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=63072000")))
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.matchesPattern("^[a-f0-9]{32}$")));
    }

    @Test
    void healthIsPublicButHidesDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void otherActuatorEndpointsAreNotReachable() throws Exception {
        for (String path : new String[] {"/actuator", "/actuator/env", "/actuator/info", "/actuator/beans", "/actuator/heapdump"}) {
            int code = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(code).as(path).isIn(401, 403, 404);
        }
    }

    @Test
    void csrfCookieHasHostPrefixAttributes() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        Cookie cookie = response.getCookie(SecurityConfig.CSRF_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.isHttpOnly()).as("SPA cookie-ni oxuya bilməlidir").isFalse();
        // MockHttpServletResponse Servlet 6 cookie atributlarını Set-Cookie sətrinə yazmır; Tomcat isə yazır
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void stateChangingRequestWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/v1/public/test/echo").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void csrfCookieWithoutMatchingHeaderIsRejected() throws Exception {
        CsrfSupport.Csrf csrf = CsrfSupport.obtain(mvc);
        mvc.perform(post("/api/v1/public/test/echo")
                        .cookie(csrf.cookie())
                        .header(SecurityConfig.CSRF_HEADER, "forged-value")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void stateChangingRequestWithValidCsrfTokenSucceeds() throws Exception {
        CsrfSupport.Csrf csrf = CsrfSupport.obtain(mvc);
        mvc.perform(csrf.apply(post("/api/v1/public/test/echo"))
                        .header("Origin", "http://localhost")
                        .header("Sec-Fetch-Site", "same-origin")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aysel"));
    }

    @Test
    void crossOriginStateChangingRequestIsRejectedEvenWithToken() throws Exception {
        CsrfSupport.Csrf csrf = CsrfSupport.obtain(mvc);
        mvc.perform(csrf.apply(post("/api/v1/public/test/echo"))
                        .header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(csrf.apply(post("/api/v1/public/test/echo"))
                        .header("Sec-Fetch-Site", "cross-site")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        mvc.perform(csrf.apply(post("/api/v1/public/test/echo"))
                        .header("Origin", "null")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
    }
}
