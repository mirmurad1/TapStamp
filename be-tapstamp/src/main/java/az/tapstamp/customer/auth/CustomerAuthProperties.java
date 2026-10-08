package az.tapstamp.customer.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Müştəri autentifikasiyasının parametrləri.
 *
 * @param otpTtl             OTP kodunun ömrü
 * @param otpResendCooldown  eyni nömrəyə yeni kod göndərilməzdən əvvəl gözləmə
 * @param otpMaxAttempts     bir kod üçün cəhd sayı (sonra kod LOCKED olur)
 * @param registrationTtl    yeni nömrə təsdiqlənəndən sonra qeydiyyatı tamamlamaq üçün vaxt
 * @param accessTtl          access tokenin ömrü
 * @param refreshTtl         refresh tokenin ömrü (hər yeniləmədə uzanır)
 * @param refreshRaceWindow  istifadə olunmuş refresh tokeni bu müddət ərzində yenidən gələrsə — iki tabın yarışıdır,
 *                           sessiya ləğv olunmur
 * @param accessCacheTtl     access tokenin yoxlanması üçün yaddaş keşi (ləğvetmədə dərhal təmizlənir)
 * @param stepUpValidity     hesabın silinməsi kimi əməliyyatlar üçün OTP təkrar təsdiqinin etibarlılıq müddəti
 */
@ConfigurationProperties("app.customer-auth")
public record CustomerAuthProperties(
        @DefaultValue("5m") Duration otpTtl,
        @DefaultValue("60s") Duration otpResendCooldown,
        @DefaultValue("5") int otpMaxAttempts,
        @DefaultValue("10m") Duration registrationTtl,
        @DefaultValue("15m") Duration accessTtl,
        @DefaultValue("180d") Duration refreshTtl,
        @DefaultValue("10s") Duration refreshRaceWindow,
        @DefaultValue("15s") Duration accessCacheTtl,
        @DefaultValue("10m") Duration stepUpValidity) {
}
