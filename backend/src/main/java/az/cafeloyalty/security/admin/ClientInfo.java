package az.cafeloyalty.security.admin;

import jakarta.servlet.http.HttpServletRequest;

/** Sorğunun mənbəyi (IP nginx-in X-Forwarded-For başlığından, yalnız etibarlı proxy-dən qəbul olunur). */
public record ClientInfo(String ip, String userAgent) {

    public static ClientInfo of(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
