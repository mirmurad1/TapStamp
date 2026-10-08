package az.cafeloyalty.notification.sms;

import az.cafeloyalty.shared.i18n.Language;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * SMS mətnləri alıcının dilində ({@code sms.*} açarları). OTP mesajı həmişə 1 seqmentə sığdırılır: şirkətin adı uzundursa
 * qısaldılır, lazım gələrsə adsız qısa şablon işlədilir. Android-in Web OTP sətri ({@code @domen #kod}) yalnız yer
 * qalanda əlavə olunur.
 */
@Component
public class SmsTemplates {

    private static final int MIN_NAME_LENGTH = 3;

    private final MessageSource messages;

    public SmsTemplates(MessageSource messages) {
        this.messages = messages;
    }

    public String otp(Language language, String companyName, String code, String webOtpHost) {
        String text = otpText(language, companyName == null ? "" : companyName.strip(), code);
        if (webOtpHost != null && !webOtpHost.isBlank()) {
            String withWebOtp = text + "\n\n@" + webOtpHost + " #" + code;
            if (SmsSegments.analyze(withWebOtp).segments() == 1) {
                return withWebOtp;
            }
        }
        return text;
    }

    private String otpText(Language language, String companyName, String code) {
        for (int length = companyName.length(); length >= MIN_NAME_LENGTH; length--) {
            String name = companyName.substring(0, length).strip();
            String text = messages.getMessage("sms.otp", new Object[] {name, code}, language.locale());
            if (SmsSegments.analyze(text).segments() == 1) {
                return text;
            }
        }
        return messages.getMessage("sms.otpShort", new Object[] {code}, language.locale());
    }
}
