package az.tapstamp.notification.sms;

import java.time.Clock;
import java.time.Duration;

import az.tapstamp.shared.pii.PhoneMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class SmsConfig {

    private static final Logger log = LoggerFactory.getLogger(SmsConfig.class);

    /**
     * Dev və testlər: SMS göndərilmir — mətn loga və dev outbox-a yazılır (OTP kodunu görmək üçün). Prod-da bu provayder
     * qadağandır ({@code ProdSafetyGuard}), ona görə OTP prod loglarına heç vaxt düşmür.
     */
    @Bean
    @ConditionalOnProperty(name = "app.notifications.sms-provider", havingValue = "console", matchIfMissing = true)
    SmsProvider consoleSmsProvider(ApplicationEventPublisher events) {
        return message -> {
            SmsSegments.Info info = SmsSegments.analyze(message.text());
            log.info("SMS (konsol, yalnız dev): to={}, {} seqment ({}): {}", PhoneMasker.mask(message.to()),
                    info.segments(), info.encoding(), message.text().replace('\n', ' '));
            events.publishEvent(new SmsSentEvent(message));
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.notifications.sms-provider", havingValue = "http")
    SmsProvider httpSmsProvider(Environment environment, Clock clock) {
        return new HttpSmsProvider(
                environment.getRequiredProperty("app.notifications.sms-http.url"),
                environment.getRequiredProperty("app.notifications.sms-http.api-key"),
                environment.getProperty("app.notifications.sms-sender", ""),
                environment.getProperty("app.notifications.sms-http.timeout", Duration.class, Duration.ofSeconds(10)),
                clock);
    }

    /** Poçt Göyərçini (Figensoft) SMS API. */
    @Bean
    @ConditionalOnProperty(name = "app.notifications.sms-provider", havingValue = "postaguvercini")
    SmsProvider postaGuverciniSmsProvider(Environment environment, Clock clock) {
        return new PostaGuverciniSmsProvider(
                environment.getRequiredProperty("app.notifications.posta-guvercini.url"),
                environment.getRequiredProperty("app.notifications.posta-guvercini.public-key"),
                environment.getRequiredProperty("app.notifications.posta-guvercini.private-key"),
                environment.getProperty("app.notifications.sms-sender", ""),
                environment.getProperty("app.notifications.sms-http.timeout", Duration.class, Duration.ofSeconds(10)),
                clock);
    }
}
