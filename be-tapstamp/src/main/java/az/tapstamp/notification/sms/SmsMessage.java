package az.tapstamp.notification.sms;

import az.tapstamp.shared.pii.PhoneMasker;
import org.jspecify.annotations.NonNull;

/**
 * Göndəriləcək SMS. {@code to} E.164 formatındadır; {@code tenantId} dev outbox-da mesajları şirkətə görə ayırmaq
 * üçündür. Göndərən adı (alfa-ad) bütün şirkətlər üçün birdir və provayderin ayarıdır ({@code app.notifications.sms-sender}).
 */
public record SmsMessage(String to, String text, String tenantId) {

    @Override
    public @NonNull String toString() {
        // Mətndə OTP kodu var — toString-də göstərilmir, nömrə maskalanır
        return "SmsMessage[to=" + PhoneMasker.mask(to) + "]";
    }
}
