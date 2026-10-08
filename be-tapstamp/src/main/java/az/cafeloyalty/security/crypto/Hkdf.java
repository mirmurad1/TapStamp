package az.cafeloyalty.security.crypto;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HKDF-SHA256 (RFC 5869): bir master açardan kontekstə görə fərqli, bir-birindən asılı olmayan açarlar törədir.
 * Məsələn, hər tenant üçün ayrıca telefon HMAC açarı.
 */
public final class Hkdf {

    private static final String HMAC = "HmacSHA256";
    private static final int HASH_LENGTH = 32;

    private Hkdf() {
    }

    public static byte[] derive(byte[] inputKeyMaterial, byte[] salt, byte[] info, int length) {
        if (length <= 0 || length > 255 * HASH_LENGTH) {
            throw new IllegalArgumentException("Invalid HKDF output length");
        }
        byte[] prk = hmac(salt.length == 0 ? new byte[HASH_LENGTH] : salt, inputKeyMaterial);
        ByteArrayOutputStream okm = new ByteArrayOutputStream(length);
        byte[] previous = new byte[0];
        for (int counter = 1; okm.size() < length; counter++) {
            byte[] block = new byte[previous.length + info.length + 1];
            System.arraycopy(previous, 0, block, 0, previous.length);
            System.arraycopy(info, 0, block, previous.length, info.length);
            block[block.length - 1] = (byte) counter;
            previous = hmac(prk, block);
            okm.write(previous, 0, Math.min(previous.length, length - okm.size()));
        }
        return okm.toByteArray();
    }

    /** HMAC-SHA256. */
    public static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("HMAC failed", e);
        }
    }
}
