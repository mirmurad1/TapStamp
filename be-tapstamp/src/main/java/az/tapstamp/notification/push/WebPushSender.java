package az.tapstamp.notification.push;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Web Push göndərişi: mesaj RFC 8291 ilə şifrələnir, göndərən VAPID (RFC 8292, ES256 JWT) ilə imzalanır. Açarlar
 * {@code app.push.vapid-*} ({@code .env}); təyin olunmayıbsa push söndürülür — dev və testlərdə
 * ({@code app.push.temporary-keys}) isə başlanğıcda müvəqqəti açar yaranır. Ünvan yalnız tanınmış push serverlərinə ola bilər (SSRF qorunması: müştəri istənilən URL
 * göndərib serveri ora sorğu atmağa məcbur edə bilməz).
 */
@Component
public class WebPushSender {

    /** Göndərişin nəticəsi: GONE — abunə etibarsızdır (silinməlidir). */
    public enum Result { SENT, GONE, FAILED }

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final Duration JWT_TTL = Duration.ofHours(12);
    /** Push xidmətləri: Google (Chrome, Android), Mozilla, Apple, Microsoft. */
    private static final List<String> ALLOWED_HOSTS = List.of("fcm.googleapis.com", "updates.push.services.mozilla.com",
            "push.services.mozilla.com", "web.push.apple.com");
    private static final List<String> ALLOWED_SUFFIXES = List.of(".push.apple.com", ".notify.windows.com");

    private final PushTransport transport;
    private final Clock clock;
    private final @Nullable KeyPair vapid;
    private final String subject;

    public WebPushSender(PushTransport transport, Clock clock,
                         @Value("${app.push.vapid-public-key:}") String publicKey,
                         @Value("${app.push.vapid-private-key:}") String privateKey,
                         @Value("${app.push.subject:}") String subject,
                         @Value("${app.notifications.mail-from:}") String mailFrom,
                         @Value("${app.push.temporary-keys:false}") boolean temporaryKeys) {
        this.transport = transport;
        this.clock = clock;
        this.subject = subject(subject, mailFrom);
        this.vapid = keys(publicKey, privateKey, temporaryKeys);
    }

    /** VAPID "sub": push xidməti problem olanda əlaqə üçün (mailto:); təyin olunmayıbsa email göndərənin ünvanı. */
    private static String subject(String subject, String mailFrom) {
        if (!subject.isBlank()) {
            return subject.strip();
        }
        java.util.regex.Matcher email = java.util.regex.Pattern.compile("[^\\s<>]+@[^\\s<>]+").matcher(mailFrom);
        return email.find() ? "mailto:" + email.group() : "mailto:noreply@localhost";
    }

    private static @Nullable KeyPair keys(String publicKey, String privateKey, boolean generateIfMissing) {
        try {
            if (!publicKey.isBlank() && !privateKey.isBlank()) {
                return new KeyPair(WebPushCrypto.decodePoint(B64D.decode(publicKey.strip())),
                        WebPushCrypto.decodePrivate(B64D.decode(privateKey.strip())));
            }
            if (generateIfMissing) {
                log.info("Web push: VAPID keys are not configured — temporary keys generated (dev/test only)");
                return WebPushCrypto.generateKeyPair();
            }
            log.info("Web push disabled: APP_PUSH_VAPID_PUBLIC_KEY / APP_PUSH_VAPID_PRIVATE_KEY are not set");
            return null;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid VAPID keys (APP_PUSH_VAPID_*)", e);
        }
    }

    public boolean enabled() {
        return vapid != null;
    }

    /** Brauzerin abunəsi üçün açıq açar (base64url, sıxılmamış nöqtə). */
    public @Nullable String publicKey() {
        return vapid == null ? null : B64.encodeToString(WebPushCrypto.encodePoint((ECPublicKey) vapid.getPublic()));
    }

    /** Ünvan icazəli push serverinə aiddirmi (https, standart port). */
    public static boolean allowedEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equals(uri.getScheme()) && uri.getPort() == -1 && uri.getUserInfo() == null
                    && (ALLOWED_HOSTS.contains(host) || ALLOWED_SUFFIXES.stream().anyMatch(host::endsWith));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * @param ttlSeconds push serveri mesajı cihaz oflayn olarsa nə qədər saxlasın
     */
    public Result send(String endpoint, byte[] p256dh, byte[] auth, byte[] payload, int ttlSeconds) {
        if (vapid == null || !allowedEndpoint(endpoint)) {
            return Result.FAILED;
        }
        try {
            URI uri = URI.create(endpoint);
            byte[] body = WebPushCrypto.encrypt(payload, p256dh, auth);
            Map<String, String> headers = Map.of(
                    "TTL", String.valueOf(ttlSeconds),
                    "Content-Encoding", "aes128gcm",
                    "Content-Type", "application/octet-stream",
                    "Urgency", "normal",
                    "Authorization", "vapid t=" + jwt(uri.getScheme() + "://" + uri.getHost()) + ", k=" + publicKey());
            int status = transport.post(uri, headers, body);
            if (status == 404 || status == 410) {
                return Result.GONE;
            }
            if (status >= 200 && status < 300) {
                return Result.SENT;
            }
            log.warn("Web push rejected by {}: HTTP {}", uri.getHost(), status);
            return Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        } catch (Exception e) {
            log.warn("Web push failed: {}", e.getClass().getSimpleName());
            return Result.FAILED;
        }
    }

    private String jwt(String audience) throws GeneralSecurityException {
        String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        long exp = clock.instant().plus(JWT_TTL).getEpochSecond();
        String claims = B64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + exp + ",\"sub\":\"" + subject + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign((ECPrivateKey) vapid.getPrivate());
        signature.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return header + "." + claims + "." + B64.encodeToString(signature.sign());
    }
}
