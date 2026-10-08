package az.cafeloyalty.security.totp;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * TOTP (RFC 6238: vaxta əsaslanan birdəfəlik kod, Google Authenticator və s. ilə uyğun):
 * HMAC-SHA1, 6 rəqəm, 30 saniyəlik addım, ±1 addım tolerantlığı (saat fərqi üçün).
 *
 * <p>Eyni kodun təkrar istifadəsinin qarşısı: uğurlu yoxlamada istifadə olunan addım qaytarılır və çağıran onu
 * saxlayır; növbəti dəfə yalnız daha böyük addım qəbul olunur.
 */
@Component
public class TotpService {

    public static final int SECRET_BYTES = 20;
    static final int DIGITS = 6;
    static final long STEP_SECONDS = 30;
    private static final int WINDOW = 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;

    public TotpService(Clock clock) {
        this.clock = clock;
    }

    public byte[] generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return secret;
    }

    /** Autentifikator tətbiqi üçün QR koda yerləşdirilən URI. */
    public String otpauthUri(String issuer, String account, byte[] secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + Base32.encode(secret) + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /**
     * Kodu yoxlayır. Uğurlu olarsa istifadə olunan zaman addımını qaytarır.
     *
     * @param lastUsedStep əvvəl qəbul edilmiş son addım (yoxdursa null); ondan kiçik və ya bərabər addımlar rədd edilir
     */
    public OptionalLong verify(byte[] secret, String code, Long lastUsedStep) {
        if (code == null || !code.matches("^\\d{" + DIGITS + "}$")) {
            return OptionalLong.empty();
        }
        long currentStep = clock.millis() / 1000 / STEP_SECONDS;
        for (long step = currentStep - WINDOW; step <= currentStep + WINDOW; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) {
                continue;
            }
            byte[] expected = codeForStep(secret, step).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    /** RFC 4226 HOTP: dinamik kəsmə ilə 6 rəqəmli kod. */
    static String codeForStep(byte[] secret, long step) {
        return codeForStep(secret, step, DIGITS);
    }

    static String codeForStep(byte[] secret, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, digits);
            return String.format(Locale.ROOT, "%0" + digits + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP computation failed", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
