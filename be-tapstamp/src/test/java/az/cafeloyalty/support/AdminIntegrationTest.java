package az.cafeloyalty.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import az.cafeloyalty.security.admin.AdminAccount;
import az.cafeloyalty.security.admin.AdminRealm;
import az.cafeloyalty.security.admin.AdminRealms;
import az.cafeloyalty.security.admin.AdminRole;
import az.cafeloyalty.security.crypto.KeyPurpose;
import az.cafeloyalty.security.password.PasswordHasher;
import az.cafeloyalty.security.totp.TotpCodes;
import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.shared.id.Ids;
import az.cafeloyalty.tenancy.TenantContext;
import az.cafeloyalty.tenancy.TenantInfo;
import az.cafeloyalty.tenancy.registry.TenantDirectory;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Admin autentifikasiyası testlərinin bazası: hesabların birbaşa bazada yaradılması, TOTP kodları, saat. */
public abstract class AdminIntegrationTest extends IntegrationTest {

    protected static final String PASSWORD = "Test-Qəhvə-Parolu-2026";
    protected static final byte[] TOTP_SECRET = "integration-test-key".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected AdminRealms realms;

    @Autowired
    protected PasswordHasher hasher;

    @Autowired
    protected TenantDirectory directory;

    @Autowired
    @Qualifier("tenantJdbc")
    protected JdbcClient tenantJdbc;

    @Autowired
    @Qualifier("platformJdbc")
    protected JdbcClient platformJdbc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected TestMailbox mailbox;

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    }

    protected TenantInfo tenant(String host) {
        return directory.findByHost(host).orElseThrow();
    }

    /** Şirkətin ilk filialı (test şirkətlərində bir filial var). */
    protected UUID branchOf(String host) {
        return TenantContext.callAs(tenant(host), () -> tenantJdbc.sql("SELECT id FROM branches ORDER BY created_at LIMIT 1")
                .query(UUID.class).single());
    }

    protected UUID addBranch(String host, String nameAz) {
        UUID id = Ids.newId();
        TenantContext.runAs(tenant(host), () -> tenantJdbc.sql("""
                        INSERT INTO branches (id, name, status, created_at, updated_at)
                        VALUES (:id, CAST(:name AS jsonb), 'ACTIVE', now(), now())""")
                .param("id", id)
                .param("name", "{\"az\": \"" + nameAz + "\"}")
                .update());
        return id;
    }

    /** İşçi hesabı: totp=true olarsa {@link #TOTP_SECRET} ilə TOTP aktivdir. */
    protected UUID createStaff(String host, String email, AdminRole role, UUID branchId, boolean totp) {
        return TenantContext.callAs(tenant(host), () -> realms.tx(AdminRealm.STAFF).execute(status -> {
            UUID id = realms.accounts(AdminRealm.STAFF).insert(email, hasher.hash(PASSWORD), "Test " + role,
                    Language.AZ, AdminAccount.AccountStatus.ACTIVE, clock.instant());
            tenantJdbc.sql("""
                            INSERT INTO staff_roles (id, staff_user_id, role, branch_id, created_at)
                            VALUES (:id, :userId, :role, :branchId, now())""")
                    .param("id", Ids.newId())
                    .param("userId", id)
                    .param("role", role.name())
                    .param("branchId", branchId)
                    .update();
            if (totp) {
                enableTotp(AdminRealm.STAFF, id);
            }
            return id;
        }));
    }

    protected UUID createPlatformAdmin(String email, boolean totp) {
        return realms.tx(AdminRealm.PLATFORM).execute(status -> {
            UUID id = realms.accounts(AdminRealm.PLATFORM).insert(email, hasher.hash(PASSWORD), "Platform Test",
                    Language.AZ, AdminAccount.AccountStatus.ACTIVE, clock.instant());
            if (totp) {
                enableTotp(AdminRealm.PLATFORM, id);
            }
            return id;
        });
    }

    private void enableTotp(AdminRealm realm, UUID id) {
        byte[] encrypted = realms.cipher(realm).encrypt(KeyPurpose.TOTP, TOTP_SECRET, realms.totpAad(realm, id));
        realms.accounts(realm).storePendingTotp(id, encrypted, clock.instant());
        realms.accounts(realm).enableTotp(id, 0, clock.instant());
    }

    /**
     * Yeni zaman addımının TOTP kodu. Saat 30 saniyə irəli çəkilir: eyni addımın kodu ikinci dəfə qəbul olunmur
     * (təkrar istifadənin qarşısı), real autentifikator tətbiqində də növbəti kodu gözləmək lazımdır.
     */
    protected String freshTotp(byte[] secret) {
        clock.advance(Duration.ofSeconds(30));
        return TotpCodes.at(secret, clock.instant());
    }

    protected static String json(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }

    /** Tam daxil olmuş işçi (TOTP varsa MFA addımı da keçilir). */
    protected AdminClient signedInStaff(String host, String email, boolean totp) throws Exception {
        AdminClient client = AdminClient.staff(mvc, host);
        client.login(email, PASSWORD);
        if (totp) {
            client.post("/auth/mfa/verify", Map.of("code", freshTotp(TOTP_SECRET)));
        }
        return client;
    }

    protected AdminClient signedInPlatformAdmin(String email) throws Exception {
        AdminClient client = AdminClient.platform(mvc);
        client.login(email, PASSWORD);
        client.post("/auth/mfa/verify", Map.of("code", freshTotp(TOTP_SECRET)));
        return client;
    }
}
