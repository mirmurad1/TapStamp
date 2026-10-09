package az.tapstamp.nfc.admin;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.nfc.TapVerificationService;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminAuthService;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.security.admin.AdminRealms;
import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.security.admin.BranchScope;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.security.crypto.KeyPurpose;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.persistence.LocalizedTextConverter;
import az.tapstamp.tenancy.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * NFC çiplərinin idarəsi (bax: plan §6.7). Çipləri yalnız platforma admini əlavə edir və idarə edir (qərar 44); sahib
 * və menecer yalnız baxır. Platforma əməliyyatlarından əvvəl tenant konteksti {@code TenantContext.callAs} ilə təyin
 * olunur. Hər şirkətdə ilk {@code app.nfc.free-tags-per-tenant} çip pulsuzdur, qalanları pulludur (yalnız say).
 * Qeydiyyatda server 3 təsadüfi açar (K0, K1, K2) yaradır və şifrəli saxlayır
 * (PENDING); açarlar yalnız bir dəfə — provisioning paketində (sahib + step-up) göstərilir. Proqramlaşdırılmış çipə
 * ilk toxunuş PENDING dəsti ACTIVE edir. Açarın yenilənməsi yeni PENDING dəst yaradır; keçidə qədər köhnə dəst işləyir.
 */
@Service
public class TagAdminService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final LocalizedTextConverter TEXT = new LocalizedTextConverter();

    public record TagView(UUID id, String uid, String label, String type, UUID branchId, String status, Instant lastTapAt,
                      int lastCounter, Integer activeKeyVersion, Integer pendingKeyVersion, boolean pendingExported,
                      long tapsLast7Days, Instant createdAt, boolean free) {
    }

    /** Çiplərin sayı: pulsuz limit, cəmi, pulsuz və pullu (simulyator çipləri sayılmır). */
    public record TagUsage(int freeLimit, long total, long free, long paid) {
    }

    public record TapEvent(UUID id, String stage, String outcome, String reason, Integer counter, Integer counterGap,
                           UUID customerId, Instant createdAt) {
    }

    /**
     * Çipin proqramlaşdırılması üçün məlumat (NTAG 424 DNA, SDM/SUN). Ofsetlər NDEF faylının əvvəlindən baytlarladır:
     * NLEN (2) + URI qeydinin başlığı (5: D1 01 len 55 04) + "https://"-suz URL-də yerin indeksi.
     */
    public record ProvisioningPackage(String uid, int keyVersion, String url, String appMasterKey, String sdmMetaReadKey,
                                      String sdmFileReadKey, int piccDataOffset, int sdmMacInputOffset,
                                      int sdmMacOffset, String note) {
        @Override
        public @NonNull String toString() {
            return "ProvisioningPackage[uid=" + uid + ", keyVersion=" + keyVersion + "]";
        }
    }

    private final JdbcClient jdbc;
    private final EnvelopeCipher cipher;
    private final AdminAuthService adminAuth;
    private final AdminRealms realms;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int freeTags;

    public TagAdminService(@Qualifier("tenantJdbc") JdbcClient jdbc, @Qualifier("tenantCipher") EnvelopeCipher cipher,
                           AdminAuthService adminAuth, AdminRealms realms, AuditLogger audit,
                           PlatformTransactionManager transactionManager, Clock clock,
                           @Value("${app.nfc.free-tags-per-tenant:1}") int freeTags) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.adminAuth = adminAuth;
        this.realms = realms;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.freeTags = freeTags;
    }

    /** Pulsuz və pullu çiplərin sayı (bütün şirkət üzrə). */
    public TagUsage usage() {
        long total = jdbc.sql("SELECT count(*) FROM nfc_tags WHERE type <> 'SIMULATED'").query(Long.class).single();
        long free = Math.min(total, freeTags);
        return new TagUsage(freeTags, total, free, total - free);
    }

    /** Aktiv filiallar (platforma panelində çipin filialını seçmək üçün). */
    public List<BranchOption> branches() {
        return jdbc.sql("SELECT id, name::text AS name FROM branches WHERE status = 'ACTIVE' ORDER BY created_at")
                .query((rs, n) -> new BranchOption(rs.getObject("id", UUID.class),
                        TEXT.convertToEntityAttribute(rs.getString("name"))))
                .list();
    }

    public record BranchOption(UUID id, LocalizedText name) {
    }

    /** Sahib və platforma admini bütün çipləri, menecer öz filiallarının çiplərini görür. */
    public List<TagView> list(AdminPrincipal principal, UUID branchId) {
        BranchScope scope = BranchScope.of(principal, branchId);
        return jdbc.sql(SELECT + " WHERE (:all OR t.branch_id IN (:branches)) ORDER BY t.created_at DESC")
                .param("all", scope.all())
                .param("branches", scope.branchIds())
                .param("since", ts(clock.instant().minus(7, ChronoUnit.DAYS)))
                .param("freeTags", freeTags)
                .query((rs, n) -> map(rs))
                .list();
    }

    public TagView get(AdminPrincipal principal, UUID tagId) {
        TagView tag = jdbc.sql(SELECT + " WHERE t.id = :id")
                .param("id", tagId)
                .param("since", ts(clock.instant().minus(7, ChronoUnit.DAYS)))
                .param("freeTags", freeTags)
                .query((rs, n) -> map(rs))
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        AccessPolicy.requireBranch(principal, tag.branchId());
        return tag;
    }

    /** Yeni çip: UID (7 bayt, 14 hex), filial və etiket. Açarlar PENDING yaranır. */
    public TagView register(AdminPrincipal principal, String uidHex, UUID branchId, String label) {
        requirePlatform(principal);
        byte[] uid = parseUid(uidHex);
        requireActiveBranch(branchId);
        String cleanLabel = cleanLabel(label);
        Instant now = clock.instant();
        UUID id = Ids.newId();
        tx.executeWithoutResult(status -> {
            boolean exists = jdbc.sql("SELECT count(*) FROM nfc_tags WHERE uid = :uid").param("uid", uid)
                    .query(Integer.class).single() > 0;
            if (exists) {
                throw new ApiException(ErrorCode.ALREADY_EXISTS);
            }
            jdbc.sql("""
                            INSERT INTO nfc_tags (id, branch_id, uid, type, label, status, created_at, updated_at)
                            VALUES (:id, :branchId, :uid, 'NTAG424_SUN', :label, 'ACTIVE', :now, :now)""")
                    .param("id", id)
                    .param("branchId", branchId)
                    .param("uid", uid)
                    .param("label", cleanLabel)
                    .param("now", ts(now))
                    .update();
            insertPendingKeys(id, 1, now);
        });
        audit.tenant("TAG_REGISTERED", AuditLogger.ActorType.PLATFORM, principal.userId(), "tag", id,
                Map.of("uid", HEX.formatHex(uid), "branchId", branchId.toString()));
        return get(principal, id);
    }

    /** Etiket və ya filial dəyişikliyi. */
    public TagView update(AdminPrincipal principal, UUID tagId, String label, UUID branchId) {
        requirePlatform(principal);
        get(principal, tagId);
        if (branchId != null) {
            requireActiveBranch(branchId);
        }
        jdbc.sql("""
                        UPDATE nfc_tags SET label = COALESCE(:label, label), branch_id = COALESCE(:branchId, branch_id),
                                            updated_at = :now, version = version + 1
                        WHERE id = :id""")
                .param("label", label == null ? null : cleanLabel(label))
                .param("branchId", branchId)
                .param("now", ts(clock.instant()))
                .param("id", tagId)
                .update();
        Map<String, Object> details = new LinkedHashMap<>();
        if (label != null) {
            details.put("label", cleanLabel(label));
        }
        if (branchId != null) {
            details.put("branchId", branchId.toString());
        }
        audit.tenant("TAG_UPDATED", AuditLogger.ActorType.PLATFORM, principal.userId(), "tag", tagId, details);
        return get(principal, tagId);
    }

    /** Deaktivasiya dərhal qüvvəyə minir: bütün toxunuşlar {@code TAG_DISABLED} alır. */
    public TagView setEnabled(AdminPrincipal principal, UUID tagId, boolean enabled) {
        requirePlatform(principal);
        get(principal, tagId);
        Instant now = clock.instant();
        jdbc.sql("""
                        UPDATE nfc_tags SET status = :status, disabled_at = :disabledAt, disabled_by = NULL,
                                            updated_at = :now, version = version + 1
                        WHERE id = :id""")
                .param("status", enabled ? "ACTIVE" : "DISABLED")
                .param("disabledAt", enabled ? null : ts(now))
                .param("now", ts(now))
                .param("id", tagId)
                .update();
        audit.tenant(enabled ? "TAG_ENABLED" : "TAG_DISABLED", AuditLogger.ActorType.PLATFORM, principal.userId(), "tag",
                tagId, Map.of());
        return get(principal, tagId);
    }

    /**
     * Açarın yenilənməsi (sahib + step-up): yeni PENDING dəst. Hələ ixrac olunmamış PENDING dəst varsa, o əvəzlənir;
     * ixrac olunubsa (çip artıq proqramlaşdırılıb ola bilər), xəta — əvvəlcə ona toxunulmalıdır.
     */
    public TagView rotateKeys(AdminPrincipal principal, UUID tagId) {
        requirePlatform(principal);
        adminAuth.requireStepUp(principal);
        TagView tag = get(principal, tagId);
        if (tag.pendingKeyVersion() != null && tag.pendingExported()) {
            throw new ApiException(ErrorCode.TAG_ROTATION_IN_PROGRESS);
        }
        Instant now = clock.instant();
        tx.executeWithoutResult(status -> {
            jdbc.sql("DELETE FROM nfc_tag_keys WHERE tag_id = :id AND status = 'PENDING' AND provisioning_exported_at IS NULL")
                    .param("id", tagId).update();
            int next = jdbc.sql("SELECT coalesce(max(key_version), 0) + 1 FROM nfc_tag_keys WHERE tag_id = :id")
                    .param("id", tagId).query(Integer.class).single();
            insertPendingKeys(tagId, next, now);
        });
        audit.tenant("TAG_KEYS_ROTATED", AuditLogger.ActorType.PLATFORM, principal.userId(), "tag", tagId, Map.of());
        return get(principal, tagId);
    }

    /** Provisioning paketi (sahib + step-up) — PENDING açarlar yalnız bir dəfə göstərilir, sonra auditə yazılır. */
    public ProvisioningPackage provisioningPackage(AdminPrincipal principal, UUID tagId) {
        requirePlatform(principal);
        adminAuth.requireStepUp(principal);
        TagView tag = get(principal, tagId);
        UUID tenantId = TenantContext.require().id();
        ProvisioningPackage pkg = tx.execute(status -> {
            Map<String, Object> keys = jdbc.sql("""
                            UPDATE nfc_tag_keys SET provisioning_exported_at = :now
                            WHERE tag_id = :id AND status = 'PENDING' AND provisioning_exported_at IS NULL
                            RETURNING id, key_version, app_master_key_enc, meta_read_key_enc, file_read_key_enc""")
                    .param("now", ts(clock.instant()))
                    .param("id", tagId)
                    .query()
                    .listOfRows()
                    .stream().findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.PROVISIONING_ALREADY_EXPORTED));
            UUID keyId = (UUID) keys.get("id");
            String k0 = HEX.formatHex(cipher.decrypt((byte[]) keys.get("app_master_key_enc"),
                    TapVerificationService.keyAad(tenantId, "app_master_key_enc", keyId)));
            String k1 = HEX.formatHex(cipher.decrypt((byte[]) keys.get("meta_read_key_enc"),
                    TapVerificationService.keyAad(tenantId, "meta_read_key_enc", keyId)));
            String k2 = HEX.formatHex(cipher.decrypt((byte[]) keys.get("file_read_key_enc"),
                    TapVerificationService.keyAad(tenantId, "file_read_key_enc", keyId)));
            String piccPlaceholder = "0".repeat(32);
            String macPlaceholder = "0".repeat(16);
            String url = realms.baseUrl(AdminRealm.STAFF) + "/t/" + tag.uid() + "?picc=" + piccPlaceholder + "&cmac=" + macPlaceholder;
            String withoutScheme = url.substring("https://".length());
            int piccOffset = 7 + withoutScheme.indexOf("picc=") + "picc=".length();
            int macOffset = 7 + withoutScheme.indexOf("cmac=") + "cmac=".length();
            return new ProvisioningPackage(tag.uid(), ((Number) keys.get("key_version")).intValue(), url, k0, k1, k2,
                    piccOffset, macOffset, macOffset,
                    "SDM: PICCData K1 ilə şifrəli (UID + sayğac), CMAC K2 ilə (boş giriş). Ətraflı: docs/nfc-ntag424-programming.md");
        });
        audit.tenant("TAG_PROVISIONING_EXPORTED", AuditLogger.ActorType.PLATFORM, principal.userId(), "tag", tagId,
                Map.of("keyVersion", pkg.keyVersion()));
        return pkg;
    }

    /** Çipin son toxunuşları (qəbul və rədd edilənlər, səbəbləri ilə). */
    public List<TapEvent> events(AdminPrincipal principal, UUID tagId) {
        get(principal, tagId);
        return jdbc.sql("""
                        SELECT id, stage, outcome, reason, counter, counter_gap, customer_id, created_at FROM tap_events
                        WHERE tag_id = :id ORDER BY created_at DESC LIMIT 100""")
                .param("id", tagId)
                .query((rs, n) -> new TapEvent(rs.getObject("id", UUID.class), rs.getString("stage"),
                        rs.getString("outcome"), rs.getString("reason"), (Integer) rs.getObject("counter"),
                        (Integer) rs.getObject("counter_gap"), rs.getObject("customer_id", UUID.class),
                        instant(rs.getTimestamp("created_at"))))
                .list();
    }

    // ------------------------------------------------------------------

    private static final String SELECT = """
            SELECT t.id, t.uid, t.label, t.type, t.branch_id, t.status, t.last_tap_at, t.last_counter, t.created_at,
                   (SELECT k.key_version FROM nfc_tag_keys k WHERE k.tag_id = t.id AND k.status = 'ACTIVE') AS active_version,
                   (SELECT k.key_version FROM nfc_tag_keys k WHERE k.tag_id = t.id AND k.status = 'PENDING') AS pending_version,
                   EXISTS (SELECT 1 FROM nfc_tag_keys k WHERE k.tag_id = t.id AND k.status = 'PENDING'
                           AND k.provisioning_exported_at IS NOT NULL) AS pending_exported,
                   (SELECT count(*) FROM tap_events e WHERE e.tag_id = t.id AND e.stage = 'VERIFY'
                           AND e.outcome = 'ACCEPTED' AND e.created_at >= :since) AS taps_7d,
                   (t.type = 'SIMULATED' OR (SELECT count(*) FROM nfc_tags o WHERE o.type <> 'SIMULATED'
                           AND (o.created_at, o.id) < (t.created_at, t.id)) < :freeTags) AS free
            FROM nfc_tags t""";

    private static void requirePlatform(AdminPrincipal principal) {
        AccessPolicy.requireAnyRole(principal, AdminRole.SUPER_ADMIN);
    }

    /** {@code created_by} və {@code disabled_by} işçiyə istinad edir; platforma admini işçi deyil — aktor auditdədir. */
    private void insertPendingKeys(UUID tagId, int version, Instant now) {
        UUID keyId = Ids.newId();
        UUID tenantId = TenantContext.require().id();
        jdbc.sql("""
                        INSERT INTO nfc_tag_keys (id, tag_id, key_version, status, app_master_key_enc, meta_read_key_enc,
                                                  file_read_key_enc, created_by, created_at)
                        VALUES (:id, :tagId, :version, 'PENDING', :k0, :k1, :k2, NULL, :now)""")
                .param("id", keyId)
                .param("tagId", tagId)
                .param("version", version)
                .param("k0", cipher.encrypt(KeyPurpose.NFC_KEYS, randomKey(),
                        TapVerificationService.keyAad(tenantId, "app_master_key_enc", keyId)))
                .param("k1", cipher.encrypt(KeyPurpose.NFC_KEYS, randomKey(),
                        TapVerificationService.keyAad(tenantId, "meta_read_key_enc", keyId)))
                .param("k2", cipher.encrypt(KeyPurpose.NFC_KEYS, randomKey(),
                        TapVerificationService.keyAad(tenantId, "file_read_key_enc", keyId)))
                .param("now", ts(now))
                .update();
    }

    private void requireActiveBranch(UUID branchId) {
        boolean exists = branchId != null && jdbc.sql("SELECT count(*) FROM branches WHERE id = :id AND status = 'ACTIVE'")
                .param("id", branchId).query(Integer.class).single() > 0;
        if (!exists) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    private static byte[] parseUid(String uidHex) {
        String clean = uidHex == null ? "" : uidHex.replace(":", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (!clean.matches("^[0-9A-F]{14}$")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return HexFormat.of().parseHex(clean);
    }

    private static String cleanLabel(String label) {
        String clean = label == null ? "" : label.strip();
        if (clean.isEmpty() || clean.length() > 60) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return clean;
    }

    private static byte[] randomKey() {
        byte[] key = new byte[16];
        RANDOM.nextBytes(key);
        return key;
    }

    private static TagView map(ResultSet rs) throws SQLException {
        return new TagView(rs.getObject("id", UUID.class), HEX.formatHex(rs.getBytes("uid")), rs.getString("label"),
                rs.getString("type"), rs.getObject("branch_id", UUID.class), rs.getString("status"),
                instant(rs.getTimestamp("last_tap_at")), rs.getInt("last_counter"),
                (Integer) rs.getObject("active_version"), (Integer) rs.getObject("pending_version"),
                rs.getBoolean("pending_exported"), rs.getLong("taps_7d"), instant(rs.getTimestamp("created_at")),
                rs.getBoolean("free"));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static java.time.OffsetDateTime ts(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
