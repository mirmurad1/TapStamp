package az.tapstamp.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

/** Web Push şifrələməsi: RFC 8291 Əlavə A-dakı rəsmi nümunə və brauzer tərəfinin deşifrəsi ilə yoxlama. */
class WebPushCryptoTest {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();
    private static final Base64.Encoder B64E = Base64.getUrlEncoder().withoutPadding();

    @Test
    void matchesTheRfc8291Example() throws Exception {
        byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
        ECPrivateKey asPrivate = WebPushCrypto.decodePrivate(B64.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"));
        ECPublicKey asPublic = WebPushCrypto.decodePoint(B64.decode(
                "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"));
        byte[] uaPublic = B64.decode(
                "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] auth = B64.decode("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] salt = B64.decode("DGv6ra1nlYgDCS1FRnbzlw");

        byte[] body = WebPushCrypto.encrypt(plaintext, uaPublic, auth, new KeyPair(asPublic, asPrivate), salt);

        assertThat(B64E.encodeToString(body)).isEqualTo(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    void browserCanDecryptWithItsPrivateKey() throws Exception {
        KeyPair browser = WebPushCrypto.generateKeyPair();
        byte[] uaPublic = WebPushCrypto.encodePoint((ECPublicKey) browser.getPublic());
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        byte[] plaintext = "{\"title\":\"Hədiyyən hazırdır!\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushCrypto.encrypt(plaintext, uaPublic, auth);

        // Brauzer tərəfi (RFC 8291 §3): başlıqdan salt və server açarı, ECDH, HKDF, AES-GCM
        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        assertThat(body[20]).isEqualTo((byte) 65);
        byte[] asPublic = Arrays.copyOfRange(body, 21, 86);
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(WebPushCrypto.decodePoint(asPublic), true);
        byte[] keyInfo = WebPushCrypto.concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[] {0},
                uaPublic, asPublic);
        byte[] ikm = WebPushCrypto.hkdf(auth, agreement.generateSecret(), keyInfo, 32);
        byte[] cek = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] decrypted = cipher.doFinal(Arrays.copyOfRange(body, 86, body.length));

        assertThat(decrypted[decrypted.length - 1]).isEqualTo((byte) 2);
        assertThat(Arrays.copyOf(decrypted, decrypted.length - 1)).isEqualTo(plaintext);
    }
}
