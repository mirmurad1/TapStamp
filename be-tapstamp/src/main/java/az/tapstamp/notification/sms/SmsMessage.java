package az.tapstamp.notification.sms;

import az.tapstamp.shared.pii.PhoneMasker;

/**
 * Göndəriləcək SMS. {@code to} E.164 formatındadır; {@code sender} şirkətin SMS göndərən adıdır (yoxdursa provayderin
 * standart adı); {@code tenantId} dev outbox-da mesajları şirkətə görə ayırmaq üçündür.
 */
public record SmsMessage(String to, String text, String sender, String tenantId) {

    @Override
    public String toString() {
        // Mətndə OTP kodu var — toString-də göstərilmir, nömrə maskalanır
        return "SmsMessage[to=" + PhoneMasker.mask(to) + "]";
    }
}
