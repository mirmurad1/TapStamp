package az.tapstamp.tenancy.web;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import az.tapstamp.config.AppProperties;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.AppLocaleResolver;
import az.tapstamp.tenancy.RequestScope;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.TenantInfo;
import az.tapstamp.tenancy.registry.TenantDirectory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Sorğunun {@code Host} başlığına görə tenant-ı müəyyən edir (hər şirkətin öz domeni var).
 * Tenant heç vaxt sorğunun body-sindən və ya parametrlərindən götürülmür.
 *
 * <ul>
 *   <li>şirkətin domeni → {@link TenantContext} qurulur;</li>
 *   <li>platformanın domeni → tenant yoxdur, yalnız platforma endpoint-ləri işləyir;</li>
 *   <li>platforma endpoint-ləri şirkət domenində, şirkət endpoint-ləri platforma domenində 404 qaytarır;</li>
 *   <li>tanınmayan domen → 404, aktiv olmayan tenant → 503 (sorğu heç bir emala çatmır).</li>
 * </ul>
 * Kontekst sorğu bitəndə mütləq təmizlənir ki, thread-lər arasında sızma olmasın.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantResolutionFilter extends OncePerRequestFilter {

    /** Domendən asılı olmayan yollar (konteyner health check-i Host: localhost ilə gəlir). */
    private static final List<String> HOST_INDEPENDENT_PREFIXES = List.of(
            "/actuator/health", "/swagger-ui/", "/api/v1/docs", "/api/v1/openapi.json", "/internal/");
    /** Platforma domenində açıq olan yollar; qalanları (şirkət API-si) orada mövcud deyil. */
    private static final List<String> PLATFORM_PREFIXES = List.of(
            "/api/v1/platform/", "/api/v1/csrf", "/api/v1/context", "/api/v1/dev/");
    private static final String PLATFORM_API_PREFIX = "/api/v1/platform/";
    private static final Pattern VALID_HOST = Pattern.compile("^[a-z0-9.-]{1,253}$");

    private final TenantDirectory directory;
    private final AppProperties properties;
    private final HandlerExceptionResolver resolver;

    public TenantResolutionFilter(TenantDirectory directory, AppProperties properties,
                                  @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.directory = directory;
        this.properties = properties;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (HOST_INDEPENDENT_PREFIXES.stream().anyMatch(path::startsWith)) {
            request.setAttribute(RequestScope.ATTRIBUTE, RequestScope.NONE);
            chain.doFilter(request, response);
            return;
        }

        String host = normalize(request.getServerName());
        if (host.equals(properties.platformDomain().toLowerCase(Locale.ROOT))) {
            if (PLATFORM_PREFIXES.stream().noneMatch(path::startsWith)) {
                resolver.resolveException(request, response, null, new ApiException(ErrorCode.NOT_FOUND));
                return;
            }
            request.setAttribute(RequestScope.ATTRIBUTE, RequestScope.PLATFORM);
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith(PLATFORM_API_PREFIX)) {
            resolver.resolveException(request, response, null, new ApiException(ErrorCode.NOT_FOUND));
            return;
        }
        Optional<TenantInfo> tenant = VALID_HOST.matcher(host).matches() ? directory.findByHost(host) : Optional.empty();
        if (tenant.isEmpty()) {
            resolver.resolveException(request, response, null, new ApiException(ErrorCode.NOT_FOUND));
            return;
        }
        if (tenant.get().status().outOfService()) {
            resolver.resolveException(request, response, null, new ApiException(ErrorCode.TENANT_UNAVAILABLE));
            return;
        }

        request.setAttribute(RequestScope.ATTRIBUTE, RequestScope.TENANT);
        request.setAttribute(AppLocaleResolver.TENANT_DEFAULT_LANGUAGE_ATTRIBUTE, tenant.get().defaultLanguage());
        TenantContext.bind(tenant.get());
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static String normalize(String host) {
        String lower = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        return lower.endsWith(".") ? lower.substring(0, lower.length() - 1) : lower;
    }
}
