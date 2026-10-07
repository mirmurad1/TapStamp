package az.cafeloyalty.customer.auth;

import java.io.IOException;

import az.cafeloyalty.shared.error.GlobalExceptionHandler;
import az.cafeloyalty.shared.i18n.AppLocaleResolver;
import az.cafeloyalty.shared.web.ClientInfo;
import az.cafeloyalty.tenancy.RequestScope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Müştərinin access token cookie-sini yoxlayır və Spring Security kontekstini qurur. Yalnız şirkət domenində və
 * admin/dev yollarından kənarda işləyir (işçi sessiyası ilə qarışmır).
 */
public class CustomerSessionFilter extends OncePerRequestFilter {

    private final CustomerSessionService sessions;
    private final CustomerCookies cookies;

    public CustomerSessionFilter(CustomerSessionService sessions, CustomerCookies cookies) {
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (applies(request)) {
            cookies.readAccess(request).ifPresent(token -> authenticate(request, token));
        }
        chain.doFilter(request, response);
    }

    static boolean applies(HttpServletRequest request) {
        if (RequestScope.of(request) != RequestScope.TENANT) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/") && !path.startsWith("/api/v1/admin/") && !path.startsWith("/api/v1/dev/");
    }

    private void authenticate(HttpServletRequest request, String token) {
        sessions.authenticate(token, ClientInfo.of(request)).ifPresentOrElse(principal -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new CustomerPrincipal.Authentication(principal));
            SecurityContextHolder.setContext(context);
            request.setAttribute(AppLocaleResolver.PROFILE_LANGUAGE_ATTRIBUTE, principal.language());
        }, () -> request.setAttribute(GlobalExceptionHandler.SESSION_EXPIRED_ATTRIBUTE, Boolean.TRUE));
    }
}
