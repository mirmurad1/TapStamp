package az.tapstamp.nfc;

/**
 * Toxunuşun kriptoqrafik yoxlaması uğursuzdur. {@link #reason()} yalnız {@code tap_events}-ə yazılır; istifadəçi
 * həmişə eyni ümumi kodu ({@code TAP_SIGNATURE_INVALID}) görür — hücumçu hansı addımı keçdiyini bilmir.
 */
public class TapVerificationException extends RuntimeException {

    private final String reason;

    public TapVerificationException(String reason) {
        super(reason, null, false, false);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
