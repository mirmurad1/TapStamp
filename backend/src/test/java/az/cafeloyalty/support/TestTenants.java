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
    /** Platforma panelinin domeni (app.platform-domain standart dəyəri). */
    public static final String PLATFORM_HOST = "platform.localhost";

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
            // Hüquqi sənədlər (qeydiyyatda razılıq tələb olunur) və mükafat qaydası (möhür kartı üçün)
            Integer documents = tenantJdbc.sql("SELECT count(*) FROM legal_documents").query(Integer.class).single();
            if (documents == 0) {
                for (String type : new String[] {"PRIVACY_POLICY", "TERMS"}) {
                    tenantJdbc.sql("""
                                    INSERT INTO legal_documents (id, type, doc_version, content, published_at, is_current)
                                    VALUES (:id, :type, 1, CAST(:content AS jsonb), now(), true)""")
                            .param("id", Ids.newId())
                            .param("type", type)
                            .param("content", "{\"az\": \"" + type + " v1\", \"en\": \"" + type + " v1 EN\"}")
                            .update();
                }
                tenantJdbc.sql("""
                                INSERT INTO reward_rules (id, rule_version, stamps_required, reward_name, active, created_at)
                                VALUES (:id, 1, 9, CAST(:name AS jsonb), true, now())""")
                        .param("id", Ids.newId())
                        .param("name", "{\"az\": \"Hədiyyə qəhvə\", \"en\": \"Free coffee\"}")
                        .update();
            }
        });
    }
}
