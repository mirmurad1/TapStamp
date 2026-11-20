package az.tapstamp.shared.error;

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
    /** Loqo faylı tələblərə uyğun deyil (növ, həcm, ölçü). */
    LOGO_INVALID(HttpStatus.BAD_REQUEST),
    /** Kataloq şəkli uyğun deyil (növ, həcm, ölçü). */
    CATALOG_IMAGE_INVALID(HttpStatus.BAD_REQUEST),
    /** Kataloqda kateqoriya və ya məhsul limiti dolub. */
    CATALOG_LIMIT_REACHED(HttpStatus.CONFLICT),
    /** Kateqoriyada məhsul var — əvvəlcə məhsulları silmək və ya köçürmək lazımdır. */
    CATALOG_CATEGORY_NOT_EMPTY(HttpStatus.CONFLICT),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    /** Şirkətin (yeni nömrələrə) və ya platformanın gündəlik SMS kod limiti dolub. */
    SMS_DAILY_LIMIT_REACHED(HttpStatus.TOO_MANY_REQUESTS),
    /** Serverdə web push qurulmayıb (VAPID açarları yoxdur). */
    PUSH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    /** Saytın poçt qutusu qurulmayıb və ya email göndərilmədi — müraciət qəbul olunmadı. */
    SITE_INBOX_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    /** Bu imkan şirkətin paketinə daxil deyil. */
    PLAN_FEATURE_UNAVAILABLE(HttpStatus.FORBIDDEN),
    /** Paketin limiti (filial, işçi) dolub. */
    PLAN_LIMIT_REACHED(HttpStatus.CONFLICT),
    TIER_THRESHOLD_TAKEN(HttpStatus.CONFLICT),
    TIER_IN_USE(HttpStatus.CONFLICT),
    /** Sınaq müddəti bitib, ödəniş qeyd olunmayıb — şirkətin xidməti dayandırılıb. */
    PLAN_TRIAL_EXPIRED(HttpStatus.PAYMENT_REQUIRED),
    /** Bu paketin sınağı bu şirkət üçün artıq istifadə olunub (hər paket üçün bir dəfə). */
    PLAN_TRIAL_ALREADY_USED(HttpStatus.CONFLICT),
    /** Sessiyanın vaxtı bitib (fəaliyyətsizlik və ya mütləq limit) — yenidən daxil olmaq lazımdır. */
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED),
    /** Email və ya şifrə yanlışdır (hansının səhv olduğu açıqlanmır). */
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED),
    /** Çox sayda uğursuz cəhd: hesab müvəqqəti bloklanıb (mövcud olmayan email-lər üçün də eyni cavab). */
    LOGIN_TEMPORARILY_BLOCKED(HttpStatus.TOO_MANY_REQUESTS),
    MFA_INVALID(HttpStatus.UNAUTHORIZED),
    MFA_ALREADY_ENABLED(HttpStatus.CONFLICT),
    MFA_NOT_ENABLED(HttpStatus.CONFLICT),
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
    /** Toxunuş linkinin formatı yanlışdır (çip düzgün proqramlaşdırılmayıb və ya link əl ilə dəyişdirilib). */
    TAP_MALFORMED(HttpStatus.BAD_REQUEST),
    /** Bu şirkətdə belə çip qeydiyyatda deyil. */
    TAG_UNKNOWN(HttpStatus.NOT_FOUND),
    /** Çip deaktiv edilib. */
    TAG_DISABLED(HttpStatus.FORBIDDEN),
    /** Çipin imzası yanlışdır. Dəqiq səbəb (PICC, UID, CMAC) yalnız toxunuş jurnalındadır. */
    TAP_SIGNATURE_INVALID(HttpStatus.BAD_REQUEST),
    /** Bu toxunuş linki artıq istifadə olunub (köhnə sayğac) — çipə yenidən toxunmaq lazımdır. */
    TAP_REPLAYED(HttpStatus.CONFLICT),
    /** Toxunuşun vaxtı keçib (60 saniyə) və ya token tanınmır. */
    TAP_TOKEN_EXPIRED(HttpStatus.GONE),
    /** Bu toxunuş artıq istifadə olunub. */
    TAP_TOKEN_USED(HttpStatus.CONFLICT),
    /** Toxunuş başqa brauzerdə edilib — link başqasına ötürülə bilməz. */
    TAP_BINDING_MISMATCH(HttpStatus.FORBIDDEN),
    /** Növbəti möhür üçün hələ tezdir; cavabda {@code retryAfterSeconds} var. */
    STAMP_COOLDOWN_ACTIVE(HttpStatus.CONFLICT),
    /** Bu günün möhür limiti dolub; cavabda növbəti günə qədər {@code retryAfterSeconds} var. */
    STAMP_DAILY_LIMIT_REACHED(HttpStatus.CONFLICT),
    /** Filial hazırda bağlıdır (iş saatları qaydası aktivdir). */
    OUTSIDE_BUSINESS_HOURS(HttpStatus.CONFLICT),
    /** Çipin filialı şirkət tərəfindən bağlanıb (deaktiv): möhür verilmir. */
    BRANCH_CLOSED(HttpStatus.CONFLICT),
    /** Möhür artıq təsdiqlənib, rədd edilib və ya vaxtı keçib. */
    STAMP_NOT_PENDING(HttpStatus.CONFLICT),
    /** Belə hədiyyə kartı yoxdur (və ya başqa şirkətindir). */
    GIFT_CARD_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** Hədiyyə kartı artıq istifadə olunub; cavabda {@code redeemedAt}. */
    GIFT_CARD_ALREADY_REDEEMED(HttpStatus.CONFLICT),
    /** Hədiyyə kartının müddəti bitib. */
    GIFT_CARD_EXPIRED(HttpStatus.GONE),
    /** Hədiyyə kartı ləğv edilib (məs. hesab silinib). */
    GIFT_CARD_REVOKED(HttpStatus.CONFLICT),
    /** Şirkət dayandırılıb və ya bazası hazır deyil (məs. miqrasiya uğursuz olub). */
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT),
    STAMP_ADJUSTMENT_TOO_LARGE(HttpStatus.CONFLICT),
    TAG_ROTATION_IN_PROGRESS(HttpStatus.CONFLICT),
    PROVISIONING_ALREADY_EXPORTED(HttpStatus.CONFLICT),
    /** Köçürmə paketi yalnız deaktiv çip üçündür. */
    TAG_NOT_DISABLED(HttpStatus.CONFLICT),
    DOMAIN_IS_PRIMARY(HttpStatus.CONFLICT),
    CAMPAIGN_NO_RECIPIENTS(HttpStatus.CONFLICT),
    CAMPAIGN_STATE_CHANGED(HttpStatus.CONFLICT),
    TENANT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private static final String TYPE_PREFIX = "urn:tapstamp:problem:";

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

    /** Problem Details {@code type} sahəsi, məs. {@code urn:tapstamp:problem:rate-limited}. */
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
