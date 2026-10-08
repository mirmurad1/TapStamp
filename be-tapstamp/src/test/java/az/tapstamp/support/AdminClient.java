package az.tapstamp.support;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.security.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * Brauzer kimi davranan test klienti: domen (Host), öz IP-si, CSRF cookie-si və admin sessiya cookie-si.
 * Cavabdakı {@code Set-Cookie} ilə sessiya tokeni avtomatik yenilənir (rotasiya) və ya silinir.
 */
public final class AdminClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

    private final MockMvc mvc;
    private final String host;
    private final AdminRealm realm;
    private final String basePath;
    private String ip;
    private Cookie csrf;
    private String session;

    private AdminClient(MockMvc mvc, String host, AdminRealm realm) {
        this.mvc = mvc;
        this.host = host;
        this.realm = realm;
        this.basePath = realm == AdminRealm.STAFF ? "/api/v1/admin" : "/api/v1/platform";
        this.ip = uniqueIp();
    }

    public static AdminClient staff(MockMvc mvc, String host) {
        return new AdminClient(mvc, host, AdminRealm.STAFF);
    }

    public static AdminClient platform(MockMvc mvc) {
        return new AdminClient(mvc, TestTenants.PLATFORM_HOST, AdminRealm.PLATFORM);
    }

    /** Hər klient üçün ayrıca IP: per-IP limitlər testlər arasında paylaşılmasın. */
    public static String uniqueIp() {
        int n = IP_COUNTER.incrementAndGet();
        return "10." + ((n >> 16) & 0xFF) + "." + ((n >> 8) & 0xFF) + "." + (n & 0xFF);
    }

    public AdminClient fromIp(String ip) {
        this.ip = ip;
        return this;
    }

    public String basePath() {
        return basePath;
    }

    public String session() {
        return session;
    }

    public void useSession(String token) {
        this.session = token;
    }

    /** Eyni sessiya ilə başqa domenə sorğu göndərən klient (cookie-nin başqa şirkətdə işləmədiyini yoxlamaq üçün). */
    public AdminClient withSessionOn(String otherHost, AdminRealm otherRealm) {
        AdminClient other = new AdminClient(mvc, otherHost, otherRealm);
        other.session = session;
        return other;
    }

    public ResultActions login(String email, String password) throws Exception {
        return post("/auth/login", Map.of("email", email, "password", password));
    }

    public ResultActions get(String path) throws Exception {
        return perform(MockMvcRequestBuilders.get(basePath + path));
    }

    public ResultActions getBackground(String path) throws Exception {
        return perform(MockMvcRequestBuilders.get(basePath + path).header("X-Background", "1"));
    }

    public ResultActions post(String path, Object body) throws Exception {
        return perform(withJson(MockMvcRequestBuilders.post(basePath + path), body));
    }

    /** POST əlavə başlıqla (məs. {@code Idempotency-Key}). */
    public ResultActions post(String path, Object body, String header, String value) throws Exception {
        return perform(withJson(MockMvcRequestBuilders.post(basePath + path), body).header(header, value));
    }

    public ResultActions post(String path) throws Exception {
        return perform(MockMvcRequestBuilders.post(basePath + path));
    }

    public ResultActions put(String path, Object body) throws Exception {
        return perform(withJson(MockMvcRequestBuilders.put(basePath + path), body));
    }

    public ResultActions patch(String path, Object body) throws Exception {
        return perform(withJson(MockMvcRequestBuilders.patch(basePath + path), body));
    }

    public ResultActions delete(String path) throws Exception {
        return perform(MockMvcRequestBuilders.delete(basePath + path));
    }

    /** Tam yol ilə sorğu (məs. başqa modulun endpoint-i). */
    public ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        if (csrf == null) {
            csrf = obtainCsrf();
        }
        builder.with(request -> {
            request.setServerName(host);
            request.setRemoteAddr(ip);
            return request;
        });
        List<Cookie> cookies = new ArrayList<>();
        cookies.add(csrf);
        if (session != null) {
            cookies.add(new Cookie(realm.cookieName(), session));
        }
        builder.cookie(cookies.toArray(Cookie[]::new)).header(SecurityConfig.CSRF_HEADER, csrf.getValue());
        ResultActions result = mvc.perform(builder);
        captureSession(result.andReturn());
        return result;
    }

    private Cookie obtainCsrf() throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/api/v1/csrf").with(request -> {
            request.setServerName(host);
            request.setRemoteAddr(ip);
            return request;
        })).andReturn();
        Cookie cookie = result.getResponse().getCookie(SecurityConfig.CSRF_COOKIE);
        if (cookie == null) {
            throw new IllegalStateException("CSRF cookie was not issued for " + host);
        }
        return cookie;
    }

    private void captureSession(MvcResult result) {
        for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            String prefix = realm.cookieName() + "=";
            if (!header.startsWith(prefix)) {
                continue;
            }
            String value = header.substring(prefix.length(), header.indexOf(';') < 0 ? header.length() : header.indexOf(';'));
            boolean cleared = value.isEmpty() || header.toLowerCase(Locale.ROOT).contains("max-age=0");
            session = cleared ? null : value;
        }
    }

    private static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, Object body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
    }
}
