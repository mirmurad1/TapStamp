package az.cafeloyalty.dev.seed;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.cafeloyalty.config.AppProperties;
import az.cafeloyalty.security.admin.AdminAccount;
import az.cafeloyalty.security.admin.AdminAccountStore;
import az.cafeloyalty.security.admin.AdminRealm;
import az.cafeloyalty.security.admin.AdminRealms;
import az.cafeloyalty.security.admin.AdminRole;
import az.cafeloyalty.security.crypto.Aad;
import az.cafeloyalty.security.crypto.EnvelopeCipher;
import az.cafeloyalty.security.crypto.KeyPurpose;
import az.cafeloyalty.security.crypto.PhoneHasher;
import az.cafeloyalty.security.password.PasswordHasher;
import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.shared.id.Ids;
import az.cafeloyalty.shared.pii.PhoneMasker;
import az.cafeloyalty.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Dev test hesabları (README-də göstərilir). İdempotentdir: email artıq varsa hesab yenidən yaradılmır.
 * Sahib və platforma admini üçün TOTP məlum dev sirri ilə qurulur ki, autentifikator tətbiqinə əlavə etmək mümkün olsun;
 * Demo Çay Evinin sahibində TOTP yoxdur — ilk girişdə qeydiyyat axını göstərilir.
 */
@Component
@Profile("dev")
public class DevAccountSeeder {

    /** Dev TOTP sirri (20 bayt, Base32: {@code MNQWMZJNNRXXSYLMOR4S2ZDFOYWWWZLZ}). Yalnız dev. */
    public static final byte[] DEV_TOTP_SECRET = "cafe-loyalty-dev-key".getBytes(StandardCharsets.US_ASCII);

    public static final String PLATFORM_ADMIN_EMAIL = "superadmin@platform.localhost";

    /** Qayıdan müştəri demosu (Mock Coffee House): mövcud seed müştərilərindən birinə verilən məlum nömrə. */
    public static final String DEMO_CUSTOMER_PHONE = "+994501234567";

    record AccountSeed(String email, String displayName, Language language, AdminRole role, String branchNameAz,
                       boolean totp) {
    }

    static final Map<String, List<AccountSeed>> STAFF = Map.of(
            "mockcoffee", List.of(
                    new AccountSeed("owner@mockcoffee.az", "Leyla Məmmədova", Language.AZ, AdminRole.COMPANY_OWNER, null, true),
                    new AccountSeed("manager@mockcoffee.az", "Rəşad Əliyev", Language.AZ, AdminRole.BRANCH_MANAGER,
                            "Gənclik filialı", false),
                    new AccountSeed("barista@mockcoffee.az", "Anna Petrova", Language.RU, AdminRole.STAFF,
                            "Mərkəz filialı", false)),
            "democay", List.of(
                    new AccountSeed("owner@democay.az", "Elçin Quliyev", Language.AZ, AdminRole.COMPANY_OWNER, null, false)));

    private static final Logger log = LoggerFactory.getLogger(DevAccountSeeder.class);

    private final AdminRealms realms;
    private final PasswordHasher hasher;
    private final JdbcClient tenantJdbc;
    private final AppProperties properties;
    private final EnvelopeCipher tenantCipher;
    private final PhoneHasher phoneHasher;
    private final Clock clock;

    public DevAccountSeeder(AdminRealms realms, PasswordHasher hasher, @Qualifier("tenantJdbc") JdbcClient tenantJdbc,
                            AppProperties properties, @Qualifier("tenantCipher") EnvelopeCipher tenantCipher,
                            PhoneHasher phoneHasher, Clock clock) {
        this.realms = realms;
        this.hasher = hasher;
        this.tenantJdbc = tenantJdbc;
        this.properties = properties;
        this.tenantCipher = tenantCipher;
        this.phoneHasher = phoneHasher;
        this.clock = clock;
    }

    /**
     * Mock Coffee House-da {@link #DEMO_CUSTOMER_PHONE} nömrəsini kartında bir neçə möhür olan mövcud seed müştərisinə
     * verir (yeni müştəri yaradılmır — seed-in tarixçəsi və saylar dəyişmir). İdempotentdir.
     */
    public void seedDemoCustomer(String slug) {
        if (!"mockcoffee".equals(slug)) {
            return;
        }
        UUID tenantId = TenantContext.require().id();
        byte[] phoneHash = phoneHasher.hash(tenantId, DEMO_CUSTOMER_PHONE);
        boolean exists = tenantJdbc.sql("SELECT count(*) FROM customers WHERE phone_hash = :hash")
                .param("hash", phoneHash).query(Integer.class).single() > 0;
        if (exists) {
            return;
        }
        tenantJdbc.sql("""
                        SELECT c.id FROM customers c JOIN loyalty_accounts la ON la.customer_id = c.id
                        WHERE c.status = 'ACTIVE' AND la.current_stamps BETWEEN 4 AND 7
                        ORDER BY la.total_stamps DESC, c.id LIMIT 1""")
                .query(UUID.class)
                .optional()
                .ifPresent(id -> {
                    tenantJdbc.sql("""
                                    UPDATE customers SET phone_enc = :enc, phone_hash = :hash, phone_masked = :masked
                                    WHERE id = :id""")
                            .param("enc", tenantCipher.encryptString(KeyPurpose.PII, DEMO_CUSTOMER_PHONE,
                                    Aad.tenantField(tenantId, "customers", "phone_enc", id)))
                            .param("hash", phoneHash)
                            .param("masked", PhoneMasker.mask(DEMO_CUSTOMER_PHONE))
                            .param("id", id)
                            .update();
                    log.info("Dev demo müştərisi: {} → {}", PhoneMasker.mask(DEMO_CUSTOMER_PHONE), id);
                });
    }

    /** Cari tenant-ın (TenantContext) işçiləri. */
    public void seedStaff(String slug) {
        for (AccountSeed seed : STAFF.getOrDefault(slug, List.of())) {
            AdminAccountStore accounts = realms.accounts(AdminRealm.STAFF);
            if (accounts.findByEmail(seed.email()).isPresent()) {
                continue;
            }
            Instant now = clock.instant();
            UUID branchId = seed.branchNameAz() == null ? null : tenantJdbc.sql(
                            "SELECT id FROM branches WHERE name ->> 'az' = :name")
                    .param("name", seed.branchNameAz())
                    .query(UUID.class)
                    .single();
            realms.tx(AdminRealm.STAFF).executeWithoutResult(status -> {
                UUID id = accounts.insert(seed.email(), hasher.hash(password()), seed.displayName(), seed.language(),
                        AdminAccount.AccountStatus.ACTIVE, now);
                tenantJdbc.sql("""
                                INSERT INTO staff_roles (id, staff_user_id, role, branch_id, created_at)
                                VALUES (:id, :userId, :role, :branchId, now())""")
                        .param("id", Ids.newId())
                        .param("userId", id)
                        .param("role", seed.role().name())
                        .param("branchId", branchId)
                        .update();
                if (seed.totp()) {
                    enableDevTotp(AdminRealm.STAFF, id, now);
                }
            });
            log.info("Dev hesabı yaradıldı: {} ({})", seed.email(), seed.role());
        }
    }

    public void seedPlatformAdmin() {
        AdminAccountStore accounts = realms.accounts(AdminRealm.PLATFORM);
        if (accounts.findByEmail(PLATFORM_ADMIN_EMAIL).isPresent()) {
            return;
        }
        Instant now = clock.instant();
        realms.tx(AdminRealm.PLATFORM).executeWithoutResult(status -> {
            UUID id = accounts.insert(PLATFORM_ADMIN_EMAIL, hasher.hash(password()), "Platforma Admini", Language.AZ,
                    AdminAccount.AccountStatus.ACTIVE, now);
            enableDevTotp(AdminRealm.PLATFORM, id, now);
        });
        log.info("Dev platforma admini yaradıldı: {}", PLATFORM_ADMIN_EMAIL);
    }

    private void enableDevTotp(AdminRealm realm, UUID userId, Instant now) {
        byte[] encrypted = realms.cipher(realm).encrypt(KeyPurpose.TOTP, DEV_TOTP_SECRET, realms.totpAad(realm, userId));
        realms.accounts(realm).storePendingTotp(userId, encrypted, now);
        realms.accounts(realm).enableTotp(userId, 0, now);
    }

    private String password() {
        String password = properties.dev().seedAdminPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("app.dev.seed-admin-password is not set");
        }
        return password;
    }
}
