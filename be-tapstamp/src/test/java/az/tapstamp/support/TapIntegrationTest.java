package az.tapstamp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.nfc.TapVerificationService;
import az.tapstamp.nfc.sun.Ntag424Sun;
import az.tapstamp.security.admin.AdminAccount;
import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.security.admin.AdminRealms;
import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.security.crypto.KeyPurpose;
import az.tapstamp.security.password.PasswordHasher;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.TenantInfo;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MvcResult;

/**
 * NFC testlərinin bazası: məlum açarlı NTAG 424 çipləri (real SUN kriptoqrafiyası ilə emulyasiya), ayrıca qaydaları
 * olan filiallar (testlər bir-birinin limitlərinə toxunmur) və toxunuş linkləri.
 */
public abstract class TapIntegrationTest extends CustomerIntegrationTest {

    protected static final String STAFF_PASSWORD = "Test-Möhür-Parolu-2026";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    /** Test çipi: K1 və K2 test tərəfindən bilinir. */
    public record TestTag(UUID id, UUID branchId, byte[] uid, byte[] k1, byte[] k2) {
        public String uidHex() {
            return HEX.formatHex(uid);
        }
    }

    /** Filialın sui-istifadə qaydası (standartdan fərqli olanlar). */
    public record Policy(int minIntervalMinutes, int maxStampsPerDay, boolean requireStaffApproval,
                         int pendingTimeoutMinutes, boolean enforceBusinessHours, String businessHours) {
        public static Policy relaxed() {
            return new Policy(0, 50, false, 30, false, null);
        }
    }

    @Autowired
    @Qualifier("tenantCipher")
    protected EnvelopeCipher cipher;

    @Autowired
    protected AdminRealms realms;

    @Autowired
    protected PasswordHasher hasher;

    protected TenantInfo alpha() {
        return directory.findByHost(HOST).orElseThrow();
    }

    /** Bu testdə yaradılan filiallar — test bitəndə bağlanır ki, paketin filial limitini doldurmasın. */
    private final List<UUID> createdBranches = new ArrayList<>();

    @AfterEach
    void closeCreatedBranches() {
        TenantContext.runAs(alpha(), () -> createdBranches.forEach(id -> tenantJdbc
                .sql("UPDATE branches SET status = 'CLOSED' WHERE id = :id").param("id", id).update()));
        createdBranches.clear();
    }

    /** Yeni filial + onun qaydası (çip üzrə toxunuş limiti testlər üçün yüksəkdir). */
    protected UUID newBranch(Policy policy) {
        UUID branchId = Ids.newId();
        createdBranches.add(branchId);
        TenantContext.runAs(alpha(), () -> {
            tenantJdbc.sql("""
                            INSERT INTO branches (id, name, business_hours, status, created_at, updated_at)
                            VALUES (:id, CAST(:name AS jsonb), CAST(:hours AS jsonb), 'ACTIVE', now(), now())""")
                    .param("id", branchId)
                    .param("name", "{\"az\": \"Test filialı " + branchId.toString().substring(0, 8) + "\"}")
                    .param("hours", policy.businessHours())
                    .update();
            tenantJdbc.sql("""
                            INSERT INTO stamp_policies (id, branch_id, min_interval_minutes, max_stamps_per_day,
                                                        require_staff_approval, pending_timeout_minutes,
                                                        enforce_business_hours, max_taps_per_tag_per_minute, updated_at)
                            VALUES (:id, :branchId, :interval, :max, :approval, :timeout, :hours, 600, now())""")
                    .param("id", Ids.newId())
                    .param("branchId", branchId)
                    .param("interval", policy.minIntervalMinutes())
                    .param("max", policy.maxStampsPerDay())
                    .param("approval", policy.requireStaffApproval())
                    .param("timeout", policy.pendingTimeoutMinutes())
                    .param("hours", policy.enforceBusinessHours())
                    .update();
        });
        return branchId;
    }

    protected TestTag newTag(UUID branchId) {
        byte[] uid = new byte[7];
        RANDOM.nextBytes(uid);
        uid[0] = 0x04;
        byte[] k1 = randomKey();
        byte[] k2 = randomKey();
        UUID tagId = Ids.newId();
        TenantContext.runAs(alpha(), () -> {
            tenantJdbc.sql("""
                            INSERT INTO nfc_tags (id, branch_id, uid, type, label, status, created_at, updated_at)
                            VALUES (:id, :branchId, :uid, 'NTAG424_SUN', 'Test çipi', 'ACTIVE', now(), now())""")
                    .param("id", tagId)
                    .param("branchId", branchId)
                    .param("uid", uid)
                    .update();
            insertKeys(tagId, 1, "ACTIVE", k1, k2);
        });
        return new TestTag(tagId, branchId, uid, k1, k2);
    }

    /** Açar rotasiyası: yeni PENDING dəst (çip yeni açarlarla "yenidən proqramlaşdırılır"). */
    protected TestTag rotateKeys(TestTag tag) {
        byte[] k1 = randomKey();
        byte[] k2 = randomKey();
        TenantContext.runAs(alpha(), () -> insertKeys(tag.id(), 2, "PENDING", k1, k2));
        return new TestTag(tag.id(), tag.branchId(), tag.uid(), k1, k2);
    }

    private void insertKeys(UUID tagId, int version, String status, byte[] k1, byte[] k2) {
        UUID keyId = Ids.newId();
        UUID tenantId = alpha().id();
        tenantJdbc.sql("""
                        INSERT INTO nfc_tag_keys (id, tag_id, key_version, status, app_master_key_enc, meta_read_key_enc,
                                                  file_read_key_enc, created_at)
                        VALUES (:id, :tagId, :version, :status, :k0, :k1, :k2, now())""")
                .param("id", keyId)
                .param("tagId", tagId)
                .param("version", version)
                .param("status", status)
                .param("k0", cipher.encrypt(KeyPurpose.NFC_KEYS, randomKey(),
                        TapVerificationService.keyAad(tenantId, "app_master_key_enc", keyId)))
                .param("k1", cipher.encrypt(KeyPurpose.NFC_KEYS, k1,
                        TapVerificationService.keyAad(tenantId, "meta_read_key_enc", keyId)))
                .param("k2", cipher.encrypt(KeyPurpose.NFC_KEYS, k2,
                        TapVerificationService.keyAad(tenantId, "file_read_key_enc", keyId)))
                .update();
    }

    /** Çipin yazdığı link (real NTAG 424 SUN: PICCData şifrəli, CMAC sessiya açarı ilə). */
    protected static String tapPath(TestTag tag, int counter) {
        byte[] padding = new byte[5];
        RANDOM.nextBytes(padding);
        return "/t/" + tag.uidHex() + "?picc=" + HEX.formatHex(Ntag424Sun.encryptPicc(tag.k1(), tag.uid(), counter, padding))
                + "&cmac=" + HEX.formatHex(Ntag424Sun.sdmMac(tag.k2(), tag.uid(), counter));
    }

    /** Toxunuşun yönləndirmə ünvanı ({@code /tap/{token}} və ya {@code /tap/error?code=…}). */
    protected static String tap(CustomerClient client, String path) throws Exception {
        MvcResult result = client.get(path).andExpect(status().isSeeOther()).andReturn();
        return result.getResponse().getHeader("Location");
    }

    /** Uğurlu toxunuşun tokeni. */
    protected static String tapToken(CustomerClient client, TestTag tag, int counter) throws Exception {
        String location = tap(client, tapPath(tag, counter));
        assertThat(location).startsWith("/tap/").doesNotStartWith("/tap/error");
        return location.substring("/tap/".length());
    }

    protected static String claimPath(String token) {
        return "/api/v1/taps/" + token + "/claim";
    }

    protected int count(String sql, Map<String, ?> params) {
        return TenantContext.callAs(alpha(), () -> tenantJdbc.sql(sql).params(params).query(Integer.class).single());
    }

    /** Barista hesabı (TOTP-siz) və onun admin klienti. */
    protected AdminClient signedInBarista(UUID branchId) throws Exception {
        return signedInBarista(HOST, branchId);
    }

    protected AdminClient signedInBarista(String host, UUID branchId) throws Exception {
        return signedInStaff(host, AdminRole.STAFF, branchId);
    }

    /** İstənilən rolda işçi (TOTP-siz; step-up şifrə ilə olur). Sahib üçün {@code branchId} null-dur. */
    protected AdminClient signedInStaff(String host, AdminRole role, UUID branchId) throws Exception {
        TenantInfo tenant = directory.findByHost(host).orElseThrow();
        String email = role.name().toLowerCase(java.util.Locale.ROOT) + "-" + UUID.randomUUID().toString().substring(0, 8)
                + "@example.test";
        TenantContext.runAs(tenant, () -> realms.tx(AdminRealm.STAFF).executeWithoutResult(status -> {
            UUID id = realms.accounts(AdminRealm.STAFF).insert(email, hasher.hash(STAFF_PASSWORD), "Test " + role,
                    Language.AZ, AdminAccount.AccountStatus.ACTIVE, clock.instant());
            tenantJdbc.sql("""
                            INSERT INTO staff_roles (id, staff_user_id, role, branch_id, created_at)
                            VALUES (:id, :userId, :role, :branchId, now())""")
                    .param("id", Ids.newId())
                    .param("userId", id)
                    .param("role", role.name())
                    .param("branchId", branchId)
                    .update();
        }));
        AdminClient client = AdminClient.staff(mvc, host);
        client.login(email, STAFF_PASSWORD);
        return client;
    }

    /** Platforma admini (TOTP-siz; step-up şifrə ilə olur). */
    protected AdminClient signedInPlatformAdmin() throws Exception {
        String email = "super-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        realms.tx(AdminRealm.PLATFORM).executeWithoutResult(status -> realms.accounts(AdminRealm.PLATFORM)
                .insert(email, hasher.hash(STAFF_PASSWORD), "Platform Test", Language.AZ,
                        AdminAccount.AccountStatus.ACTIVE, clock.instant()));
        AdminClient client = AdminClient.platform(mvc);
        client.login(email, STAFF_PASSWORD);
        return client;
    }

    private static byte[] randomKey() {
        byte[] key = new byte[16];
        RANDOM.nextBytes(key);
        return key;
    }
}
