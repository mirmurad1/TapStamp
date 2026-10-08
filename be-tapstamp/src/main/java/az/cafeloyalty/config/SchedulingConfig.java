package az.cafeloyalty.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Fon işləri ({@code @Scheduled}). Həmişə aktivdir — testlər də eyni bean konfiqurasiyası ilə başlayır; ayrı-ayrı
 * işlər öz parametrləri ilə söndürülə bilər (məs. {@code app.jobs.enabled=false}).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
