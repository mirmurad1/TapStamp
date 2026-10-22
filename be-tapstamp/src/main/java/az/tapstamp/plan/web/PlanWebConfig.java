package az.tapstamp.plan.web;

import az.tapstamp.plan.PlanService;
import az.tapstamp.plan.RequiresFeature;
import az.tapstamp.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** {@link RequiresFeature} annotasiyasının yoxlanması: imkan şirkətin paketində yoxdursa, sorğu controller-ə çatmır. */
@Configuration
public class PlanWebConfig implements WebMvcConfigurer {

    private final PlanService plans;

    public PlanWebConfig(PlanService plans) {
        this.plans = plans;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull Object handler) {
                if (handler instanceof HandlerMethod method && TenantContext.current().isPresent()) {
                    RequiresFeature required = method.getMethodAnnotation(RequiresFeature.class);
                    if (required == null) {
                        required = method.getBeanType().getAnnotation(RequiresFeature.class);
                    }
                    if (required != null) {
                        plans.require(required.value());
                    }
                }
                return true;
            }
        });
    }
}
