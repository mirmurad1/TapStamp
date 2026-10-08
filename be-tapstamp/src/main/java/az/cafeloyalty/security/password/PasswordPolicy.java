package az.cafeloyalty.security.password;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Şifrə siyasəti (NIST SP 800-63B): uzunluq, geniş yayılmış şifrələr siyahısı və email ilə oxşarlıq yoxlanılır.
 * "Böyük hərf + rəqəm + simvol" kimi tərkib qaydaları tətbiq olunmur — NIST onları tövsiyə etmir.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    public enum Violation {
        TOO_SHORT,
        TOO_LONG,
        COMMON_PASSWORD,
        CONTAINS_EMAIL,
        REPETITIVE
    }

    private final Set<String> commonPasswords;

    public PasswordPolicy() {
        this.commonPasswords = loadCommonPasswords();
    }

    public List<Violation> check(String password, String email) {
        List<Violation> violations = new ArrayList<>();
        if (password == null) {
            violations.add(Violation.TOO_SHORT);
            return violations;
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH) {
            violations.add(Violation.TOO_SHORT);
        }
        if (length > MAX_LENGTH) {
            violations.add(Violation.TOO_LONG);
        }
        String normalized = password.toLowerCase(Locale.ROOT);
        if (commonPasswords.contains(normalized)) {
            violations.add(Violation.COMMON_PASSWORD);
        }
        if (email != null) {
            String localPart = email.toLowerCase(Locale.ROOT).split("@", 2)[0];
            if (localPart.length() >= 4 && normalized.contains(localPart)) {
                violations.add(Violation.CONTAINS_EMAIL);
            }
        }
        if (password.codePoints().distinct().count() <= 3) {
            violations.add(Violation.REPETITIVE);
        }
        return violations;
    }

    private static Set<String> loadCommonPasswords() {
        Set<String> result = new HashSet<>();
        try (InputStream stream = PasswordPolicy.class.getResourceAsStream("/security/common-passwords.txt")) {
            if (stream == null) {
                throw new IllegalStateException("security/common-passwords.txt tapılmadı");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .map(line -> line.toLowerCase(Locale.ROOT))
                        .forEach(result::add);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Common password list could not be read", e);
        }
        return Set.copyOf(result);
    }
}
