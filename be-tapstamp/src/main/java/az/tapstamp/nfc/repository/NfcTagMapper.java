package az.tapstamp.nfc.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.nfc.NfcTag;
import az.tapstamp.nfc.TagKeys;
import az.tapstamp.shared.persistence.TenantMapper;
import org.jspecify.annotations.NonNull;

/**
 * {@code nfc_tags} və {@code nfc_tag_keys} — toxunuşun yoxlanması üçün (cari tenant-ın bazası).
 * SQL: {@code mapper/tenant/nfc/NfcTagMapper.xml}.
 */
@TenantMapper
public interface NfcTagMapper {

    /** Şifrəli açar dəsti (K0 burada oxunmur — yoxlama üçün lazım deyil). */
    record StoredKeys(UUID id, int version, TagKeys.KeyStatus status, byte[] metaReadKeyEnc, byte[] fileReadKeyEnc) {

        @Override
        public @NonNull String toString() {
            return "StoredKeys[id=" + id + ", status=" + status + "]";
        }
    }

    Optional<NfcTag> findByUid(byte[] uid);

    Optional<NfcTag> findById(UUID id);

    /** Sətir kilidi: eyni çipin paralel toxunuşları ardıcıl emal olunur (replay qorunması). */
    NfcTag lock(UUID id);

    /** Çipin filialı aktivdir (şirkət bağlamayıb). */
    boolean branchActive(UUID branchId);

    void acceptCounter(UUID id, int counter, Instant now);

    /** Yoxlama üçün açar dəstləri: əvvəlcə ACTIVE, sonra (rotasiya gedirsə) PENDING. */
    List<StoredKeys> verificationKeys(UUID tagId);

    void retireActiveKeys(UUID tagId, Instant now);

    void activatePendingKey(UUID keyId, Instant now);

    /** Rotasiyanın tamamlanması: köhnə ACTIVE dəst RETIRED, PENDING dəst ACTIVE olur (çağıranın tranzaksiyasında). */
    default void activateKeys(UUID tagId, UUID keyId, Instant now) {
        retireActiveKeys(tagId, now);
        activatePendingKey(keyId, now);
    }

    /** Çip üzrə dəqiqədə toxunuş limiti: filialın sui-istifadə qaydası, yoxdursa şirkət standartı. */
    Integer findTapLimitPerMinute(UUID branchId);

    default int tapLimitPerMinute(UUID branchId) {
        Integer limit = findTapLimitPerMinute(branchId);
        return limit == null ? 10 : limit;
    }
}
