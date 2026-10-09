package az.tapstamp.shared.error;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import az.tapstamp.shared.web.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Lokallaşdırılmış RFC 7807 cavablarını vahid formada yaradır. */
@Component
public class ProblemDetailFactory {

    private final MessageSource messageSource;

    public ProblemDetailFactory(@Qualifier(AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME) MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public ProblemDetail create(ErrorCode code, Locale locale) {
        return create(code, locale, new Object[0], Map.of());
    }

    public ProblemDetail create(ErrorCode code, Locale locale, Object[] args, Map<String, Object> properties) {
        ProblemDetail problem = ProblemDetail.forStatus(code.status());
        problem.setType(URI.create(code.typeUri()));
        problem.setTitle(messageSource.getMessage(code.titleKey(), null, locale));
        problem.setDetail(messageSource.getMessage(code.detailKey(), args, locale));
        problem.setProperty("code", code.name());
        properties.forEach(problem::setProperty);
        String traceId = MDC.get(RequestIdFilter.MDC_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }

    /** Validasiya xətası: hər sahə üçün sabit kod (məs. {@code NotBlank}) və lokallaşdırılmış mesaj. */
    public ProblemDetail validation(Locale locale, List<FieldViolation> violations) {
        ProblemDetail problem = create(ErrorCode.VALIDATION_FAILED, locale);
        problem.setProperty("errors", violations);
        return problem;
    }

    public record FieldViolation(String field, String code, String message) {
    }
}
