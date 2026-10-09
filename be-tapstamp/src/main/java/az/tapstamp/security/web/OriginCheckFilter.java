package az.tapstamp.security.web;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF tokeninə əlavə qat: dəyişdirici sorğular yalnız eyni origin-dən qəbul olunur.
 *
 * <ul>
 *   <li>{@code Sec-Fetch-Site} varsa, yalnız {@code same-origin} və ya {@code none} qəbul olunur.</li>
 *   <li>{@code Origin} varsa, sorğunun öz origin-i ilə (sxem + host + port) eyni olmalıdır.</li>
 * </ul>
 * Brauzer olmayan klientlər (bu başlıqları göndərməyən) yenə də CSRF tokeni ilə qorunur.
 */
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> ALLOWED_FETCH_SITES = Set.of("same-origin", "none");

    private final AccessDeniedHandler accessDeniedHandler;

    public OriginCheckFilter(AccessDeniedHandler accessDeniedHandler) {
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        if (!SAFE_METHODS.contains(request.getMethod()) && !isSameOrigin(request)) {
            accessDeniedHandler.handle(request, response, new CrossOriginRequestException());
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean isSameOrigin(HttpServletRequest request) {
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (fetchSite != null && !ALLOWED_FETCH_SITES.contains(fetchSite.toLowerCase(Locale.ROOT))) {
            return false;
        }
        String origin = request.getHeader("Origin");
        if (origin == null) {
            return true;
        }
        try {
            URI uri = URI.create(origin);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return false; // "null" origin və ya pozulmuş dəyər
            }
            return uri.getScheme().equalsIgnoreCase(request.getScheme())
                    && uri.getHost().equalsIgnoreCase(request.getServerName())
                    && effectivePort(uri) == request.getServerPort();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /** Başqa origin-dən gələn dəyişdirici sorğu; CSRF xətası kimi cavablandırılır. */
    static final class CrossOriginRequestException extends CsrfException {
        CrossOriginRequestException() {
            super("Cross-origin state-changing request rejected");
        }
    }
}
