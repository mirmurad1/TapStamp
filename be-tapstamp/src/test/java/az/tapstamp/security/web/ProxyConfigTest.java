package az.tapstamp.security.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Rate limit-lər müştəri IP-sinə əsaslanır. IP yalnız nginx-in {@code X-Forwarded-For} başlığından götürülür; nginx bu
 * başlığı klientdən gələn dəyərə əlavə etmir, öz {@code $remote_addr} dəyəri ilə əvəz edir — klient saxta başlıq
 * göndərsə də, backend real IP-ni görür.
 */
class ProxyConfigTest {

    private static final Path PROXY_SNIPPET = Path.of("..", "infra", "nginx", "snippets", "proxy-backend.conf");

    @Test
    void nginxOverwritesForwardedForWithTheRealClientAddress() throws IOException {
        String config = Files.readString(PROXY_SNIPPET, StandardCharsets.UTF_8);
        assertThat(config).contains("proxy_set_header X-Forwarded-For $remote_addr;");
        assertThat(config).doesNotContain("$proxy_add_x_forwarded_for");
    }
}
