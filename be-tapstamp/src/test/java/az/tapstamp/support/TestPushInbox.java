package az.tapstamp.support;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import az.tapstamp.notification.push.PushTransport;
import az.tapstamp.notification.push.WebPushCrypto;

/**
 * Testlərdə push serveri: göndərilən mesajları yazır (real push xidmətinə sorğu getmir); "brauzer" kimi abunə açarları
 * yaradır və mesajı deşifrə edir. Ünvan üçün cavab kodu təyin oluna bilər (məs. 410 — abunə etibarsızdır).
 */
public class TestPushInbox implements PushTransport {

    public record Delivery(URI endpoint, Map<String, String> headers, byte[] body) {
    }

    /** Test "brauzeri": abunənin açarları və şəxsi açar (deşifrə üçün). */
    public record Browser(String endpoint, KeyPair keys, byte[] auth) {
        public String p256dh() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(WebPushCrypto.encodePoint((ECPublicKey) keys.getPublic()));
        }

        public String authKey() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(auth);
        }
    }

    private final List<Delivery> deliveries = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();

    @Override
    public int post(URI endpoint, Map<String, String> headers, byte[] body) {
        deliveries.add(new Delivery(endpoint, headers, body));
        return statuses.getOrDefault(endpoint.toString(), 201);
    }

    public void respond(String endpoint, int status) {
        statuses.put(endpoint, status);
    }

    public Browser newBrowser() throws Exception {
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        return new Browser("https://fcm.googleapis.com/fcm/send/" + java.util.UUID.randomUUID(),
                WebPushCrypto.generateKeyPair(), auth);
    }

    public List<Delivery> to(String endpoint) {
        List<Delivery> result = new ArrayList<>();
        for (Delivery delivery : deliveries) {
            if (delivery.endpoint().toString().equals(endpoint)) {
                result.add(delivery);
            }
        }
        return result;
    }

    /** Bildirişlər commit-dən sonra arxa planda gedir — gözləyirik. */
    public Delivery await(String endpoint, int count) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            List<Delivery> found = to(endpoint);
            if (found.size() >= count) {
                return found.get(count - 1);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Push was not delivered to " + endpoint);
    }

    /** Brauzer tərəfi (RFC 8291): mesajın JSON mətni. */
    public static String decrypt(Browser browser, byte[] body) throws Exception {
        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        byte[] asPublic = Arrays.copyOfRange(body, 21, 21 + body[20]);
        byte[] uaPublic = WebPushCrypto.encodePoint((ECPublicKey) browser.keys().getPublic());
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.keys().getPrivate());
        agreement.doPhase(WebPushCrypto.decodePoint(asPublic), true);
        byte[] info = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[] {0}, uaPublic, asPublic);
        byte[] ikm = hkdf(browser.auth(), agreement.generateSecret(), info, 32);
        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = cipher.doFinal(Arrays.copyOfRange(body, 21 + body[20], body.length));
        return new String(plain, 0, plain.length - 1, StandardCharsets.UTF_8);
    }

    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), length);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }
}
