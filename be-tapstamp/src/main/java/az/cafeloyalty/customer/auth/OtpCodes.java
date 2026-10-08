package az.cafeloyalty.customer.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import az.cafeloyalty.security.crypto.CryptoProperties;
import az.cafeloyalty.security.crypto.Hkdf;
import org.springframework.stereotype.Component;

/**
 * OTP kodları: 6 təsadüfi rəqəm (SecureRandom). Bazada yalnız {@code HMAC-SHA256(pepper, challengeId ‖ kod)} saxlanılır —
 * baza sızsa belə 10⁶ variantı yoxlamaq üçün pepper lazımdır, o isə yalnız env-dədir. Challenge ID-si hash-ə daxildir,
 * ona görə eyni kodun iki sorğudakı hash-ləri fərqlidir.
 */
@Component
public class OtpCodes {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] pepper;

    public OtpCodes(CryptoProperties crypto) {
        if (crypto.otpPepper() == null || crypto.otpPepper().isBlank()) {
            throw new IllegalStateException("app.crypto.otp-pepper təyin olunmayıb");
        }
        this.pepper = Base64.getDecoder().decode(crypto.otpPepper().trim());
        if (pepper.length < 32) {
            throw new IllegalStateException("app.crypto.otp-pepper ən azı 32 bayt olmalıdır");
        }
    }

    public String generate() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    public byte[] hash(UUID challengeId, String code) {
        return Hkdf.hmac(pepper, (challengeId + ":" + code).getBytes(StandardCharsets.UTF_8));
    }

    /** Sabit zamanlı müqayisə: cavab vaxtına görə kodun hansı rəqəmlərinin düz olduğunu təxmin etmək olmur. */
    public boolean matches(UUID challengeId, String code, byte[] expectedHash) {
        if (code == null || !code.matches("^\\d{6}$")) {
            return false;
        }
        return MessageDigest.isEqual(hash(challengeId, code), expectedHash);
    }
}
