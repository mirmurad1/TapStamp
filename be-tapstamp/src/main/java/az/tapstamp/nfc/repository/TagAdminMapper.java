package az.tapstamp.nfc.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.nfc.admin.TagAdminService.BranchOption;
import az.tapstamp.nfc.admin.TagAdminService.TagView;
import az.tapstamp.nfc.admin.TagAdminService.TapEvent;
import az.tapstamp.shared.persistence.TenantMapper;

/** NFC çiplərinin idarəsi (platforma paneli və şirkətin baxışı). SQL: {@code mapper/tenant/nfc/TagAdminMapper.xml}. */
@TenantMapper
public interface TagAdminMapper {

    /** İxrac olunan PENDING açar dəsti (şifrəli). */
    record ExportedKeys(UUID id, int keyVersion, byte[] appMasterKeyEnc, byte[] metaReadKeyEnc, byte[] fileReadKeyEnc) {
    }

    /**
     * Çiplər: {@code all} — bütün filiallar, əks halda yalnız {@code branchIds}. {@code since} — toxunuş sayı üçün dövrün
     * başlanğıcı, {@code freeTags} — pulsuz çip limiti (ən köhnə çiplər pulsuzdur).
     */
    List<TagView> list(boolean all, List<UUID> branchIds, Instant since, int freeTags, int limit, long offset);

    long count(boolean all, List<UUID> branchIds);

    Optional<TagView> find(UUID id, Instant since, int freeTags);

    long countBillable();

    List<BranchOption> activeBranches();

    boolean activeBranchExists(UUID id);

    boolean uidExists(byte[] uid);

    void insertTag(UUID id, UUID branchId, byte[] uid, String label, Instant now);

    /** {@code null} sahələr dəyişmir. */
    void updateTag(UUID id, String label, UUID branchId, Instant now);

    void updateStatus(UUID id, String status, Instant disabledAt, Instant now);

    /**
     * Yeni PENDING açar dəsti. {@code created_by} işçiyə istinad edir; çipləri platforma admini idarə edir (işçi deyil),
     * ona görə boş qalır — aktor auditdədir.
     */
    void insertPendingKeys(UUID id, UUID tagId, int version, byte[] appMasterKeyEnc, byte[] metaReadKeyEnc,
                           byte[] fileReadKeyEnc, Instant now);

    /** Hələ ixrac olunmamış PENDING dəst silinir (rotasiya onu əvəzləyir). */
    void deleteUnexportedPendingKeys(UUID tagId);

    int nextKeyVersion(UUID tagId);

    /** PENDING dəsti ixrac olunmuş kimi işarələyir və qaytarır (yalnız bir dəfə). */
    Optional<ExportedKeys> exportPendingKeys(UUID tagId, Instant now);

    /** Son 100 toxunuş cəhdi. */
    List<TapEvent> events(UUID tagId, int limit, long offset);

    long countEvents(UUID tagId);
}
