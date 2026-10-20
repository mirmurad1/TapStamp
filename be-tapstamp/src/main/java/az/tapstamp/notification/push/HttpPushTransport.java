package az.tapstamp.notification.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

/** JDK HTTP klienti ilə; yönləndirmələrə əməl edilmir (ünvan yalnız icazəli push serverinə gedir). */
@Component
public class HttpPushTransport implements PushTransport {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    // İlk göndərişdə yaranır: klient selector thread-i açır, push istifadə olunmayanda resurs tutmasın
    private volatile HttpClient client;

    @Override
    public int post(URI endpoint, Map<String, String> headers, byte[] body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(request::header);
        return client().send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private HttpClient client() {
        HttpClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) {
                    client = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER)
                            .build();
                }
                current = client;
            }
        }
        return current;
    }
}
