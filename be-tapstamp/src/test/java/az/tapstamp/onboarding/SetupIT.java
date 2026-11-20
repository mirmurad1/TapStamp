package az.tapstamp.onboarding;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.TapIntegrationTest;
import az.tapstamp.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Başlanğıc siyahısı: 5 məcburi addım mövcud məlumatdan hesablanır (filial var, iş saatı yoxdursa — yarımçıq; loqo bir
 * dəfə yüklənibsə, silinsə də tamamlanmış qalır; filiallar bağlansa, addım yenidən açılır),
 * tövsiyələr paketə görədir və gizlədilə bilər; yalnız sahib görür; platforma şirkətin vəziyyətini görür.
 */
class SetupIT extends TapIntegrationTest {

    @AfterEach
    void showRecommendations() {
        TenantContext.runAs(alpha(), () -> tenantJdbc.sql("UPDATE setup_state SET recommendations_hidden_at = NULL")
                .update());
    }

    private AdminClient owner() throws Exception {
        return signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
    }

    @Test
    void freshCompanyWithNothingStoredYetGetsTheChecklist() throws Exception {
        // Yeni şirkət: setup_state sətrinin bütün sahələri boşdur
        TenantContext.runAs(alpha(), () -> tenantJdbc.sql(
                "UPDATE setup_state SET recommendations_hidden_at = NULL, branding_done_at = NULL").update());
        signedInPlatformAdmin().get("/tenants/" + alpha().id() + "/setup").andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationsHidden").value(false));
        TenantContext.runAs(alpha(), () -> tenantJdbc.sql(
                "UPDATE setup_state SET recommendations_hidden_at = NULL, branding_done_at = NULL").update());
        owner().get("/setup").andExpect(status().isOk());
    }

    @Test
    void requiredStepsComeFromExistingDataAndRecommendationsCanBeHidden() throws Exception {
        AdminClient owner = owner();
        owner.get("/setup").andExpect(status().isOk())
                .andExpect(jsonPath("$.required.length()").value(5))
                .andExpect(jsonPath("$.required[0].key").value("BRANDING"))
                .andExpect(jsonPath("$.required[1].key").value("REWARD_RULE"))
                .andExpect(jsonPath("$.required[2].key").value("BRANCH"))
                .andExpect(jsonPath("$.required[3].key").value("TEAM"))
                .andExpect(jsonPath("$.required[4].key").value("NFC_TAG"))
                .andExpect(jsonPath("$.recommended[?(@.key == 'GOOGLE_REVIEW')]").exists())
                .andExpect(jsonPath("$.recommendationsHidden").value(false));

        // Filial var, iş saatı yoxdur — addım yarımçıqdır
        UUID branch = TenantContext.callAs(alpha(), () -> tenantJdbc.sql(
                "SELECT id FROM branches WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1").query(UUID.class).single());
        Map<UUID, String> hours = new java.util.HashMap<>();
        TenantContext.runAs(alpha(), () -> tenantJdbc.sql(
                        "SELECT id, business_hours::text AS h FROM branches WHERE status = 'ACTIVE'")
                .query((rs, row) -> hours.put(UUID.fromString(rs.getString("id")), rs.getString("h"))).list());
        try {
            TenantContext.runAs(alpha(), () -> tenantJdbc.sql("UPDATE branches SET business_hours = NULL").update());
            owner.get("/setup").andExpect(jsonPath("$.required[2].done").value(false))
                    .andExpect(jsonPath("$.required[2].partial").value(true))
                    .andExpect(jsonPath("$.requiredComplete").value(false));
            TenantContext.runAs(alpha(), () -> tenantJdbc.sql(
                            "UPDATE branches SET business_hours = CAST(:h AS jsonb) WHERE id = :id")
                    .params(Map.of("h", "{\"mon\":[[\"09:00\",\"18:00\"]]}", "id", branch)).update());
            owner.get("/setup").andExpect(jsonPath("$.required[2].done").value(true))
                    .andExpect(jsonPath("$.required[2].partial").value(false));
        } finally {
            TenantContext.runAs(alpha(), () -> hours.forEach((id, h) -> tenantJdbc.sql(
                            "UPDATE branches SET business_hours = CAST(:h AS jsonb) WHERE id = :id")
                    .param("h", h).param("id", id).update()));
        }

        // Loqo: ilk yüklənəndə tamamlanır, sonradan silinsə də tamamlanmış qalır
        byte[] logo = TenantContext.callAs(alpha(), () -> tenantJdbc.sql("SELECT logo FROM company_profile")
                .query(byte[].class).optional().orElse(null));
        try {
            TenantContext.runAs(alpha(), () -> {
                tenantJdbc.sql("UPDATE company_profile SET logo = NULL").update();
                tenantJdbc.sql("UPDATE setup_state SET branding_done_at = NULL").update();
            });
            owner.get("/setup").andExpect(jsonPath("$.required[0].done").value(false));
            TenantContext.runAs(alpha(), () -> tenantJdbc.sql("UPDATE company_profile SET logo = :logo")
                    .param("logo", new byte[] {1, 2, 3}).update());
            owner.get("/setup").andExpect(jsonPath("$.required[0].done").value(true));
            TenantContext.runAs(alpha(), () -> tenantJdbc.sql("UPDATE company_profile SET logo = NULL").update());
            owner.get("/setup").andExpect(jsonPath("$.required[0].done").value(true));
        } finally {
            TenantContext.runAs(alpha(), () -> tenantJdbc.sql("UPDATE company_profile SET logo = :logo")
                    .param("logo", logo).update());
        }

        // Bütün filiallar bağlansa, filial addımı yenidən açıq olur (canlı hesablanır)
        java.util.List<UUID> active = TenantContext.callAs(alpha(), () -> tenantJdbc.sql(
                "SELECT id FROM branches WHERE status = 'ACTIVE'").query(UUID.class).list());
        try {
            TenantContext.runAs(alpha(), () -> tenantJdbc.sql("UPDATE branches SET status = 'CLOSED'").update());
            owner.get("/setup").andExpect(jsonPath("$.required[2].done").value(false))
                    .andExpect(jsonPath("$.required[2].partial").value(false))
                    .andExpect(jsonPath("$.requiredComplete").value(false));
        } finally {
            TenantContext.runAs(alpha(), () -> tenantJdbc.sql("UPDATE branches SET status = 'ACTIVE' WHERE id IN (:ids)")
                    .param("ids", active).update());
        }

        // Tövsiyələr gizlədilir (məcburi addımlar yenə qaytarılır)
        owner.put("/setup/recommendations", Map.of("hidden", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationsHidden").value(true))
                .andExpect(jsonPath("$.required.length()").value(5));
        owner.put("/setup/recommendations", Map.of("hidden", false))
                .andExpect(jsonPath("$.recommendationsHidden").value(false));
    }

    @Test
    void onlyTheOwnerSeesTheChecklistAndThePlatformSeesEveryCompany() throws Exception {
        UUID branch = TenantContext.callAs(alpha(), () -> tenantJdbc.sql(
                "SELECT id FROM branches WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1").query(UUID.class).single());
        signedInStaff(HOST, AdminRole.BRANCH_MANAGER, branch).get("/setup").andExpect(status().isForbidden());

        signedInPlatformAdmin().get("/tenants/" + alpha().id() + "/setup").andExpect(status().isOk())
                .andExpect(jsonPath("$.required.length()").value(5))
                // Sahibdən başqa işçi var (yuxarıdakı menecer); test şirkətində qeydiyyatdan keçmiş çip var
                .andExpect(jsonPath("$.required[3].done").value(true))
                .andExpect(jsonPath("$.required[4].key").value("NFC_TAG"));
    }
}
