package az.cafeloyalty.nfc;

import java.util.UUID;

/** Çip (tenant bazasındakı {@code nfc_tags} sətri). {@code lastCounter} — son qəbul olunmuş oxunma sayğacı (-1: hələ yoxdur). */
public record NfcTag(UUID id, UUID branchId, byte[] uid, TagType type, String label, boolean active, int lastCounter) {
}
