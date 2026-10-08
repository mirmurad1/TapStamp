package az.cafeloyalty.security.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-GCM (həm şifrələyən, həm də dəyişikliyi aşkarlayan rejim). Çıxış: nonce(12) ‖ şifrəli mətn ‖ teq(16).
 * AAD (əlavə autentifikasiya olunan məlumat) şifrələnmir, amma imzaya daxildir: fərqli kontekstdə açmaq olmur.
 */
public final class AesGcm {

    public static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AesGcm() {
    }

    public static byte[] encrypt(SecretKey key, byte[] plaintext, String aad) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(NONCE_LENGTH + ciphertext.length).put(nonce).put(ciphertext).array();
        } catch (GeneralSecurityException e) {
            throw new CryptoException("AES-GCM encryption failed", e);
        }
    }

    public static byte[] decrypt(SecretKey key, byte[] nonceAndCiphertext, String aad) {
        if (nonceAndCiphertext.length < NONCE_LENGTH + TAG_BITS / 8) {
            throw new CryptoException("Ciphertext too short");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonceAndCiphertext, 0, NONCE_LENGTH));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(nonceAndCiphertext, NONCE_LENGTH, nonceAndCiphertext.length - NONCE_LENGTH);
        } catch (GeneralSecurityException e) {
            // Səhv açar, dəyişdirilmiş məlumat və ya fərqli AAD — hamısı eyni xəta (oracle verilmir)
            throw new CryptoException("AES-GCM decryption failed", e);
        }
    }
}
