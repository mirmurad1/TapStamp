package az.tapstamp.security.csrf;

import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * SPA üçün CSRF (başqa saytın istifadəçi adından sorğu göndərməsi) qorunması.
 *
 * <ul>
 *   <li>Başlıqda gələn token ({@code X-XSRF-TOKEN}) cookie-dəki xam dəyərlə müqayisə olunur
 *       (double-submit: başqa sayt bizim cookie-ni oxuya bilmədiyi üçün başlığı qura bilmir).</li>
 *   <li>Server tərəfindən render olunan tokenlər BREACH hücumuna qarşı XOR ilə maskalanır.</li>
 *   <li>Token hər sorğuda yüklənir ki, cookie frontend-ə vaxtında çatsın.</li>
 * </ul>
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.xor.handle(request, response, csrfToken);
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return (StringUtils.hasText(headerValue) ? this.plain : this.xor).resolveCsrfTokenValue(request, csrfToken);
    }
}
