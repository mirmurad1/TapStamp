package az.tapstamp.shared.error;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import az.tapstamp.shared.error.ProblemDetailFactory.FieldViolation;
import az.tapstamp.shared.i18n.AppLocaleResolver;
import az.tapstamp.shared.i18n.Language;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Bütün xətaları vahid, lokallaşdırılmış Problem Details formatına çevirir.
 * İstifadəçiyə stack trace və ya daxili istisna mesajı heç vaxt qaytarılmır.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Sessiya filtri sessiyanın vaxtının bitdiyini bu atributla bildirir: 401 cavabı SESSION_EXPIRED kodunu alır. */
    public static final String SESSION_EXPIRED_ATTRIBUTE = "az.tapstamp.session-expired";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problems;
    private final AppLocaleResolver localeResolver;

    public GlobalExceptionHandler(ProblemDetailFactory problems, AppLocaleResolver localeResolver) {
        this.problems = problems;
        this.localeResolver = localeResolver;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = problems.create(ex.code(), locale(request), ex.messageArgs(), ex.properties());
        HttpHeaders headers = new HttpHeaders();
        if (ex.properties().get("retryAfterSeconds") instanceof Number seconds) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(seconds.longValue()));
        }
        return ResponseEntity.status(ex.code().status()).headers(headers).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(unauthenticated(request), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        if (ex instanceof CsrfException) {
            return respond(ErrorCode.CSRF_INVALID, request);
        }
        // Daxil olmamış istifadəçi üçün 403 əvəzinə 401: əvvəlcə giriş lazımdır
        return respond(isAnonymous() ? unauthenticated(request) : ErrorCode.FORBIDDEN, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(
                        v.getPropertyPath().toString(),
                        v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        v.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(problems.validation(locale(request), violations));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Gözlənilməz xəta: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .toList();
        return ResponseEntity.badRequest().body(problems.validation(locale(request), violations));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error -> new FieldViolation(
                        result.getMethodParameter().getParameterName(),
                        error.getCodes() != null && error.getCodes().length > 0
                                ? lastSegment(error.getCodes()[error.getCodes().length - 1])
                                : "Invalid",
                        error.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(problems.validation(locale(request), violations));
    }

    /** Spring-in özünün yaratdığı bütün xətalar (404, 405, 415, oxunmayan JSON və s.) buradan keçir. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = ErrorCode.fromStatus(statusCode);
        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("Daxili xəta", ex);
        }
        ProblemDetail problem = problems.create(code, locale(request), new Object[0], Map.of());
        return ResponseEntity.status(code.status()).headers(headers).body(problem);
    }

    private ResponseEntity<ProblemDetail> respond(ErrorCode code, HttpServletRequest request) {
        return ResponseEntity.status(code.status()).body(problems.create(code, locale(request)));
    }

    private Locale locale(HttpServletRequest request) {
        return localeResolver.resolveLocale(request);
    }

    private Locale locale(WebRequest request) {
        HttpServletRequest servletRequest = request instanceof NativeWebRequest nativeRequest
                ? nativeRequest.getNativeRequest(HttpServletRequest.class)
                : null;
        return servletRequest != null ? locale(servletRequest) : Language.DEFAULT.locale();
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getCode(), error.getDefaultMessage());
    }

    private static String lastSegment(String code) {
        int dot = code.lastIndexOf('.');
        return dot >= 0 ? code.substring(dot + 1) : code;
    }

    private static ErrorCode unauthenticated(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(SESSION_EXPIRED_ATTRIBUTE))
                ? ErrorCode.SESSION_EXPIRED : ErrorCode.UNAUTHENTICATED;
    }

    private static boolean isAnonymous() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated();
    }
}
