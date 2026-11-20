package az.tapstamp.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
 * Müştəri brauzerini təqlid edən test klienti: cookie anbarı (yolu nəzərə alır — refresh cookie yalnız
 * {@code /api/v1/auth} yollarına göndərilir), CSRF, domen və IP.
 */
public final class CustomerClient {

    private record StoredCookie(String value, String path) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mvc;
    private final String host;
    private final String ip;
    private final Map<String, StoredCookie> jar = new LinkedHashMap<>();
    private boolean sendCsrf = true;

    public CustomerClient(MockMvc mvc, String host) {
        this(mvc, host, AdminClient.uniqueIp());
    }

    public CustomerClient(MockMvc mvc, String host, String ip) {
        this.mvc = mvc;
        this.host = host;
        this.ip = ip;
    }

    /** Eyni cookie-lərlə başqa domenə gedən klient (cookie-nin başqa şirkətdə işləməməsini yoxlamaq üçün). */
    public CustomerClient onHost(String otherHost) {
        CustomerClient other = new CustomerClient(mvc, otherHost, ip);
        jar.forEach((name, cookie) -> {
            if (!name.equals(SecurityConfig.CSRF_COOKIE)) {
                other.jar.put(name, cookie);
            }
        });
        return other;
    }

    public CustomerClient withoutCsrf() {
        this.sendCsrf = false;
        return this;
    }

    public String cookie(String name) {
        StoredCookie cookie = jar.get(name);
        return cookie == null ? null : cookie.value();
    }

    public void setCookie(String name, String value, String path) {
        jar.put(name, new StoredCookie(value, path));
    }

    public ResultActions get(String path) throws Exception {
        return perform(MockMvcRequestBuilders.get(path), path);
    }

    public ResultActions post(String path) throws Exception {
        return perform(MockMvcRequestBuilders.post(path), path);
    }

    public ResultActions post(String path, Object body) throws Exception {
        return perform(json(MockMvcRequestBuilders.post(path), body), path);
    }

    public ResultActions patch(String path, Object body) throws Exception {
        return perform(json(MockMvcRequestBuilders.patch(path), body), path);
    }

    public ResultActions put(String path, Object body) throws Exception {
        return perform(json(MockMvcRequestBuilders.put(path), body), path);
    }

    public ResultActions delete(String path) throws Exception {
        return perform(MockMvcRequestBuilders.delete(path), path);
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder, String path) throws Exception {
        if (sendCsrf && !jar.containsKey(SecurityConfig.CSRF_COOKIE)) {
            MvcResult csrf = mvc.perform(MockMvcRequestBuilders.get("/api/v1/csrf").with(request -> {
                request.setServerName(host);
                request.setRemoteAddr(ip);
                return request;
            })).andReturn();
            Cookie cookie = csrf.getResponse().getCookie(SecurityConfig.CSRF_COOKIE);
            jar.put(SecurityConfig.CSRF_COOKIE, new StoredCookie(cookie.getValue(), "/"));
        }
        builder.with(request -> {
            request.setServerName(host);
            request.setRemoteAddr(ip);
            return request;
        });
        List<Cookie> cookies = new ArrayList<>();
        jar.forEach((name, cookie) -> {
            if (path.startsWith(cookie.path()) && (sendCsrf || !name.equals(SecurityConfig.CSRF_COOKIE))) {
                cookies.add(new Cookie(name, cookie.value()));
            }
        });
        if (!cookies.isEmpty()) {
            builder.cookie(cookies.toArray(Cookie[]::new));
        }
        if (sendCsrf) {
            builder.header(SecurityConfig.CSRF_HEADER, jar.get(SecurityConfig.CSRF_COOKIE).value());
        }
        ResultActions result = mvc.perform(builder);
        capture(result.andReturn());
        return result;
    }

    private void capture(MvcResult result) {
        for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            int eq = header.indexOf('=');
            int semicolon = header.indexOf(';');
            String name = header.substring(0, eq);
            String value = header.substring(eq + 1, semicolon < 0 ? header.length() : semicolon);
            String lower = header.toLowerCase(Locale.ROOT);
            int pathIndex = lower.indexOf("path=");
            String path = "/";
            if (pathIndex >= 0) {
                int end = header.indexOf(';', pathIndex);
                path = header.substring(pathIndex + 5, end < 0 ? header.length() : end);
            }
            if (value.isEmpty() || lower.contains("max-age=0")) {
                jar.remove(name);
            } else {
                jar.put(name, new StoredCookie(value, path));
            }
        }
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
    }
}
