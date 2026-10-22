package az.tapstamp.plan.web;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import az.tapstamp.plan.PlanService;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.TenantInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Sınaq müddəti bitmiş şirkət: şirkətin bütün API-si {@code PLAN_TRIAL_EXPIRED} (402) qaytarır — həm işçi paneli,
 * həm müştəri tətbiqi, həm NFC toxunuşları (çipin {@code /t/} yönləndirməsi frontend-ə çatır, möhür isə API ilə
 * alınır). Yalnız frontend-in "xidmət dayandırılıb" ekranını göstərməsi üçün
 * lazım olanlar (kontekst, paket, şirkətin adı və loqosu, CSRF) açıq qalır.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class TrialExpiredFilter extends OncePerRequestFilter {

    private static final List<String> OPEN_PREFIXES = List.of(
            "/api/v1/context", "/api/v1/csrf", "/api/v1/public/plan","/api/v1/public/company", "/api/v1/public/icons/");

    private final PlanService plans;
    private final ObjectProvider<HandlerExceptionResolver> resolver;

    public TrialExpiredFilter(PlanService plans,
                              @Qualifier("handlerExceptionResolver") ObjectProvider<HandlerExceptionResolver> resolver) {
        this.plans = plans;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Optional<TenantInfo> tenant = TenantContext.current();
        boolean blocked = tenant.isPresent() && path.startsWith("/api/")
                && OPEN_PREFIXES.stream().noneMatch(path::startsWith) && plans.expired(tenant.get().id());
        if (blocked) {
            resolver.getObject().resolveException(request, response, null, new ApiException(ErrorCode.PLAN_TRIAL_EXPIRED));
            return;
        }
        chain.doFilter(request, response);
    }
}
