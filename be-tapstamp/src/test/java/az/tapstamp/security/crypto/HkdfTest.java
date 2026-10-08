package az.tapstamp.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class HkdfTest {

    private static final HexFormat HEX = HexFormat.of();

    @Test
    void matchesRfc5869TestCase1() {
        byte[] ikm = HEX.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = HEX.parseHex("000102030405060708090a0b0c");
        byte[] info = HEX.parseHex("f0f1f2f3f4f5f6f7f8f9");

        byte[] okm = Hkdf.derive(ikm, salt, info, 42);

        assertThat(HEX.formatHex(okm)).isEqualTo(
                "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865");
    }

    @Test
    void differentInfoGivesIndependentKeys() {
        byte[] ikm = new byte[32];
        assertThat(Hkdf.derive(ikm, new byte[0], "tenant-a".getBytes(), 32))
                .isNotEqualTo(Hkdf.derive(ikm, new byte[0], "tenant-b".getBytes(), 32));
    }
}
