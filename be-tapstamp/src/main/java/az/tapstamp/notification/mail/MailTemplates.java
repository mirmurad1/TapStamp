package az.tapstamp.notification.mail;

import az.tapstamp.shared.i18n.Language;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

/** Email mətnləri alıcının dilində ({@code messages_*.properties}, {@code mail.*} açarları). */
@Component
public class MailTemplates {

    private final MessageSource messages;

    public MailTemplates(@Qualifier(AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME) MessageSource messages) {
        this.messages = messages;
    }

    public MailMessage passwordReset(String to, Language language, String link, long validMinutes, String tenantId) {
        return render("passwordReset", to, language, tenantId, link, validMinutes);
    }

    public MailMessage passwordChanged(String to, Language language, String tenantId) {
        return render("passwordChanged", to, language, tenantId);
    }

    public MailMessage invitation(String to, Language language, String companyName, String link, long validHours,
                                  String tenantId) {
        return render("invitation", to, language, tenantId, companyName, link, validHours);
    }

    /** Platformaya: SMS limitinin 80% / 100%-i doldu. */
    public MailMessage smsLimitAlert(String to, Language language, String scope, int level, long used, long limit,
                                     java.time.LocalDate day) {
        return render(level >= 100 ? "smsLimitReached" : "smsLimitWarning", to, language, null, scope, used, limit,
                day.toString());
    }

    private MailMessage render(String template, String to, Language language, String tenantId, Object... args) {
        String subject = messages.getMessage("mail." + template + ".subject", args, language.locale());
        String body = messages.getMessage("mail." + template + ".body", args, language.locale());
        return new MailMessage(to, subject, body, tenantId);
    }
}
