package az.tapstamp.shared.pii;

/**
 * Telefon nömrəsinin maskalanmış görünüşü: ekranlarda və loglarda tam nömrə göstərilmir.
 * {@code +994513102967} → {@code +994 51 *** 29 67} (operator kodu və son dörd rəqəm qalır).
 */
public final class PhoneMasker {

    /** Köhnə formatın ({@code +994 50 ***-**-67}) tanınması — bazadakı dəyərlərin yenilənməsi üçün. */
    public static final String LEGACY_MARKER = "***-**-";

    private PhoneMasker() {
    }

    public static String mask(String e164) {
        if (e164 == null || !e164.matches("^\\+\\d{8,15}$")) {
            return "***";
        }
        int length = e164.length();
        String lastFour = e164.substring(length - 4, length - 2) + " " + e164.substring(length - 2);
        if (e164.startsWith("+994") && length == 13) {
            return "+994 " + e164.substring(4, 6) + " *** " + lastFour;
        }
        // Digər ölkələr: ölkə kodunun başlanğıcı və son dörd rəqəm
        return e164.substring(0, 4) + " *** " + lastFour;
    }
}
