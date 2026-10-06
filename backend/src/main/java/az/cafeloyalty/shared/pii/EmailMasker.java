package az.cafeloyalty.shared.pii;

/** Email ünvanının audit və loglar üçün maskalanmış görünüşü: {@code owner@mockcoffee.az} → {@code o***@mockcoffee.az}. */
public final class EmailMasker {

    private EmailMasker() {
    }

    public static String mask(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        return (at == 0 ? "" : email.substring(0, 1)) + "***" + email.substring(at);
    }
}
