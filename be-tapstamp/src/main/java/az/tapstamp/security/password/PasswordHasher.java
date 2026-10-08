package az.tapstamp.security.password;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Şifrələrin Argon2id ilə hash-lənməsi (yaddaş tələb edən, GPU ilə sındırılması çətin alqoritm).
 * Parametrlər OWASP tövsiyəsinə uyğundur: m=19 MiB, t=2, p=1. Parametrlər dəyişərsə, köhnə hash-lər
 * uğurlu girişdə avtomatik yenilənir ({@link #needsRehash}).
 */
@Component
public class PasswordHasher {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19_456;
    private static final int ITERATIONS = 2;

    private final Argon2PasswordEncoder encoder =
            new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS);

    /** Mövcud olmayan hesab üçün də eyni müddətli hesablama aparmaq üçün (cavab vaxtına görə təxmin olmasın). */
    private final String dummyHash = encoder.encode("dummy-password-for-timing-equalization");

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hash) {
        if (hash == null || hash.isBlank()) {
            encoder.matches(rawPassword, dummyHash);
            return false;
        }
        return encoder.matches(rawPassword, hash);
    }

    /** Hesab yoxdursa və ya bloklanıbsa da Argon2 hesablaması aparılır. */
    public void simulateVerification(String rawPassword) {
        encoder.matches(rawPassword == null ? "" : rawPassword, dummyHash);
    }

    public boolean needsRehash(String hash) {
        return hash != null && encoder.upgradeEncoding(hash);
    }
}
