package az.tapstamp.security.password;

import static org.assertj.core.api.Assertions.assertThat;

import az.tapstamp.security.password.PasswordPolicy.Violation;
import org.junit.jupiter.api.Test;

/** Şifrə siyasəti (NIST SP 800-63B): uzunluq, məşhur şifrələr, email ilə oxşarlıq; mürəkkəblik qaydası yoxdur. */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void acceptsLongPassphrasesWithoutCompositionRules() {
        assertThat(policy.check("qəhvə dəmlənir səhər tezdən", "owner@mockcoffee.az")).isEmpty();
        assertThat(policy.check("Kofe-Dev-Parol-2026", "owner@mockcoffee.az")).isEmpty();
        assertThat(policy.check("all lowercase words here", null)).isEmpty();
    }

    @Test
    void rejectsShortAndOverlongPasswords() {
        assertThat(policy.check("Qisa-123", null)).containsExactly(Violation.TOO_SHORT);
        assertThat(policy.check("a".repeat(64) + "b".repeat(65), null)).contains(Violation.TOO_LONG);
        assertThat(policy.check(null, null)).containsExactly(Violation.TOO_SHORT);
    }

    @Test
    void lengthCountsCharactersNotBytes() {
        // 12 Azərbaycan hərfi: UTF-8-də 12-dən çox bayt, amma 12 simvol
        assertThat(policy.check("şəğıöüçşəğıö", null)).doesNotContain(Violation.TOO_SHORT);
    }

    @Test
    void rejectsCommonPasswordsCaseInsensitively() {
        assertThat(policy.check("Password1234", null)).contains(Violation.COMMON_PASSWORD);
        assertThat(policy.check("QWERTYUIOP123", null)).contains(Violation.COMMON_PASSWORD);
    }

    @Test
    void rejectsPasswordsContainingTheEmailName() {
        assertThat(policy.check("manager-secure-2026", "Manager@mockcoffee.az")).contains(Violation.CONTAINS_EMAIL);
    }

    @Test
    void rejectsRepetitivePasswords() {
        assertThat(policy.check("aaaaaaaaaaaaaaaa", null)).contains(Violation.REPETITIVE);
        assertThat(policy.check("abababababababab", null)).contains(Violation.REPETITIVE);
    }

    @Test
    void argon2idHashesAreSaltedAndVerifiable() {
        String hash = hasher.hash("Kofe-Dev-Parol-2026");
        assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(hasher.hash("Kofe-Dev-Parol-2026")).isNotEqualTo(hash);
        assertThat(hasher.matches("Kofe-Dev-Parol-2026", hash)).isTrue();
        assertThat(hasher.matches("kofe-dev-parol-2026", hash)).isFalse();
        assertThat(hasher.matches("Kofe-Dev-Parol-2026", null)).isFalse();
        assertThat(hasher.needsRehash(hash)).isFalse();
    }
}
