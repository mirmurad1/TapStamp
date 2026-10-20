package az.tapstamp.notification.sms;

import org.jspecify.annotations.Nullable;

/**
 * SMS göndərilmədi (və ya nəticə məlum deyil). {@link Kind} kampaniya göndərişinin necə davranacağını müəyyən edir:
 * təkrar cəhd, kampaniyanın dayandırılması, alıcının son xətası və ya "göndərilib" sayılması.
 */
public class SmsDeliveryException extends RuntimeException {

    public enum Kind {
        /** Provayderə çatmadı və ya müvəqqəti xəta (qoşulma, 5xx) — SMS getməyib, təkrar cəhd etmək olar. */
        TRANSIENT,
        /** Provayder bir müddət əlçatmaz sayılır (ardıcıl xətalar) — sorğu göndərilmədi, cəhd sayılmır. */
        PROVIDER_DOWN,
        /** Sorğu bütövlükdə rədd edildi (icazə, balans, yanlış açar) — hesabın problemidir, bütün göndəriş dayanmalıdır. */
        ACCOUNT,
        /** Yalnız bu alıcı rədd edildi (yanlış və ya bloklanmış nömrə) — təkrar cəhd fayda verməz. */
        RECIPIENT,
        /** Sorğu göndərildi, amma cavab alınmadı (vaxt aşımı) — SMS getmiş ola bilər, təkrar göndərilməməlidir. */
        UNKNOWN
    }

    private final Kind kind;

    public SmsDeliveryException(Kind kind, String message) {
        this(kind, message, null);
    }

    public SmsDeliveryException(Kind kind, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
