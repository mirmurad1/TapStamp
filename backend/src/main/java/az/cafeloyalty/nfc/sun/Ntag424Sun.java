package az.cafeloyalty.nfc.sun;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * NTAG 424 DNA SUN (Secure Unique NFC) kriptoqrafiyası — NXP AN12196 sənədinə uyğun.
 *
 * <ul>
 *   <li>PICCData = AES-128-CBC(K1 = SDMMetaReadKey, IV = 0): {@code C7 ‖ UID(7) ‖ SDMReadCtr(3, LSB first) ‖ doldurma(5)};</li>
 *   <li>SV2 = {@code 3C C3 00 01 00 80 ‖ UID ‖ SDMReadCtr}, K<sub>ses</sub> = AES-CMAC(K2 = SDMFileReadKey, SV2);</li>
 *   <li>MAC = AES-CMAC(K<sub>ses</sub>, ∅), URL-dəki {@code cmac} isə onun 1, 3, 5, …, 15 indeksli baytlarıdır (8 bayt).</li>
 * </ul>
 * Sinif vəziyyətsizdir və DB-yə müraciət etmir. Simulyator da çipi bu metodlarla emulyasiya edir.
 */
public final class Ntag424Sun {

    /** PICCDataTag: UID və sayğac əks olunub, UID uzunluğu 7 bayt. */
    public static final byte PICC_DATA_TAG = (byte) 0xC7;
    public static final int UID_LENGTH = 7;
    public static final int MAX_COUNTER = 0xFFFFFF;
    private static final byte[] SV2_PREFIX = {0x3C, (byte) 0xC3, 0x00, 0x01, 0x00, (byte) 0x80};
    private static final byte[] ZERO_IV = new byte[16];

    /** Deşifrə olunmuş PICCData: çipin həqiqi UID-i və oxunma sayğacı. */
    public record PiccData(byte[] uid, int counter) {
    }

    private Ntag424Sun() {
    }

    /** PICCData-nı deşifrə edir; format səhvdirsə (açar yanlışdır və ya məlumat saxtadır) {@code null} qaytarır. */
    public static PiccData decryptPicc(byte[] metaReadKey, byte[] encryptedPicc) {
        if (encryptedPicc == null || encryptedPicc.length != 16) {
            return null;
        }
        byte[] plain = aesCbc(Cipher.DECRYPT_MODE, metaReadKey, encryptedPicc);
        try {
            if (plain[0] != PICC_DATA_TAG) {
                return null;
            }
            byte[] uid = Arrays.copyOfRange(plain, 1, 1 + UID_LENGTH);
            int counter = (plain[8] & 0xFF) | (plain[9] & 0xFF) << 8 | (plain[10] & 0xFF) << 16;
            return new PiccData(uid, counter);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    /** Çipin etdiyini təkrarlayır: PICCData-nı şifrələyir (simulyator və testlər üçün). {@code padding} 5 baytdır. */
    public static byte[] encryptPicc(byte[] metaReadKey, byte[] uid, int counter, byte[] padding) {
        requireUid(uid);
        requireCounter(counter);
        if (padding.length != 5) {
            throw new IllegalArgumentException("padding must be 5 bytes");
        }
        byte[] plain = new byte[16];
        plain[0] = PICC_DATA_TAG;
        System.arraycopy(uid, 0, plain, 1, UID_LENGTH);
        System.arraycopy(counterBytes(counter), 0, plain, 8, 3);
        System.arraycopy(padding, 0, plain, 11, 5);
        return aesCbc(Cipher.ENCRYPT_MODE, metaReadKey, plain);
    }

    /** SDMMAC üçün sessiya açarı: K<sub>ses</sub> = AES-CMAC(K2, SV2). */
    public static byte[] sessionMacKey(byte[] fileReadKey, byte[] uid, int counter) {
        requireUid(uid);
        requireCounter(counter);
        byte[] sv2 = new byte[SV2_PREFIX.length + UID_LENGTH + 3];
        System.arraycopy(SV2_PREFIX, 0, sv2, 0, SV2_PREFIX.length);
        System.arraycopy(uid, 0, sv2, SV2_PREFIX.length, UID_LENGTH);
        System.arraycopy(counterBytes(counter), 0, sv2, SV2_PREFIX.length + UID_LENGTH, 3);
        return cmac(fileReadKey, sv2);
    }

    /** URL-dəki 8 baytlıq {@code cmac}: AES-CMAC(K<sub>ses</sub>, ∅)-ın tək indeksli baytları. */
    public static byte[] sdmMac(byte[] fileReadKey, byte[] uid, int counter) {
        byte[] sessionKey = sessionMacKey(fileReadKey, uid, counter);
        try {
            byte[] full = cmac(sessionKey, new byte[0]);
            byte[] truncated = new byte[8];
            for (int i = 0; i < 8; i++) {
                truncated[i] = full[2 * i + 1];
            }
            return truncated;
        } finally {
            Arrays.fill(sessionKey, (byte) 0);
        }
    }

    /** Gələn {@code cmac}-ı sabit zamanlı müqayisə edir — cavab müddətindən doğru baytları təxmin etmək olmur. */
    public static boolean verifyMac(byte[] fileReadKey, byte[] uid, int counter, byte[] receivedMac) {
        return receivedMac != null && MessageDigest.isEqual(sdmMac(fileReadKey, uid, counter), receivedMac);
    }

    static byte[] cmac(byte[] key, byte[] message) {
        CMac mac = new CMac(AESEngine.newInstance());
        mac.init(new KeyParameter(key));
        mac.update(message, 0, message.length);
        byte[] out = new byte[mac.getMacSize()];
        mac.doFinal(out, 0);
        return out;
    }

    private static byte[] aesCbc(int mode, byte[] key, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(ZERO_IV));
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-CBC failed", e);
        }
    }

    private static byte[] counterBytes(int counter) {
        return new byte[] {(byte) counter, (byte) (counter >>> 8), (byte) (counter >>> 16)};
    }

    private static void requireUid(byte[] uid) {
        if (uid == null || uid.length != UID_LENGTH) {
            throw new IllegalArgumentException("UID must be 7 bytes");
        }
    }

    private static void requireCounter(int counter) {
        if (counter < 0 || counter > MAX_COUNTER) {
            throw new IllegalArgumentException("counter must be 24-bit");
        }
    }
}
