package az.cafeloyalty.security.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kriptoqrafik sirlər (yalnız env-dən gəlir, heç vaxt loga yazılmır).
 *
 * @param keks              master açarlar (KEK): {@code id:base64,id2:base64}; köhnələri rotasiya üçün saxlanılır
 * @param activeKekId       yeni DEK-lərin şifrələndiyi KEK
 * @param phoneHmacKey      telefon axtarışı üçün HMAC açarının mənbəyi (tenant üzrə HKDF ilə törədilir)
 * @param otpPepper         OTP kodlarının hash-i üçün pepper
 * @param redemptionCodeKey mükafat kodlarının hash-i üçün açar
 */
@ConfigurationProperties("app.crypto")
public record CryptoProperties(
        String keks,
        String activeKekId,
        String phoneHmacKey,
        String otpPepper,
        String redemptionCodeKey) {

    @Override
    public String toString() {
        // Sirlər heç vaxt toString ilə loga düşməsin
        return "CryptoProperties[activeKekId=" + activeKekId + "]";
    }
}
