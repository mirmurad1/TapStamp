package az.cafeloyalty.security.admin;

import java.time.Duration;
import java.util.Optional;

import az.cafeloyalty.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Admin sessiya cookie-si: {@code __Host-} prefiksi (yalnız bu domen, yalnız HTTPS, Path=/), HttpOnly (JavaScript
 * oxuya bilmir), SameSite=Strict (başqa saytdan gələn sorğularda göndərilmir).
 */
@Component
public class AdminSessionCookies {

    private final AppProperties properties;

    public AdminSessionCookies(AppProperties properties) {
        this.properties = properties;
    }

    public void write(HttpServletResponse response, AdminRealm realm, AdminAuthService.IssuedSession session) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(realm, session.token(), session.maxAge()).toString());
    }

    public void clear(HttpServletResponse response, AdminRealm realm) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(realm, "", Duration.ZERO).toString());
    }

    public Optional<String> read(HttpServletRequest request, AdminRealm realm) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (realm.cookieName().equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie cookie(AdminRealm realm, String value, Duration maxAge) {
        return ResponseCookie.from(realm.cookieName(), value)
                .httpOnly(true)
                .secure(properties.security().cookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
