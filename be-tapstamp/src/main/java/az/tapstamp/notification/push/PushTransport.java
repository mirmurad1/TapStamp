package az.tapstamp.notification.push;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/** Şifrələnmiş push mesajının push serverinə HTTP ilə göndərilməsi; HTTP status kodu qaytarılır. Testlərdə əvəz olunur. */
public interface PushTransport {

    int post(URI endpoint, Map<String, String> headers, byte[] body) throws IOException, InterruptedException;
}
