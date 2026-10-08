package az.tapstamp.notification.mail;

import az.tapstamp.shared.pii.EmailMasker;

/**
 * Göndəriləcək email. {@code tenantId} dev outbox-da mesajları şirkətə görə ayırmaq üçündür (platforma üçün null).
 */
public record MailMessage(String to, String subject, String textBody, String tenantId) {

    @Override
    public String toString() {
        // Mətnin içində tokenlər ola bilər — toString-də göstərilmir
        return "MailMessage[to=" + EmailMasker.mask(to) + ", subject=" + subject + "]";
    }
}
