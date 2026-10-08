package az.tapstamp.nfc.sun;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

/** NXP AN12196 ("NTAG 424 DNA and NTAG 424 DNA TagTamper features and hints") sənədinin rəsmi test vektorları. */
class Ntag424SunTest {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final byte[] ZERO_KEY = new byte[16];

    // AN12196, "SUN: Encrypted PICCData + SDMMAC": bütün açarlar sıfır
    private static final String PICC = "EF963FF7828658A599F3041510671E88";
    private static final String CMAC = "94EED9EE65337086";
    private static final String UID = "04DE5F1EACC040";
    private static final int COUNTER = 0x3D;

    @Test
    void decryptsPiccDataFromAn12196() {
        Ntag424Sun.PiccData data = Ntag424Sun.decryptPicc(ZERO_KEY, HEX.parseHex(PICC));

        assertThat(data).isNotNull();
        assertThat(HEX.formatHex(data.uid())).isEqualTo(UID);
        assertThat(data.counter()).isEqualTo(61);
    }

    @Test
    void derivesSessionKeyAndMacFromAn12196() {
        byte[] uid = HEX.parseHex(UID);

        assertThat(HEX.formatHex(Ntag424Sun.sessionMacKey(ZERO_KEY, uid, COUNTER)))
                .isEqualTo("3FB5F6E3A807A03D5E3570ACE393776F");
        assertThat(HEX.formatHex(Ntag424Sun.sdmMac(ZERO_KEY, uid, COUNTER))).isEqualTo(CMAC);
        assertThat(Ntag424Sun.verifyMac(ZERO_KEY, uid, COUNTER, HEX.parseHex(CMAC))).isTrue();
    }

    @Test
    void rejectsWrongKeyTamperedMacAndOtherCounter() {
        byte[] uid = HEX.parseHex(UID);
        byte[] otherKey = HEX.parseHex("000102030405060708090A0B0C0D0E0F");
        byte[] tampered = HEX.parseHex(CMAC);
        tampered[7] ^= 0x01;

        // Yanlış K1 ilə deşifrədə PICCDataTag (0xC7) uyğun gəlmir
        assertThat(Ntag424Sun.decryptPicc(otherKey, HEX.parseHex(PICC))).isNull();
        assertThat(Ntag424Sun.verifyMac(otherKey, uid, COUNTER, HEX.parseHex(CMAC))).isFalse();
        assertThat(Ntag424Sun.verifyMac(ZERO_KEY, uid, COUNTER, tampered)).isFalse();
        assertThat(Ntag424Sun.verifyMac(ZERO_KEY, uid, COUNTER + 1, HEX.parseHex(CMAC))).isFalse();
        assertThat(Ntag424Sun.decryptPicc(ZERO_KEY, new byte[15])).isNull();
    }

    @Test
    void emulationRoundTripsWithVerification() {
        byte[] k1 = HEX.parseHex("A0A1A2A3A4A5A6A7A8A9AAABACADAEAF");
        byte[] k2 = HEX.parseHex("B0B1B2B3B4B5B6B7B8B9BABBBCBDBEBF");
        byte[] uid = HEX.parseHex("04A1B2C3D4E5F2");

        byte[] picc = Ntag424Sun.encryptPicc(k1, uid, 0xABCDEF, new byte[5]);
        Ntag424Sun.PiccData data = Ntag424Sun.decryptPicc(k1, picc);

        assertThat(data.uid()).isEqualTo(uid);
        assertThat(data.counter()).isEqualTo(0xABCDEF);
        assertThat(Ntag424Sun.verifyMac(k2, uid, 0xABCDEF, Ntag424Sun.sdmMac(k2, uid, 0xABCDEF))).isTrue();
        // AN12196 vektoru da eyni emulyasiya ilə alınır (doldurma baytları sənəddəki kimi)
        byte[] an12196Padding = Arrays.copyOfRange(decryptRaw(HEX.parseHex(PICC)), 11, 16);
        assertThat(HEX.formatHex(Ntag424Sun.encryptPicc(ZERO_KEY, HEX.parseHex(UID), COUNTER, an12196Padding)))
                .isEqualTo(PICC);
    }

    private static byte[] decryptRaw(byte[] picc) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(ZERO_KEY, "AES"), new IvParameterSpec(new byte[16]));
            return cipher.doFinal(picc);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
