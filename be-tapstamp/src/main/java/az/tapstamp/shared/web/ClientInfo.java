package az.tapstamp.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/** Sorğunun mənbəyi (IP nginx-in X-Forwarded-For başlığından, yalnız etibarlı proxy-dən qəbul olunur). */
public record ClientInfo(String ip, String userAgent) {

    public static ClientInfo of(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
