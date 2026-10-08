package az.cafeloyalty.notification.sms;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import az.cafeloyalty.shared.pii.PhoneMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real SMS provayderi üçün ümumi HTTP adapteri (JSON POST, Bearer açar). Konkret provayderin API-si fərqli olarsa,
 * yalnız bu sinif dəyişir.
 *
 * <ul>
 *   <li>bağlantı və cavab üçün timeout;</li>
 *   <li>şəbəkə xətası və ya 5xx cavabında bir təkrar cəhd;</li>
 *   <li>circuit breaker: ardıcıl 5 uğursuzluqdan sonra 60 saniyə provayderə sorğu göndərilmir (sıradakı sorğular
 *       gözləmədə ilişib qalmır).</li>
 * </ul>
 * Mətn (OTP kodu) və tam nömrə heç vaxt loga yazılmır.
 */
public class HttpSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpSmsProvider.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(60);

    private final URI endpoint;
    private final String apiKey;
    private final Duration timeout;
    private final HttpClient client;
    private final Clock clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> openUntil = new AtomicReference<>(Instant.EPOCH);

    public HttpSmsProvider(String endpoint, String apiKey, Duration timeout, Clock clock) {
        this.endpoint = URI.create(endpoint);
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.clock = clock;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public void send(SmsMessage message) {
        if (clock.instant().isBefore(openUntil.get())) {
            throw new SmsDeliveryException("SMS provider circuit is open");
        }
        try {
            deliver(message);
            consecutiveFailures.set(0);
        } catch (SmsDeliveryException first) {
            try {
                deliver(message);
                consecutiveFailures.set(0);
            } catch (SmsDeliveryException second) {
                if (consecutiveFailures.incrementAndGet() >= FAILURE_THRESHOLD) {
                    openUntil.set(clock.instant().plus(OPEN_DURATION));
                    log.warn("SMS provayderi əlçatmazdır: {} saniyə sorğu göndərilməyəcək", OPEN_DURATION.toSeconds());
                }
                throw second;
            }
        }
    }

    private void deliver(SmsMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", message.to());
        body.put("text", message.text());
        if (message.sender() != null) {
            body.put("sender", message.sender());
        }
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 500) {
                throw new SmsDeliveryException("SMS provider returned " + response.statusCode());
            }
            if (response.statusCode() >= 400) {
                // 4xx təkrar cəhd ilə düzəlmir (məs. yanlış nömrə) — loga yalnız maskalı nömrə
                log.warn("SMS rədd edildi: status={}, to={}", response.statusCode(), PhoneMasker.mask(message.to()));
                return;
            }
        } catch (IOException e) {
            throw new SmsDeliveryException("SMS provider is unreachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SmsDeliveryException("SMS sending was interrupted", e);
        }
    }

    public static class SmsDeliveryException extends RuntimeException {
        public SmsDeliveryException(String message) {
            super(message);
        }

        public SmsDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
