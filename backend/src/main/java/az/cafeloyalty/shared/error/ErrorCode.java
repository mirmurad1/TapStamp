package az.cafeloyalty.shared.error;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Dildən asılı olmayan, sabit xəta kodları. Frontend mesajı bu koda görə öz tərcüməsindən göstərir.
 * Hər kod üçün {@code error.<KOD>.title} və {@code error.<KOD>.detail} açarları 3 dildə mövcud olmalıdır
 * (bunu {@code MessagesParityTest} yoxlayır).
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    CSRF_INVALID(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    /** Sessiyanın vaxtı bitib (fəaliyyətsizlik və ya mütləq limit) — yenidən daxil olmaq lazımdır. */
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED),
    /** Email və ya şifrə yanlışdır (hansının səhv olduğu açıqlanmır). */
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED),
    /** Çox sayda uğursuz cəhd: hesab müvəqqəti bloklanıb (mövcud olmayan email-lər üçün də eyni cavab). */
    LOGIN_TEMPORARILY_BLOCKED(HttpStatus.TOO_MANY_REQUESTS),
    MFA_INVALID(HttpStatus.UNAUTHORIZED),
    MFA_ALREADY_ENABLED(HttpStatus.CONFLICT),
    /** Həssas əməliyyat son 10 dəqiqədə TOTP təsdiqi tələb edir. */
    STEP_UP_REQUIRED(HttpStatus.FORBIDDEN),
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST),
    CURRENT_PASSWORD_INVALID(HttpStatus.BAD_REQUEST),
    /** Şifrə bərpası və ya dəvət linki etibarsızdır, vaxtı keçib və ya artıq istifadə olunub. */
    TOKEN_INVALID(HttpStatus.BAD_REQUEST),
    ALREADY_EXISTS(HttpStatus.CONFLICT),
    /** Bu instansiyada yeni şirkət yaratmaq söndürülüb (prod-da standart). */
    PROVISIONING_DISABLED(HttpStatus.CONFLICT),
    /** Telefon nömrəsi düzgün deyil və ya mobil nömrə deyil. */
    PHONE_INVALID(HttpStatus.BAD_REQUEST),
    /** Bu ölkənin nömrələri şirkət tərəfindən qəbul edilmir (SMS pumping-in qarşısı). */
    PHONE_REGION_NOT_ALLOWED(HttpStatus.BAD_REQUEST),
    /** OTP kodu yanlışdır; cavabda {@code attemptsRemaining} var. */
    OTP_INVALID(HttpStatus.BAD_REQUEST),
    /** OTP kodunun vaxtı keçib və ya yenisi istənilib — yeni kod lazımdır. */
    OTP_EXPIRED(HttpStatus.BAD_REQUEST),
    /** Çox sayda yanlış kod daxil edilib; bu kod artıq qəbul olunmur. */
    OTP_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
    /** Hüquqi sənədlərin (cari versiyalarına) razılıq tələb olunur. */
    CONSENT_REQUIRED(HttpStatus.FORBIDDEN),
    /** Eyni refresh token-i iki tab eyni anda istifadə edib; yeni cookie artıq brauzerdədir — sorğunu təkrarlayın. */
    REFRESH_RACE(HttpStatus.CONFLICT),
    /** Müştəri hesabı şirkət tərəfindən bloklanıb. */
    CUSTOMER_BLOCKED(HttpStatus.FORBIDDEN),
    /** Şirkət dayandırılıb və ya bazası hazır deyil (məs. miqrasiya uğursuz olub). */
    TENANT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private static final String TYPE_PREFIX = "urn:cafe-loyalty:problem:";

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public String titleKey() {
        return "error." + name() + ".title";
    }

    public String detailKey() {
        return "error." + name() + ".detail";
    }

    /** Problem Details {@code type} sahəsi, məs. {@code urn:cafe-loyalty:problem:rate-limited}. */
    public String typeUri() {
        return TYPE_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Spring-in özünün yaratdığı xətalar üçün HTTP statusuna uyğun kod. */
    public static ErrorCode fromStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400, 406 -> MALFORMED_REQUEST;
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> RATE_LIMITED;
            default -> status.is4xxClientError() ? MALFORMED_REQUEST : INTERNAL_ERROR;
        };
    }
}
