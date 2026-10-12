package az.tapstamp.notification.sms;

import az.tapstamp.shared.i18n.Language;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

/**
 * SMS mətnləri ({@code sms.*} açarları). OTP mesajı bütün dillər üçün eynidir: {@code "<şirkət> OTP: <kod>"}. Həmişə 1
 * seqmentə sığdırılır: şirkətin adı uzundursa qısaldılır, lazım gələrsə adsız qısa şablon işlədilir.
 */
@Component
public class SmsTemplates {

    private static final int MIN_NAME_LENGTH = 3;

    private final MessageSource messages;

    public SmsTemplates(@Qualifier(AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME) MessageSource messages) {
        this.messages = messages;
    }

    public String otp(String companyName, String code) {
        String name = companyName == null ? "" : companyName.strip();
        for (int length = name.length(); length >= MIN_NAME_LENGTH; length--) {
            String text = messages.getMessage("sms.otp", new Object[] {name.substring(0, length).strip(), code},
                    Language.DEFAULT.locale());
            if (SmsSegments.analyze(text).segments() == 1) {
                return text;
            }
        }
        return messages.getMessage("sms.otpShort", new Object[] {code}, Language.DEFAULT.locale());
    }
}
