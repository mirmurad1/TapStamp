package az.tapstamp.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import az.tapstamp.security.ratelimit.RateLimitPolicy;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Prod profilində təhlükəli konfiqurasiyanı başlanğıcda aşkarlayır və tətbiqin başlamasına imkan vermir.
 *
 * <p>{@link BeanFactoryPostProcessor} kimi işləyir: bean tərifləri yüklənəndən sonra, amma heç bir bean
 * (o cümlədən DB bağlantısı) yaradılmamışdan əvvəl. Beləliklə səhv konfiqurasiya heç bir yan təsir yaratmır.
 */
@Component
@Profile("prod")
public class ProdSafetyGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    static final String DEV_PACKAGE_PREFIX = "az.tapstamp.dev.";

    /** Prod-da mütləq təyin olunmalı sirlər. Dəyərlər heç vaxt loga və ya xəta mesajına yazılmır. */
    static final List<String> REQUIRED_SECRETS = List.of(
            "app.crypto.keks",
            "app.crypto.phone-hmac-key",
            "app.crypto.otp-pepper",
            "app.crypto.redemption-code-key",
            "app.tenancy.platform.app-password",
            "app.tenancy.platform.owner-password");

    /** .env.example faylındakı və ya tipik yer tutucu dəyərlərin fraqmentləri. */
    private static final List<String> PLACEHOLDER_MARKERS = List.of("change_me", "changeme", "example", "placeholder", "dev-only");

    private Environment environment;

    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) {
        List<String> violations = new ArrayList<>(checkEnvironment(environment));
        violations.addAll(checkBeanDefinitions(beanFactory));
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Prod konfiqurasiyası təhlükəlidir, tətbiq başlamır:\n - "
                    + String.join("\n - ", violations));
        }
    }

    static List<String> checkEnvironment(Environment env) {
        List<String> violations = new ArrayList<>();

        List<String> profiles = Arrays.asList(env.getActiveProfiles());
        for (String forbidden : List.of("dev", "test")) {
            if (profiles.contains(forbidden)) {
                violations.add("'" + forbidden + "' profili 'prod' ilə birlikdə aktivdir");
            }
        }

        for (String key : List.of("app.notifications.sms-provider", "app.notifications.mail-provider")) {
            String provider = env.getProperty(key, "console");
            if ("console".equals(provider.trim().toLowerCase(Locale.ROOT))) {
                violations.add(key + " = console (yalnız dev üçündür)");
            }
        }

        if ("smtp".equals(env.getProperty("app.notifications.mail-provider", "").trim().toLowerCase(Locale.ROOT))
                && env.getProperty("app.notifications.mail-from", "").isBlank()) {
            violations.add("app.notifications.mail-from təyin olunmayıb (smtp üçün məcburidir)");
        }

        if ("http".equals(env.getProperty("app.notifications.sms-provider", "").trim().toLowerCase(Locale.ROOT))) {
            for (String key : List.of("app.notifications.sms-http.url", "app.notifications.sms-http.api-key")) {
                if (env.getProperty(key, "").isBlank()) {
                    violations.add(key + " təyin olunmayıb (http SMS provayderi üçün məcburidir)");
                }
            }
        }
        if ("postaguvercini".equals(env.getProperty("app.notifications.sms-provider", "").trim().toLowerCase(Locale.ROOT))) {
            for (String key : List.of("app.notifications.posta-guvercini.url",
                    "app.notifications.posta-guvercini.public-key", "app.notifications.posta-guvercini.private-key")) {
                if (env.getProperty(key, "").isBlank()) {
                    violations.add(key + " təyin olunmayıb (Poçt Göyərçini SMS provayderi üçün məcburidir)");
                }
            }
        }

        if (!env.getProperty("app.security.cookie-secure", Boolean.class, true)) {
            violations.add("app.security.cookie-secure söndürülüb");
        }
        if (!env.getProperty("app.security.hsts-enabled", Boolean.class, true)) {
            violations.add("app.security.hsts-enabled söndürülüb");
        }

        for (String key : List.of("app.dev.simulator-enabled", "app.dev.seed-enabled",
                "springdoc.api-docs.enabled", "springdoc.swagger-ui.enabled")) {
            if (env.getProperty(key, Boolean.class, false)) {
                violations.add(key + " aktivdir");
            }
        }

        // Sorğu limitləri prod-da yalnız azaldıla bilər (dev-də IP limitləri testlər üçün artırılıb)
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            String key = "app.rate-limits." + policy.name().toLowerCase(Locale.ROOT).replace('_', '-');
            Integer capacity = env.getProperty(key, Integer.class);
            if (capacity != null && capacity > policy.defaultCapacity()) {
                violations.add(key + " standartdan (" + policy.defaultCapacity() + ") yüksəkdir");
            }
        }

        for (String key : REQUIRED_SECRETS) {
            String value = env.getProperty(key);
            if (value == null || value.isBlank()) {
                violations.add(key + " təyin olunmayıb");
            } else if (looksLikePlaceholder(value)) {
                violations.add(key + " nümunə (yer tutucu) dəyərə bənzəyir");
            }
        }
        return violations;
    }

    static List<String> checkBeanDefinitions(ConfigurableListableBeanFactory beanFactory) {
        List<String> violations = new ArrayList<>();
        for (String name : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition definition = beanFactory.getBeanDefinition(name);
            String className = definition.getBeanClassName();
            if (className != null && className.startsWith(DEV_PACKAGE_PREFIX)) {
                violations.add("dev bean-i kontekstdədir: " + className);
            }
        }
        return violations;
    }

    private static boolean looksLikePlaceholder(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return PLACEHOLDER_MARKERS.stream().anyMatch(normalized::contains);
    }
}
