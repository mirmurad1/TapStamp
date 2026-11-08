package az.tapstamp.nfc.admin;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.nfc.TapVerificationService;
import az.tapstamp.nfc.repository.TagAdminMapper.ExportedKeys;
import az.tapstamp.nfc.repository.TagAdminMapper;
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
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import az.tapstamp.tenancy.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

    private final TagAdminMapper tags;
    private final EnvelopeCipher cipher;
    private final AdminAuthService adminAuth;
    private final AdminRealms realms;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int freeTags;

    public TagAdminService(TagAdminMapper tags, @Qualifier("tenantCipher") EnvelopeCipher cipher,
                           AdminAuthService adminAuth, AdminRealms realms, AuditLogger audit,
                           PlatformTransactionManager transactionManager, Clock clock,
                           @Value("${app.nfc.free-tags-per-tenant:1}") int freeTags) {
        this.tags = tags;
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
        long total = tags.countBillable();
        long free = Math.min(total, freeTags);
        return new TagUsage(freeTags, total, free, total - free);
    }

    /** Aktiv filiallar (platforma panelində çipin filialını seçmək üçün). */
    public List<BranchOption> branches() {
        return tags.activeBranches();
    }

    public record BranchOption(UUID id, LocalizedText name) {
    }

    /** Sahib və platforma admini bütün çipləri, menecer öz filiallarının çiplərini görür. */
    public PageResult<TagView> list(AdminPrincipal principal, UUID branchId, PageQuery page) {
        BranchScope scope = BranchScope.of(principal, branchId);
        return page.result(tags.list(scope.all(), scope.branchIds(), weekAgo(), freeTags, page.limit(), page.offset()),
                tags.count(scope.all(), scope.branchIds()));
    }

    public TagView get(AdminPrincipal principal, UUID tagId) {
        TagView tag = tags.find(tagId, weekAgo(), freeTags).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
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
            if (tags.uidExists(uid)) {
                throw new ApiException(ErrorCode.ALREADY_EXISTS);
            }
            tags.insertTag(id, branchId, uid, cleanLabel, now);
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
        tags.updateTag(tagId, label == null ? null : cleanLabel(label), branchId, clock.instant());
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
        tags.updateStatus(tagId, enabled ? "ACTIVE" : "DISABLED", enabled ? null : now, now);
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
            tags.deleteUnexportedPendingKeys(tagId);
            insertPendingKeys(tagId, tags.nextKeyVersion(tagId), now);
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
        ProvisioningPackage pkg = Objects.requireNonNull(tx.execute(status -> toPackage(tag, tenantId,
                tags.exportPendingKeys(tagId, clock.instant())
                        .orElseThrow(() -> new ApiException(ErrorCode.PROVISIONING_ALREADY_EXPORTED)))));
        audit.tenant("TAG_PROVISIONING_EXPORTED", AuditLogger.ActorType.PLATFORM, principal.userId(), "tag", tagId,
                Map.of("keyVersion", pkg.keyVersion()));
        return pkg;
    }

    /**
     * Köçürmə paketi: deaktiv çipə sonuncu yazılmış açarlar (sahib + step-up, auditə yazılır). Çipi başqa şirkətə
     * vermək üçündür — yeni şirkətin paketi bununla birlikdə yazılır: {@code ProgramTag yeni.json --old bu.json}.
     * Aktiv çip üçün verilmir: açarlar yalnız çip artıq bu şirkətdə işləmədikdə göstərilir.
     */
    public ProvisioningPackage transferPackage(AdminPrincipal principal, UUID tagId) {
        requirePlatform(principal);
        adminAuth.requireStepUp(principal);
        TagView tag = get(principal, tagId);
        if (!"DISABLED".equals(tag.status())) {
            throw new ApiException(ErrorCode.TAG_NOT_DISABLED);
        }
        ExportedKeys keys = tags.findLastExportedKeys(tagId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        ProvisioningPackage pkg = toPackage(tag, TenantContext.require().id(), keys);
        audit.tenant("TAG_TRANSFER_KEYS_EXPORTED", AuditLogger.ActorType.PLATFORM, principal.userId(), "tag", tagId,
                Map.of("keyVersion", pkg.keyVersion()));
        return pkg;
    }

    private ProvisioningPackage toPackage(TagView tag, UUID tenantId, ExportedKeys keys) {
        {
            UUID keyId = keys.id();
            String k0 = HEX.formatHex(cipher.decrypt(keys.appMasterKeyEnc(),
                    TapVerificationService.keyAad(tenantId, "app_master_key_enc", keyId)));
            String k1 = HEX.formatHex(cipher.decrypt(keys.metaReadKeyEnc(),
                    TapVerificationService.keyAad(tenantId, "meta_read_key_enc", keyId)));
            String k2 = HEX.formatHex(cipher.decrypt(keys.fileReadKeyEnc(),
                    TapVerificationService.keyAad(tenantId, "file_read_key_enc", keyId)));
            String piccPlaceholder = "0".repeat(32);
            String macPlaceholder = "0".repeat(16);
            String url = realms.baseUrl(AdminRealm.STAFF) + "/t/" + tag.uid() + "?picc=" + piccPlaceholder + "&cmac=" + macPlaceholder;
            String withoutScheme = url.substring("https://".length());
            int piccOffset = 7 + withoutScheme.indexOf("picc=") + "picc=".length();
            int macOffset = 7 + withoutScheme.indexOf("cmac=") + "cmac=".length();
            return new ProvisioningPackage(tag.uid(), keys.keyVersion(), url, k0, k1, k2,
                    piccOffset, macOffset, macOffset,
                    "SDM: PICCData K1 ilə şifrəli (UID + sayğac), CMAC K2 ilə (boş giriş). Ətraflı: docs/nfc-ntag424-programming.md");
        }
    }

    /** Çipin son toxunuşları (qəbul və rədd edilənlər, səbəbləri ilə). */
    public PageResult<TapEvent> events(AdminPrincipal principal, UUID tagId, PageQuery page) {
        get(principal, tagId);
        return page.result(tags.events(tagId, page.limit(), page.offset()), tags.countEvents(tagId));
    }

    // ------------------------------------------------------------------

    private static void requirePlatform(AdminPrincipal principal) {
        AccessPolicy.requireAnyRole(principal, AdminRole.SUPER_ADMIN);
    }

    /** Yeni PENDING dəst: 3 təsadüfi AES-128 açarı, tenant-ın açarı ilə şifrəli (AAD sətrə bağlıdır). */
    private void insertPendingKeys(UUID tagId, int version, Instant now) {
        UUID keyId = Ids.newId();
        UUID tenantId = TenantContext.require().id();
        tags.insertPendingKeys(keyId, tagId, version,
                cipher.encrypt(KeyPurpose.NFC_KEYS, randomKey(),
                        TapVerificationService.keyAad(tenantId, "app_master_key_enc", keyId)),
                cipher.encrypt(KeyPurpose.NFC_KEYS, randomKey(),
                        TapVerificationService.keyAad(tenantId, "meta_read_key_enc", keyId)),
                cipher.encrypt(KeyPurpose.NFC_KEYS, randomKey(),
                        TapVerificationService.keyAad(tenantId, "file_read_key_enc", keyId)),
                now);
    }

    private void requireActiveBranch(UUID branchId) {
        if (branchId == null || !tags.activeBranchExists(branchId)) {
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

    private Instant weekAgo() {
        return clock.instant().minus(7, ChronoUnit.DAYS);
    }
}
