package az.cafeloyalty.security.admin;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import az.cafeloyalty.security.token.SecureTokens;
import az.cafeloyalty.shared.error.GlobalExceptionHandler;
import az.cafeloyalty.shared.i18n.AppLocaleResolver;
import az.cafeloyalty.tenancy.RequestScope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Admin sessiya cookie-sini yoxlayır və Spring Security kontekstini qurur.
 *
 * <ul>
 *   <li>işçi sessiyası yalnız şirkət domenində ({@code /api/v1/admin/**}), platforma sessiyası yalnız platforma
 *       domenində ({@code /api/v1/platform/**}) qəbul olunur;</li>
 *   <li>mütləq ömür (8 saat) və ya fəaliyyətsizlik limiti (30 dəq) keçibsə sessiya ləğv olunur və cavab
 *       {@code SESSION_EXPIRED} olur;</li>
 *   <li>{@code X-Background: 1} başlıqlı fon sorğuları fəaliyyətsizlik taymerini uzatmır.</li>
 * </ul>
 */
public class AdminSessionFilter extends OncePerRequestFilter {

    public static final String SESSION_EXPIRED_ATTRIBUTE = GlobalExceptionHandler.SESSION_EXPIRED_ATTRIBUTE;

    private final AdminRealms realms;
    private final AdminSessionCookies cookies;
    private final AdminAuthProperties properties;
    private final Clock clock;

    public AdminSessionFilter(AdminRealms realms, AdminSessionCookies cookies, AdminAuthProperties properties, Clock clock) {
        this.realms = realms;
        this.cookies = cookies;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        realmFor(request).ifPresent(realm -> cookies.read(request, realm)
                .ifPresent(token -> authenticate(request, response, realm, token)));
        chain.doFilter(request, response);
    }

    static Optional<AdminRealm> realmFor(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        RequestScope scope = RequestScope.of(request);
        if (scope == RequestScope.TENANT && (path.startsWith("/api/v1/admin/") || path.startsWith("/api/v1/dev/"))) {
            return Optional.of(AdminRealm.STAFF);
        }
        if (scope == RequestScope.PLATFORM && path.startsWith("/api/v1/platform/")) {
            return Optional.of(AdminRealm.PLATFORM);
        }
        return Optional.empty();
    }

    private void authenticate(HttpServletRequest request, HttpServletResponse response, AdminRealm realm, String token) {
        Instant now = clock.instant();
        AdminSessionStore sessions = realms.sessions(realm);
        Optional<AdminSession> found = sessions.findActiveByTokenHash(SecureTokens.hash(token));
        if (found.isEmpty()) {
            expired(request, response, realm);
            return;
        }
        AdminSession session = found.get();
        boolean idle = session.lastActivityAt().plus(properties.sessionIdle()).isBefore(now);
        if (!session.absoluteExpiresAt().isAfter(now) || idle) {
            sessions.revoke(session.id(), idle ? "IDLE_TIMEOUT" : "EXPIRED", now);
            expired(request, response, realm);
            return;
        }
        Optional<AdminAccount> account = realms.accounts(realm).findById(session.userId())
                .filter(a -> a.status() == AdminAccount.AccountStatus.ACTIVE);
        if (account.isEmpty()) {
            sessions.revoke(session.id(), "ACCOUNT_INACTIVE", now);
            expired(request, response, realm);
            return;
        }
        Set<RoleGrant> roles = realms.accounts(realm).loadRoles(session.userId());
        AdminPrincipal principal = new AdminPrincipal(realm, session.userId(), session.id(), account.get().email(),
                account.get().displayName(), account.get().preferredLanguage(), session.state(), roles,
                account.get().totpEnabled(), session.stepUpAt());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new AdminAuthentication(principal));
        SecurityContextHolder.setContext(context);
        request.setAttribute(AppLocaleResolver.PROFILE_LANGUAGE_ATTRIBUTE, account.get().preferredLanguage());

        if (!"1".equals(request.getHeader("X-Background"))) {
            sessions.touch(session.id(), now);
        }
    }

    private void expired(HttpServletRequest request, HttpServletResponse response, AdminRealm realm) {
        request.setAttribute(SESSION_EXPIRED_ATTRIBUTE, Boolean.TRUE);
        cookies.clear(response, realm);
    }
}
