package az.cafeloyalty.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Base64;
import java.util.List;

import az.cafeloyalty.CafeLoyaltyApplication;
import org.junit.jupiter.api.Test;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;

class ProdSafetyGuardTest {

    private static final String STRONG_SECRET = Base64.getEncoder().encodeToString(new byte[] {
            12, 87, 3, 44, 120, 9, 66, 18, 91, 7, 54, 33, 101, 2, 77, 15,
            42, 8, 63, 29, 110, 5, 81, 36, 97, 11, 58, 24, 70, 1, 88, 49});

    private static MockEnvironment safeProdEnvironment() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("app.notifications.sms-provider", "http");
        env.setProperty("app.notifications.mail-provider", "smtp");
        env.setProperty("app.notifications.mail-from", "no-reply@cafe.az");
        env.setProperty("app.crypto.keks", "kek-2026-10:" + STRONG_SECRET);
        env.setProperty("app.crypto.phone-hmac-key", STRONG_SECRET);
        env.setProperty("app.crypto.otp-pepper", STRONG_SECRET);
        env.setProperty("app.crypto.redemption-code-key", STRONG_SECRET);
        env.setProperty("app.tenancy.platform.app-password", "Zq7xP2mL9vR4tY8wK3nB6cH1");
        env.setProperty("app.tenancy.platform.owner-password", "Jm5sD8fG2hK7lQ4wE9rT1yU6");
        return env;
    }

    @Test
    void safeConfigurationPasses() {
        assertThat(ProdSafetyGuard.checkEnvironment(safeProdEnvironment())).isEmpty();
    }

    @Test
    void devProfileTogetherWithProdIsRejected() {
        MockEnvironment env = safeProdEnvironment();
        env.setActiveProfiles("prod", "dev");
        assertThat(ProdSafetyGuard.checkEnvironment(env)).anySatisfy(v -> assertThat(v).contains("'dev'"));
    }

    @Test
    void smtpWithoutSenderAddressIsRejected() {
        MockEnvironment env = safeProdEnvironment();
        env.setProperty("app.notifications.mail-from", " ");
        assertThat(ProdSafetyGuard.checkEnvironment(env)).anySatisfy(v -> assertThat(v).contains("mail-from"));
    }

    @Test
    void consoleProvidersAndDevFeaturesAreRejected() {
        MockEnvironment env = safeProdEnvironment();
        env.setProperty("app.notifications.sms-provider", "Console");
        env.setProperty("app.dev.simulator-enabled", "true");
        env.setProperty("springdoc.swagger-ui.enabled", "true");
        env.setProperty("app.security.cookie-secure", "false");
        List<String> violations = ProdSafetyGuard.checkEnvironment(env);
        assertThat(violations).hasSize(4);
    }

    @Test
    void missingOrPlaceholderSecretsAreRejectedWithoutLeakingValues() {
        MockEnvironment env = safeProdEnvironment();
        env.setProperty("app.crypto.phone-hmac-key", "");
        env.setProperty("app.crypto.otp-pepper", "CHANGE_ME_super_secret_value");
        List<String> violations = ProdSafetyGuard.checkEnvironment(env);
        assertThat(violations).hasSize(2);
        assertThat(String.join(" ", violations)).doesNotContain("super_secret_value");
    }

    @Test
    void devPackageBeansAreRejected() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition();
        definition.setBeanClassName("az.cafeloyalty.dev.DevSimulatorVerifier");
        beanFactory.registerBeanDefinition("devSimulatorVerifier", definition);
        assertThat(ProdSafetyGuard.checkBeanDefinitions(beanFactory)).hasSize(1);
    }

    @Test
    void applicationDoesNotStartWithProdAndDevProfiles() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(CafeLoyaltyApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("prod", "dev")
                .properties(
                        // Gözlənilən başlanğıc xətası test çıxışını böyük stack trace ilə doldurmasın
                        "logging.level.org.springframework.boot.SpringApplication=OFF",
                        "app.crypto.keks=kek-2026-10:" + STRONG_SECRET,
                        "app.crypto.phone-hmac-key=" + STRONG_SECRET,
                        "app.crypto.otp-pepper=" + STRONG_SECRET);
        Throwable thrown = catchThrowable(builder::run);
        assertThat(thrown).as("tətbiq başlamamalı idi").isNotNull();
        assertThat(NestedExceptionUtils.getMostSpecificCause(thrown))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'dev' profili");
    }
}
