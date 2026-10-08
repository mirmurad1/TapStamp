package az.tapstamp.notification.mail;

/** Email göndərişi. Implementasiya {@code app.notifications.mail-provider} ilə seçilir: console (dev) və ya smtp. */
public interface MailSender {

    void send(MailMessage message);
}
