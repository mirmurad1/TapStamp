package az.tapstamp.notification.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Flow;

import az.tapstamp.notification.sms.HttpSmsProvider.SmsDeliveryException;
import az.tapstamp.notification.sms.PostaGuverciniSmsProvider.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Poçt Göyərçini adapteri: sorğunun formatı və cavabın oxunması (şəbəkəsiz, saxta göndərişlə). */
class PostaGuverciniSmsProviderTest {

    private static final SmsMessage MESSAGE = new SmsMessage("+994501234567", "Kafe OTP: 123456", "t1");
    private static final String ACCEPTED = """
            {"Status":200,"Description":"","Result":{"ReceiversAccepted":[{"id":"1","Receiver":"994501234567"}],
            "ReceiversRejected":[]}}""";

    private final List<HttpRequest> requests = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final Deque<Response> responses = new ArrayDeque<>();

    private PostaGuverciniSmsProvider provider() {
        return new PostaGuverciniSmsProvider("https://sms.provider.test/", "pub-1", "priv-1", "TrendStamp",
                Duration.ofSeconds(5), Clock.systemUTC(), request -> {
                    requests.add(request);
                    bodies.add(body(request));
                    return responses.isEmpty() ? new Response(200, ACCEPTED) : responses.removeFirst();
                });
    }

    @Test
    void sendsDocumentedRequestFormat() {
        provider().send(MESSAGE);

        HttpRequest request = requests.getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri()).hasToString("https://sms.provider.test/gateway/api/sms/v1/message/send?publicKey=pub-1");
        assertThat(request.headers().firstValue("Authorization")).hasValue("Bearer priv-1");
        JsonNode body = JsonMapper.builder().build().readTree(bodies.getFirst());
        assertThat(body.path("Text").asString()).isEqualTo("Kafe OTP: 123456");
        assertThat(body.path("Purpose").asString()).isEqualTo("INF");
        assertThat(body.path("Options").path("Originator").asString()).isEqualTo("TrendStamp");
        assertThat(body.path("Receivers").get(0).path("Receiver").asString()).isEqualTo("994501234567");
    }

    @Test
    void rejectedReceiverOrStatusFailsWithoutRetry() {
        responses.add(new Response(200, """
                {"Status":200,"Result":{"ReceiversAccepted":[],"ReceiversRejected":[{"RowNumber":1,
                "Receiver":"994501234567","ErrorCode":"ERRSMS4102","ErrorMessage":"Receiver must be valid MSISDN"}]}}"""));
        assertThatThrownBy(() -> provider().send(MESSAGE)).isInstanceOf(SmsDeliveryException.class)
                .hasMessageContaining("ERRSMS4102");
        assertThat(requests).hasSize(1);

        requests.clear();
        responses.add(new Response(200, "{\"Status\":403,\"Description\":\"Yetki hatasi\"}"));
        assertThatThrownBy(() -> provider().send(MESSAGE)).isInstanceOf(SmsDeliveryException.class);
        assertThat(requests).hasSize(1);
    }

    @Test
    void serverErrorIsRetriedOnce() {
        responses.add(new Response(503, ""));
        provider().send(MESSAGE);
        assertThat(requests).hasSize(2);
    }

    /** HttpRequest-in gövdəsini oxuyur (BodyPublisher reaktiv axındır). */
    private static String body(HttpRequest request) {
        StringBuilder out = new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                out.append(StandardCharsets.UTF_8.decode(item));
            }

            @Override
            public void onError(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        return out.toString();
    }
}
