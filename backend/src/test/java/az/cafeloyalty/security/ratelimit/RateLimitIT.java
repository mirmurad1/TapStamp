package az.cafeloyalty.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import az.cafeloyalty.support.AdminClient;
import az.cafeloyalty.support.AdminIntegrationTest;
import az.cafeloyalty.support.CustomerClient;
import az.cafeloyalty.support.TestTenants;
import org.junit.jupiter.api.Test;

/** Rate limiting: IP üzrə giriş limiti, şirkətlər arası ayrılıq, açarlarda şəxsi məlumatın olmaması. */
class RateLimitIT extends AdminIntegrationTest {

    @Test
    void loginAttemptsPerIpAreLimitedWithRetryAfter() throws Exception {
        String ip = AdminClient.uniqueIp();
        for (int i = 0; i < 20; i++) {
            AdminClient.staff(mvc, TestTenants.ALPHA_HOST).fromIp(ip)
                    .login(uniqueEmail("flood"), "Yanlış-şifrə-12345")
                    .andExpect(status().isUnauthorized());
        }
        AdminClient.staff(mvc, TestTenants.ALPHA_HOST).fromIp(ip)
                .login(uniqueEmail("flood"), "Yanlış-şifrə-12345")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
                .andExpect(header().exists("Retry-After"));

        // Hər şirkətin öz limiti var: eyni IP beta-da bloklanmayıb
        AdminClient.staff(mvc, TestTenants.BETA_HOST).fromIp(ip)
                .login(uniqueEmail("flood"), "Yanlış-şifrə-12345")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otpRequestsPerIpAreLimitedEvenForDifferentNumbers() throws Exception {
        String ip = AdminClient.uniqueIp();
        for (int i = 0; i < 20; i++) {
            new CustomerClient(mvc, TestTenants.ALPHA_HOST, ip)
                    .post("/api/v1/auth/otp/request", Map.of("phone", randomPhone(), "language", "az"))
                    .andExpect(status().isAccepted());
        }
        new CustomerClient(mvc, TestTenants.ALPHA_HOST, ip)
                .post("/api/v1/auth/otp/request", Map.of("phone", randomPhone(), "language", "az"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    private static String randomPhone() {
        return "+99470" + String.format(Locale.ROOT, "%07d", ThreadLocalRandom.current().nextInt(10_000_000));
    }

    @Test
    void bucketKeysContainNoEmailsOrIpAddresses() throws Exception {
        String ip = AdminClient.uniqueIp();
        String email = uniqueEmail("private");
        AdminClient.staff(mvc, TestTenants.ALPHA_HOST).fromIp(ip).login(email, "Yanlış-şifrə-12345");
        List<String> keys = platformJdbc.sql("SELECT id FROM rate_limit_buckets").query(String.class).list();
        assertThat(keys).isNotEmpty()
                .noneMatch(key -> key.contains(ip))
                .noneMatch(key -> key.contains("@"))
                .allMatch(key -> key.matches("^[A-Z_]+:(t:[0-9a-f-]{36}|platform|global):[0-9a-f]{32}$"));
    }
}
