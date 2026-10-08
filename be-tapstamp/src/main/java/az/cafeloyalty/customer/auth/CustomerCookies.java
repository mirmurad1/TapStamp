package az.cafeloyalty.customer.auth;

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
 * Müştəri sessiyasının cookie-ləri (hamısı HttpOnly — JavaScript oxuya bilmir):
 * <ul>
 *   <li>{@code __Host-cl_at} — access token, 15 dəqiqə, bütün yollar;</li>
 *   <li>{@code __Secure-cl_rt} — refresh token, 180 gün, yalnız {@code /api/v1/auth} yollarına göndərilir.</li>
 * </ul>
 * SameSite=Lax: NFC linki (başqa tətbiqdən açılan GET) cookie ilə gəlir, başqa saytın POST sorğusu isə yox.
 */
@Component
public class CustomerCookies {

    public static final String ACCESS = "__Host-cl_at";
    public static final String REFRESH = "__Secure-cl_rt";
    public static final String REFRESH_PATH = "/api/v1/auth";

    private final AppProperties properties;
    private final CustomerAuthProperties authProperties;

    public CustomerCookies(AppProperties properties, CustomerAuthProperties authProperties) {
        this.properties = properties;
        this.authProperties = authProperties;
    }

    public void write(HttpServletResponse response, CustomerSessionService.IssuedTokens tokens) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS, tokens.accessToken(), "/", authProperties.accessTtl()));
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(REFRESH, tokens.refreshToken(), REFRESH_PATH, authProperties.refreshTtl()));
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS, "", "/", Duration.ZERO));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH, "", REFRESH_PATH, Duration.ZERO));
    }

    public Optional<String> readAccess(HttpServletRequest request) {
        return read(request, ACCESS);
    }

    public Optional<String> readRefresh(HttpServletRequest request) {
        return read(request, REFRESH);
    }

    private static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private String cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.security().cookieSecure())
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
