package az.cafeloyalty.nfc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

import az.cafeloyalty.config.AppProperties;
import az.cafeloyalty.security.token.SecureTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Brauzerin təsadüfi identifikatoru ({@code __Host-cl_bid}, HttpOnly, 1 il). {@code tap_token} bu dəyərin hash-inə
 * bağlanır: {@code /tap/{token}} linkini başqa brauzerə göndərmək işə yaramır. Hash fırıldaq analitikasında da
 * istifadə olunur (bir brauzerdən çoxlu hesab). Şəxsi məlumat deyil — heç nəyə bağlı olmayan təsadüfi dəyərdir.
 */
@Component
public class BrowserIdCookie {

    public static final String NAME = "__Host-cl_bid";
    private static final Duration MAX_AGE = Duration.ofDays(365);
    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final AppProperties properties;

    public BrowserIdCookie(AppProperties properties) {
        this.properties = properties;
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (NAME.equals(cookie.getName()) && FORMAT.matcher(cookie.getValue()).matches()) {
                    return Optional.of(cookie.getValue());
                }
            }
        }
        return Optional.empty();
    }

    /** Mövcud identifikatoru qaytarır, yoxdursa yenisini yaradıb cookie kimi qoyur. */
    public String ensure(HttpServletRequest request, HttpServletResponse response) {
        return read(request).orElseGet(() -> {
            String value = SecureTokens.generate();
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NAME, value)
                    .httpOnly(true)
                    .secure(properties.security().cookieSecure())
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(MAX_AGE)
                    .build()
                    .toString());
            return value;
        });
    }

    /** Tokendə saxlanılan bağlama dəyəri; identifikator yoxdursa heç bir tokenə uyğun gəlməyən sabit. */
    public static byte[] bindingHash(String browserId) {
        return sha256("cl_bid:" + (browserId == null ? "" : browserId));
    }

    public static byte[] userAgentHash(String userAgent) {
        return userAgent == null || userAgent.isBlank() ? null : sha256("ua:" + userAgent);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
