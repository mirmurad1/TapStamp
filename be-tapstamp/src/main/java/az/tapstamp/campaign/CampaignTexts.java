package az.tapstamp.campaign;

import az.tapstamp.notification.sms.SmsSegments;

/**
 * Kampaniya mətni: müştəri SMS-in hansı şirkətdən gəldiyini bilsin deyə əvvəlinə şirkətin "SMS-də görünən adı" əlavə
 * olunur ({@code "Kafe: <mətn>"}) — göndərən adı bütün şirkətlər üçün eynidir (TrendStamp).
 *
 * <p>Azərbaycan hərfləri (ə, ş, ğ, ı, ç, ö, ü) mesajı UCS-2-yə keçirir: bir SMS 160 yox, 70 simvol tutur. Önizləmə
 * mətnin latın hərfləri ilə variantını və onun SMS sayını da göstərir.
 */
public final class CampaignTexts {

    private static final String FROM = "əƏşŞğĞıİçÇöÖüÜ";
    private static final String TO = "eEsSgGiIcCoOuU";

    private CampaignTexts() {
    }

    /** Göndəriləcək tam mətn. */
    public static String compose(String companyName, String text) {
        return companyName.strip() + ": " + text.strip();
    }

    /** Azərbaycan hərfləri latın qarşılığı ilə (ə→e, ş→s, ğ→g, ı→i, ç→c, ö→o, ü→u). */
    public static String latin(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            int index = FROM.indexOf(ch);
            out.append(index >= 0 ? TO.charAt(index) : ch);
        }
        return out.toString();
    }

    /** Mətnin SMS hissələri: kodlaşdırma, simvol sayı (SMS vahidləri ilə) və hissə sayı. */
    public static SmsSegments.Info analyze(String fullText) {
        return SmsSegments.analyze(fullText);
    }
}
