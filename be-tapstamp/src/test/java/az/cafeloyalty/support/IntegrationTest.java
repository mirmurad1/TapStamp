package az.cafeloyalty.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * İnteqrasiya testlərinin bazası: real PostgreSQL (Testcontainers), iki provisioning olunmuş şirkət
 * (alpha.test, beta.test) və MockMvc. Docker yoxdursa testlər uğursuz sayılmır, atlanır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestApiController.class, IntegrationTestConfig.class})
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTest {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
        // Bütün test sinifləri eyni şirkətdə çoxlu OTP göndərir: şirkət və qlobal limitlər testlər arasında paylaşılmasın
        registry.add("app.rate-limits.otp-request-per-tenant", () -> 100_000);
        registry.add("app.rate-limits.otp-request-global", () -> 100_000);
        // Fon işləri (token və möhür müddətləri) testlərdə birbaşa çağırılır — paralel işləyib nəticəni pozmasın
        registry.add("app.jobs.enabled", () -> false);
        registry.add("app.internal-token", () -> TestTenants.INTERNAL_TOKEN);
    }
}
