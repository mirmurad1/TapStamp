package az.cafeloyalty.tenancy.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.shared.i18n.LocalizedText;
import az.cafeloyalty.support.IntegrationTest;
import az.cafeloyalty.tenancy.TenantContext;
import az.cafeloyalty.tenancy.TenantStatus;
import az.cafeloyalty.tenancy.registry.TenantDirectory;
import az.cafeloyalty.tenancy.registry.TenantRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;

class TenantProvisioningIT extends IntegrationTest {

    @Autowired
    TenantProvisioner provisioner;

    @Autowired
    TenantDirectory directory;

    @Autowired
    @Qualifier("tenantJdbc")
    JdbcClient tenantJdbc;

    @Autowired
    @Qualifier("platformJdbc")
    JdbcClient platformJdbc;

    private static ProvisionTenantCommand command(String slug, String domain) {
        return new ProvisionTenantCommand(slug, LocalizedText.of("Gamma Kafe", "Gamma Cafe", "Гамма Кафе"),
                domain, Language.AZ, "Asia/Baku");
    }

    @Test
    void provisioningIsIdempotent() {
        TenantRecord first = provisioner.provision(command("gamma", "gamma.test"));
        TenantRecord second = provisioner.provision(command("gamma", "gamma.test"));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(directory.hostnames(first.id())).containsExactly("gamma.test");
        assertThat(directory.primaryHost(first.id())).contains("gamma.test");
    }

    @Test
    void interruptedProvisioningResumes() {
        TenantRecord tenant = provisioner.provision(command("theta", "theta.test"));
        // Yarımçıq qalmış provisioning-i simulyasiya edirik: rol, baza və miqrasiyalar artıq mövcuddur
        directory.updateStatus(tenant.id(), TenantStatus.PROVISIONING, null);

        TenantRecord resumed = provisioner.provision(command("theta", "theta.test"));

        assertThat(resumed.status()).isEqualTo(TenantStatus.ACTIVE);
        Integer profiles = TenantContext.callAs(resumed.toInfo(),
                () -> tenantJdbc.sql("SELECT count(*) FROM company_profile").query(Integer.class).single());
        assertThat(profiles).isEqualTo(1);
    }

    @Test
    void newTenantGetsProfileDefaultPolicyAndDataKeys() throws IOException {
        TenantRecord tenant = provisioner.provision(command("kappa", "kappa.test"));
        TenantContext.runAs(tenant.toInfo(), () -> {
            assertThat(tenantJdbc.sql("SELECT name->>'ru' FROM company_profile").query(String.class).single())
                    .isEqualTo("Гамма Кафе");
            assertThat(tenantJdbc.sql("SELECT count(*) FROM stamp_policies WHERE branch_id IS NULL")
                    .query(Integer.class).single()).isEqualTo(1);
            assertThat(tenantJdbc.sql("SELECT purpose FROM data_encryption_keys WHERE status = 'ACTIVE' ORDER BY purpose")
                    .query(String.class).list()).containsExactly("NFC_KEYS", "PII", "TOTP");
        });
        assertThat(platformJdbc.sql("SELECT schema_version FROM tenants WHERE id = :id")
                .param("id", tenant.id()).query(String.class).single()).isEqualTo(latestTenantMigration());
    }

    @Test
    void domainCannotBeTakenFromAnotherTenant() {
        assertThatThrownBy(() -> provisioner.provision(command("lambda", "alpha.test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another tenant");
    }

    @Test
    void invalidInputIsRejected() {
        assertThatThrownBy(() -> provisioner.provision(command("Bad Slug", "bad.test")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provisioner.provision(command("goodslug", "no_tld")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Ən son tenant miqrasiyasının versiyası (məs. V3__customer_auth.sql → "3"). */
    private static String latestTenantMigration() throws IOException {
        Pattern version = Pattern.compile("^V(\\d+)__");
        return String.valueOf(Arrays.stream(new PathMatchingResourcePatternResolver()
                        .getResources("classpath:db/migration/tenant/V*__*.sql"))
                .map(resource -> version.matcher(resource.getFilename() == null ? "" : resource.getFilename()))
                .filter(Matcher::find)
                .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                .max()
                .orElseThrow());
    }
}
