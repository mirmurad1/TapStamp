package az.cafeloyalty.notification.sms;

/**
 * SMS-in neçə hissəyə (seqmentə) bölünəcəyinin hesablanması. Hər seqment ayrıca ödənilir.
 *
 * <ul>
 *   <li>GSM-7 əlifbası (latın hərfləri, rəqəmlər, bəzi simvollar): 1 seqment — 160 simvol, çox seqmentdə 153;</li>
 *   <li>əlifbada olmayan hər hansı simvol (məs. ə, ş, ğ, ı, ç və kiril hərfləri) bütün mesajı UCS-2-yə keçirir:
 *       1 seqment — 70 simvol, çox seqmentdə 67.</li>
 * </ul>
 * Deməli, Azərbaycan dilindəki SMS də, rus dilindəki kimi, 70 simvola sığmalıdır.
 */
public final class SmsSegments {

    /** GSM 03.38 əsas cədvəli (ESC simvolu istisna olmaqla). */
    private static final String GSM_BASIC = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
            + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
    /** Genişlənmə cədvəli: hər simvol 2 yer tutur (ESC + simvol). */
    private static final String GSM_EXTENSION = "\f^{}\\[~]|€";

    public enum Encoding {
        GSM_7,
        UCS_2
    }

    /**
     * @param units    mesajın uzunluğu kodlaşdırma vahidlərində (GSM-7-də septet, UCS-2-də UTF-16 vahidi)
     * @param segments seqment sayı
     */
    public record Info(Encoding encoding, int units, int segments) {
    }

    private SmsSegments() {
    }

    public static Info analyze(String text) {
        int septets = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (GSM_BASIC.indexOf(c) >= 0) {
                septets++;
            } else if (GSM_EXTENSION.indexOf(c) >= 0) {
                septets += 2;
            } else {
                int units = text.length();
                return new Info(Encoding.UCS_2, units, segments(units, 70, 67));
            }
        }
        return new Info(Encoding.GSM_7, septets, segments(septets, 160, 153));
    }

    private static int segments(int units, int single, int multipart) {
        if (units == 0) {
            return 1;
        }
        return units <= single ? 1 : (units + multipart - 1) / multipart;
    }
}
