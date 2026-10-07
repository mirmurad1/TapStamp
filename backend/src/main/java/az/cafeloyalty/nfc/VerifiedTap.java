package az.cafeloyalty.nfc;

/** Kriptoqrafik yoxlamadan keçmiş toxunuş: çipin həqiqi UID-i və oxunma sayğacı. */
public record VerifiedTap(byte[] uid, int counter) {
}
