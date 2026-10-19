package az.tapstamp.campaign;

import java.util.List;

import az.tapstamp.notification.sms.SmsSegments;
import org.jspecify.annotations.Nullable;

/**
 * Kampaniya mətni: müştəri SMS-in hansı şirkətdən gəldiyini bilsin deyə əvvəlinə şirkətin "SMS-də görünən adı" əlavə
 * olunur ({@code "Kafe: <mətn>"}) — göndərən adı bütün şirkətlər üçün eynidir (TrendStamp). Fərdi müraciət seçiləndə
 * adın ardınca {@code "Hörmətli <ad>, "} (adı yoxdursa {@code "Hörmətli istifadəçi, "}) gəlir.
 *
 * <p>Azərbaycan hərfləri (ə, ş, ğ, ı, ç, ö, ü) mesajı UCS-2-yə keçirir: bir SMS 160 yox, 70 simvol tutur ("Hörmətli"
 * sözünün özündə də ö var). Önizləmə mətnin latın hərfləri ilə variantını və onun SMS sayını da göstərir.
 */
public final class CampaignTexts {

    /** Bir neçə alıcı üçün SMS sayı: ən az və ən çox hissə, cəmi. */
    public record Totals(int minSegments, int maxSegments, long total) {
    }

    private static final String FROM = "əƏşŞğĞıİçÇöÖüÜ";
    private static final String TO = "eEsSgGiIcCoOuU";

    private CampaignTexts() {
    }

    /** Göndəriləcək tam mətn; {@code personalized} olanda müştərinin adı ilə müraciət. */
    public static String compose(String companyName, String text, boolean personalized, @Nullable String customerName) {
        String greeting = personalized ? greeting(customerName) + ", " : "";
        return companyName.strip() + ": " + greeting + text.strip();
    }

    static String greeting(@Nullable String customerName) {
        return "Hörmətli " + (customerName == null || customerName.isBlank() ? "istifadəçi" : customerName.strip());
    }

    /** Göndəriləcək mesaj: {@code latin} olanda bütün mətn (müraciət və ad daxil) latın hərfləri ilə. */
    public static String message(String companyName, String text, boolean personalized, @Nullable String customerName,
                                 boolean latin) {
        String full = compose(companyName, text, personalized, customerName);
        return latin ? latin(full) : full;
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

    /**
     * Alıcılar üzrə SMS sayı. Fərdi müraciətdə hər müştərinin adı ilə hesablanır (ad uzunluğu və hərfləri hissə sayını
     * dəyişə bilər), əks halda mətn hamı üçün eynidir.
     *
     * @param names alıcıların adları (adı yoxdursa null); fərdi deyilsə yalnız sayı vacibdir
     */
    public static Totals totals(String companyName, String text, boolean personalized, List<@Nullable String> names,
                                boolean latin) {
        if (!personalized) {
            int segments = analyze(maybeLatin(compose(companyName, text, false, null), latin)).segments();
            return new Totals(segments, segments, (long) segments * names.size());
        }
        int min = Integer.MAX_VALUE;
        int max = 0;
        long total = 0;
        for (String name : names) {
            int segments = analyze(maybeLatin(compose(companyName, text, true, name), latin)).segments();
            min = Math.min(min, segments);
            max = Math.max(max, segments);
            total += segments;
        }
        if (names.isEmpty()) {
            int segments = analyze(maybeLatin(compose(companyName, text, true, null), latin)).segments();
            return new Totals(segments, segments, 0);
        }
        return new Totals(min, max, total);
    }

    private static String maybeLatin(String text, boolean latin) {
        return latin ? latin(text) : text;
    }
}
