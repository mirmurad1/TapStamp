package az.tapstamp.notification.sms;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import az.tapstamp.notification.sms.SmsDeliveryException.Kind;
import az.tapstamp.shared.pii.PhoneMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Poçt Göyərçini (Posta Güvercini, Figensoft) SMS API v1.0.4 adapteri: {@code POST /gateway/api/sms/v1/message/send}.
 *
 * <ul>
 *   <li>PublicKey ünvanda ({@code ?publicKey=}), PrivateKey {@code Authorization: Bearer} başlığında;</li>
 *   <li>nömrə beynəlxalq formatda, {@code +} işarəsi olmadan ({@code 994501234567});</li>
 *   <li>uğur yalnız cavabdakı {@code Status = 200} və alıcının {@code ReceiversAccepted}-də olmasıdır — HTTP 200
 *       qayıtsa belə, sorğu rədd edilmiş ola bilər;</li>
 *   <li>şəbəkə xətası və ya 5xx-də bir təkrar cəhd; provayderin rəddi (4xx, {@code Status ≠ 200}, rədd edilmiş alıcı)
 *       təkrarlanmır — eyni SMS iki dəfə getməsin;</li>
 *   <li>circuit breaker: ardıcıl 5 uğursuzluqdan sonra 60 saniyə provayderə sorğu göndərilmir.</li>
 * </ul>
 * Mətn (OTP kodu), açarlar və tam nömrə heç vaxt loga yazılmır.
 */
public class PostaGuverciniSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(PostaGuverciniSmsProvider.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SEND_PATH = "/gateway/api/sms/v1/message/send";
    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(60);

    private final URI endpoint;
    private final String privateKey;
    private final String originator;
    private final Duration timeout;
    private final Transport transport;
    private final Clock clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> openUntil = new AtomicReference<>(Instant.EPOCH);

    /**
     * @param baseUrl    API ünvanı, məs. {@code https://api.example.com} (yol adapterdə əlavə olunur)
     * @param originator mesaj başlığı (alfa-ad); provayderdə qeydiyyatdan keçmiş olmalıdır, boşdursa hesabın standart
     *                   başlığı istifadə olunur
     */
    public PostaGuverciniSmsProvider(String baseUrl, String publicKey, String privateKey, String originator,
                                     Duration timeout, Clock clock) {
        this(baseUrl, publicKey, privateKey, originator, timeout, clock, httpTransport(timeout));
    }

    /** HTTP göndərişi əvəz edilə bilər (testlər şəbəkəsiz işləyir). */
    PostaGuverciniSmsProvider(String baseUrl, String publicKey, String privateKey, String originator,
                              Duration timeout, Clock clock, Transport transport) {
        String base = baseUrl.strip().replaceAll("/+$", "");
        this.endpoint = URI.create(base + SEND_PATH + "?publicKey="
                + URLEncoder.encode(publicKey.strip(), StandardCharsets.UTF_8));
        this.privateKey = privateKey.strip();
        this.originator = originator == null || originator.isBlank() ? null : originator.strip();
        this.timeout = timeout;
        this.clock = clock;
        this.transport = transport;
    }

    /** HTTP cavabının bizə lazım olan hissəsi. */
    record Response(int status, String body) {
    }

    @FunctionalInterface
    interface Transport {
        Response send(HttpRequest request) throws IOException, InterruptedException;
    }

    private static Transport httpTransport(Duration timeout) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        return request -> {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        };
    }

    @Override
    public void send(SmsMessage message) {
        if (clock.instant().isBefore(openUntil.get())) {
            throw new SmsDeliveryException(Kind.PROVIDER_DOWN, "SMS provider circuit is open");
        }
        try {
            deliver(message);
            consecutiveFailures.set(0);
        } catch (SmsDeliveryException first) {
            if (first.kind() != Kind.TRANSIENT) {
                throw first;
            }
            try {
                deliver(message);
                consecutiveFailures.set(0);
            } catch (SmsDeliveryException second) {
                if (second.kind() != Kind.TRANSIENT) {
                    throw second;
                }
                if (consecutiveFailures.incrementAndGet() >= FAILURE_THRESHOLD) {
                    openUntil.set(clock.instant().plus(OPEN_DURATION));
                    log.warn("SMS provayderi əlçatmazdır: {} saniyə sorğu göndərilməyəcək", OPEN_DURATION.toSeconds());
                }
                throw second;
            }
        }
    }

    /** Göndəriləcək JSON (testlər üçün paket səviyyəsində). */
    String body(SmsMessage message) {
        // LATIN yalnız ASCII-ni olduğu kimi saxlayır (ə, ş, ü ... latına çevrilir) — belə mətn UNICODE ilə gedir
        // (provayderdə hesab üçün UNICODE icazəsi açılmalıdır)
        String encoding = SmsSegments.analyze(message.text()).encoding() == SmsSegments.Encoding.GSM_7 ? "LATIN" : "UNICODE";
        Map<String, Object> options = originator == null ? Map.of("Encoding", encoding)
                : Map.of("Originator", originator, "Encoding", encoding);
        return JSON.writeValueAsString(Map.of(
                "Text", message.text(),
                // Provayderlə razılaşdırılıb: hesabımızda giriş kodları da INF (məlumat mesajı) növü ilə gedir
                "Purpose", "INF",
                "Options", options,
                "Receivers", List.of(Map.of("Receiver", receiver(message.to())))));
    }

    /** {@code +994 50 123 45 67} → {@code 994501234567}. */
    static String receiver(String phone) {
        return phone.replaceAll("\\D", "");
    }

    private void deliver(SmsMessage message) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + privateKey)
                .POST(HttpRequest.BodyPublishers.ofString(body(message)))
                .build();
        Response response;
        try {
            response = transport.send(request);
        } catch (IOException e) {
            throw HttpSmsProvider.networkFailure(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SmsDeliveryException(Kind.UNKNOWN, "SMS sending was interrupted", e);
        }
        if (response.status() >= 500) {
            throw new SmsDeliveryException(Kind.TRANSIENT, "SMS provider returned HTTP " + response.status());
        }
        String masked = PhoneMasker.mask(message.to());
        if (response.status() >= 400) {
            // Açar, icazə və s. — bütün göndərişə aiddir
            log.warn("SMS rədd edildi: http={}, to={}", response.status(), masked);
            throw new SmsDeliveryException(Kind.ACCOUNT, "SMS provider rejected the request: HTTP " + response.status());
        }
        JsonNode root;
        try {
            root = JSON.readTree(response.body());
        } catch (RuntimeException e) {
            // Uğurlu HTTP cavabı oxunmur — SMS qəbul olunmuş ola bilər
            throw new SmsDeliveryException(Kind.UNKNOWN, "SMS provider returned an unreadable response", e);
        }
        int status = root.path("Status").asInt(-1);
        if (status != 200) {
            // Description provayderin xəta izahıdır (mətn və ya açar daxil deyil)
            log.warn("SMS rədd edildi: status={}, description={}, to={}", status,
                    root.path("Description").asString(""), masked);
            if (status >= 500) {
                throw new SmsDeliveryException(Kind.TRANSIENT, "SMS provider returned status " + status);
            }
            // Sorğu bütövlükdə rədd edildi (icazə, balans, növ) — hesabın problemidir
            throw new SmsDeliveryException(Kind.ACCOUNT, "SMS provider rejected the request: status " + status + " "
                    + root.path("Description").asString(""));
        }
        JsonNode rejected = root.path("Result").path("ReceiversRejected");
        if (rejected.isArray() && !rejected.isEmpty()) {
            JsonNode first = rejected.get(0);
            log.warn("SMS alıcısı rədd edildi: code={}, message={}, to={}", first.path("ErrorCode").asString(""),
                    first.path("ErrorMessage").asString(""), masked);
            throw new SmsDeliveryException(Kind.RECIPIENT, "SMS receiver rejected: " + first.path("ErrorCode").asString(""));
        }
        JsonNode accepted = root.path("Result").path("ReceiversAccepted");
        if (accepted.isArray() && !accepted.isEmpty()) {
            log.info("SMS qəbul edildi: id={}, to={}", accepted.get(0).path("id").asString(""), masked);
        }
    }

}
