package az.cafeloyalty.nfc;

/**
 * Çipdən gələn imzalı məlumatı yoxlayır. DB-yə yazmır; sayğac və replay məntiqi ortaq
 * {@link TapVerificationService}-dədir.
 */
public interface TagVerifier {

    boolean supports(TagType type);

    /**
     * @param keys çipin açarları; simulyasiya olunan çip üçün {@code null}
     * @return UID və sayğac
     * @throws TapVerificationException imza, UID və ya PICC məlumatı yanlışdırsa (dəqiq səbəblə)
     */
    VerifiedTap verify(NfcTag tag, TagKeys keys, TapParams params);
}
