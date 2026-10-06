package az.cafeloyalty.support;

import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.shared.i18n.LocalizedText;
import az.cafeloyalty.shared.id.Ids;
import az.cafeloyalty.tenancy.TenantContext;
import az.cafeloyalty.tenancy.provisioning.ProvisionTenantCommand;
import az.cafeloyalty.tenancy.provisioning.TenantProvisioner;
import az.cafeloyalty.tenancy.registry.TenantRecord;
import org.springframework.jdbc.core.simple.JdbcClient;

/** İnteqrasiya testlərinin iki şirkəti: izolyasiya testləri onların arasında aparılır. */
public final class TestTenants {

    public static final String ALPHA_HOST = "alpha.test";
    public static final String BETA_HOST = "beta.test";

    private TestTenants() {
    }

    public static void provisionAll(TenantProvisioner provisioner, JdbcClient tenantJdbc) {
        provision(provisioner, tenantJdbc, "alpha", "Alpha Kafe", ALPHA_HOST);
        provision(provisioner, tenantJdbc, "beta", "Beta Kafe", BETA_HOST);
    }

    private static void provision(TenantProvisioner provisioner, JdbcClient tenantJdbc, String slug, String name,
                                  String host) {
        TenantRecord tenant = provisioner.provision(new ProvisionTenantCommand(
                slug, LocalizedText.of(name, name + " EN", name + " RU"), host, Language.AZ, "Asia/Baku"));
        TenantContext.runAs(tenant.toInfo(), () -> {
            Integer branches = tenantJdbc.sql("SELECT count(*) FROM branches").query(Integer.class).single();
            if (branches == 0) {
                tenantJdbc.sql("""
                                INSERT INTO branches (id, name, status, created_at, updated_at)
                                VALUES (:id, CAST(:name AS jsonb), 'ACTIVE', now(), now())""")
                        .param("id", Ids.newId())
                        .param("name", "{\"az\": \"" + name + " — Mərkəz\"}")
                        .update();
            }
        });
    }
}
