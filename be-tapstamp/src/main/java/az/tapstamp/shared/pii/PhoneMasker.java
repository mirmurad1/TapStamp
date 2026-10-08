package az.tapstamp.shared.pii;

/**
 * Telefon nömrəsinin maskalanmış görünüşü: siyahılarda və loglarda tam nömrə göstərilmir.
 * {@code +994501234567} → {@code +994 50 ***-**-67}.
 */
public final class PhoneMasker {

    private PhoneMasker() {
    }

    public static String mask(String e164) {
        if (e164 == null || !e164.matches("^\\+\\d{8,15}$")) {
            return "***";
        }
        String lastTwo = e164.substring(e164.length() - 2);
        if (e164.startsWith("+994") && e164.length() == 13) {
            return "+994 " + e164.substring(4, 6) + " ***-**-" + lastTwo;
        }
        // Digər ölkələr: yalnız ölkə kodunun başlanğıcı və son iki rəqəm
        return e164.substring(0, Math.min(4, e164.length() - 2)) + " ***" + lastTwo;
    }
}
