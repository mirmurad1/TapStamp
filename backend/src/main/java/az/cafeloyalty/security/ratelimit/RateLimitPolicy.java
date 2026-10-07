package az.cafeloyalty.security.ratelimit;

import java.time.Duration;

/**
 * Sorğu tezliyi limitləri (rate limiting). Hər limit "pəncərə ərzində N cəhd" kimi işləyir; limit aşılanda
 * 429 + {@code Retry-After} qaytarılır. Dəyərlər {@code app.rate-limits.*} ilə dəyişdirilə bilər.
 */
public enum RateLimitPolicy {
    /** İşçi və platforma girişi — IP üzrə (hesab bloklanmasına əlavə). */
    ADMIN_LOGIN_PER_IP(20, Duration.ofMinutes(15)),
    /** Mövcud olmayan email-lə uğursuz girişlər — mövcud hesabın bloklanması ilə eyni davranış üçün. */
    ADMIN_LOGIN_FAILURE_PER_EMAIL(5, Duration.ofMinutes(15)),
    PASSWORD_RESET_PER_EMAIL(3, Duration.ofHours(1)),
    PASSWORD_RESET_PER_IP(10, Duration.ofHours(1)),
    TOKEN_REDEEM_PER_IP(20, Duration.ofMinutes(15)),
    /** Müştəri OTP göndərişi — telefon üzrə (15 dəqiqədə 3 və gündə 10). */
    OTP_REQUEST_PER_PHONE(3, Duration.ofMinutes(15)),
    OTP_REQUEST_PER_PHONE_DAILY(10, Duration.ofDays(1)),
    /** Kafenin Wi-Fi-ında çox müştəri eyni IP-dən gəlir — limit yumşaqdır. */
    OTP_REQUEST_PER_IP(20, Duration.ofHours(1)),
    /** Bir şirkətə hücum digərlərinin SMS limitini tükətmir. */
    OTP_REQUEST_PER_TENANT(100, Duration.ofMinutes(1)),
    /** Bütün platforma üzrə — SMS xərcinin qorunması. */
    OTP_REQUEST_GLOBAL(300, Duration.ofMinutes(1)),
    OTP_VERIFY_PER_IP(60, Duration.ofHours(1)),
    /** Bir telefon üçün gündə ən çox 10 uğursuz OTP yoxlaması (bütün sorğular üzrə cəmi). */
    OTP_VERIFY_FAILURE_PER_PHONE(10, Duration.ofDays(1));

    private final int defaultCapacity;
    private final Duration window;

    RateLimitPolicy(int defaultCapacity, Duration window) {
        this.defaultCapacity = defaultCapacity;
        this.window = window;
    }

    public int defaultCapacity() {
        return defaultCapacity;
    }

    public Duration window() {
        return window;
    }
}
