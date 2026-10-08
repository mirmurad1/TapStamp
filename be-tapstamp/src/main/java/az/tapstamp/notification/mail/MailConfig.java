package az.tapstamp.notification.mail;

import az.tapstamp.shared.pii.EmailMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    /**
     * Dev və testlər: email göndərilmir, yalnız hadisə yayımlanır (dev outbox göstərir). Mətnin özü yalnız DEBUG
     * səviyyəsində loga yazılır; prod-da bu provayder qadağandır (ProdSafetyGuard).
     */
    @Bean
    @ConditionalOnProperty(name = "app.notifications.mail-provider", havingValue = "console", matchIfMissing = true)
    MailSender consoleMailSender(ApplicationEventPublisher events) {
        return message -> {
            log.info("Email (konsol): to={}, subject={}", EmailMasker.mask(message.to()), message.subject());
            log.debug("Email mətni:\n{}", message.textBody());
            events.publishEvent(new MailSentEvent(message));
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.notifications.mail-provider", havingValue = "smtp")
    MailSender smtpMailSender(ObjectProvider<JavaMailSender> javaMailSender,
                              Environment environment) {
        String from = environment.getRequiredProperty("app.notifications.mail-from");
        return message -> {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(message.to());
            mail.setSubject(message.subject());
            mail.setText(message.textBody());
            javaMailSender.getObject().send(mail);
        };
    }
}
