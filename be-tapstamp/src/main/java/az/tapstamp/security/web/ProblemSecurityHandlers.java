package az.tapstamp.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Spring Security filtrlərində yaranan 401/403 xətalarını MVC-nin {@code GlobalExceptionHandler}-inə ötürür.
 * Beləliklə filtr səviyyəsindəki xətalar da eyni lokallaşdırılmış Problem Details formatında qaytarılır.
 *
 * <p>Resolver tənbəl (lazy) götürülür: veb serversiz işə salmada (məs. {@code --create-platform-admin} əmri,
 * {@code web-application-type=none}) MVC bean-ləri yoxdur, amma tətbiq yenə başlamalıdır.
 */
@Component
public class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectProvider<HandlerExceptionResolver> resolver;

    public ProblemSecurityHandlers(@Qualifier("handlerExceptionResolver")
                                   ObjectProvider<HandlerExceptionResolver> resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                         @NonNull AuthenticationException ex) {
        resolver.getObject().resolveException(request, response, null, ex);
    }

    @Override
    public void handle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                       @NonNull AccessDeniedException ex) {
        resolver.getObject().resolveException(request, response, null, ex);
    }
}
