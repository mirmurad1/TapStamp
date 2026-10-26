package az.tapstamp.support;

import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.provisioning.ProvisionTenantCommand;
import az.tapstamp.tenancy.provisioning.TenantProvisioner;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.springframework.jdbc.core.simple.JdbcClient;

/** İnteqrasiya testlərinin iki şirkəti: izolyasiya testləri onların arasında aparılır. */
public final class TestTenants {

    public static final String ALPHA_HOST = "alpha.test";
    public static final String BETA_HOST = "beta.test";
    /** Daxili endpoint-lərin (TLS domen siyahısı) test sirri. */
    public static final String INTERNAL_TOKEN = "test-internal-token-0123456789";
    /** Şirkət saytının domeni və demo müraciətlərinin poçt qutusu. */
    public static final String SITE_HOST = "site.test";
    public static final String SITE_INBOX = "info@site.test";
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
            // Hüquqi sənədləri provisioning özü yazır (DefaultLegalDocuments); mükafat qaydası — möhür kartı üçün
            Integer rules = tenantJdbc.sql("SELECT count(*) FROM reward_rules").query(Integer.class).single();
            if (rules == 0) {
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
