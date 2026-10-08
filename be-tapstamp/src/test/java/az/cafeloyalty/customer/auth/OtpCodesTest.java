package az.cafeloyalty.customer.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import az.cafeloyalty.security.crypto.CryptoProperties;
import org.junit.jupiter.api.Test;

/** OTP kodları: 6 rəqəm, bazada yalnız HMAC (pepper + challenge ID), sabit zamanlı yoxlama. */
class OtpCodesTest {

    private static final String PEPPER = Base64.getEncoder().encodeToString(
            "pepper-for-unit-tests-32-bytes!!".getBytes(StandardCharsets.US_ASCII));

    private final OtpCodes codes = new OtpCodes(new CryptoProperties(null, null, null, PEPPER, null));

    @Test
    void generatesSixDigitCodesWithLeadingZeros() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            String code = codes.generate();
            assertThat(code).matches("^\\d{6}$");
            seen.add(code);
        }
        assertThat(seen).hasSizeGreaterThan(1_900);
    }

    @Test
    void hashDependsOnChallengeAndDoesNotContainTheCode() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        byte[] hash = codes.hash(first, "123456");
        assertThat(hash).hasSize(32);
        assertThat(hash).isNotEqualTo(codes.hash(second, "123456"));
        assertThat(new String(hash, StandardCharsets.ISO_8859_1)).doesNotContain("123456");
        assertThat(codes.matches(first, "123456", hash)).isTrue();
        assertThat(codes.matches(second, "123456", hash)).isFalse();
        assertThat(codes.matches(first, "123457", hash)).isFalse();
    }

    @Test
    void rejectsMalformedInput() {
        byte[] hash = codes.hash(UUID.randomUUID(), "000000");
        assertThat(codes.matches(UUID.randomUUID(), null, hash)).isFalse();
        assertThat(codes.matches(UUID.randomUUID(), "12345", hash)).isFalse();
        assertThat(codes.matches(UUID.randomUUID(), "１２３４５６", hash)).isFalse();
    }

    @Test
    void requiresAStrongPepper() {
        assertThatThrownBy(() -> new OtpCodes(new CryptoProperties(null, null, null, "", null)))
                .isInstanceOf(IllegalStateException.class);
        String weak = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new OtpCodes(new CryptoProperties(null, null, null, weak, null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
