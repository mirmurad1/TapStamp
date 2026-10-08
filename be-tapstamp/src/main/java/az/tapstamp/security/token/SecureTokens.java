package az.tapstamp.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * Sessiya, şifrə bərpası və dəvət tokenləri. Token 256 bit təsadüfi dəyərdir; bazada yalnız SHA-256 hash-i
 * saxlanılır — baza sızsa belə, tokenlərdən istifadə etmək olmur.
 */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    private SecureTokens() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Bərpa kodu: "xxxxx-xxxxx" (oxunaqlı əlifba, ~50 bit). */
    public static String recoveryCode() {
        StringBuilder code = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                code.append('-');
            }
            code.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
        }
        return code.toString();
    }

    public static String normalizeRecoveryCode(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
