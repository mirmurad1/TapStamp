package az.cafeloyalty.notification.mail;

import az.cafeloyalty.shared.i18n.Language;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/** Email mətnləri alıcının dilində ({@code messages_*.properties}, {@code mail.*} açarları). */
@Component
public class MailTemplates {

    private final MessageSource messages;

    public MailTemplates(MessageSource messages) {
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

    private MailMessage render(String template, String to, Language language, String tenantId, Object... args) {
        String subject = messages.getMessage("mail." + template + ".subject", args, language.locale());
        String body = messages.getMessage("mail." + template + ".body", args, language.locale());
        return new MailMessage(to, subject, body, tenantId);
    }
}
