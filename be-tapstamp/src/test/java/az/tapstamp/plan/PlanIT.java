package az.tapstamp.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.TapIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Paketlər: platforma panelindən dəyişmə, hər paket üçün bir dəfə sınaq, imkanların və limitlərin bağlanması,
 * sınaq bitəndə şirkətin xidmətinin dayandırılması.
 */
class PlanIT extends TapIntegrationTest {

    @Autowired
    @Qualifier("platformJdbc")
    private JdbcClient platformJdbc;

    @Autowired
    private PlanService plans;

    @AfterEach
    void restoreLegacyPlan() {
        platformJdbc.sql("DELETE FROM tenant_subscriptions WHERE tenant_id = :id").param("id", alpha().id()).update();
        platformJdbc.sql("DELETE FROM tenant_plan_trials WHERE tenant_id = :id").param("id", alpha().id()).update();
        platformJdbc.sql("DELETE FROM tenant_addons WHERE tenant_id = :id").param("id", alpha().id()).update();
        plans.evictCaches();
    }

    @Test
    void addonsAreSoldSeparatelyFromThePlanAndCloseWithAnExpiredTrial() throws Exception {
        AdminClient admin = signedInPlatformAdmin();
        String addons = planPath() + "/addons";
        admin.get(addons).andExpect(status().isOk()).andExpect(jsonPath("$.addons").isEmpty());
        client().get("/api/v1/public/plan").andExpect(jsonPath("$.addons").isEmpty());

        // Basic paketdə də açıla bilər (paketdən asılı deyil); təkrar açmaq audit yazmır
        setPlan(admin, "BASIC", "TRIAL", 0);
        admin.put(addons + "/CATALOG", Map.of("enabled", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.addons[0]").value("CATALOG"));
        admin.put(addons + "/CATALOG", Map.of("enabled", true)).andExpect(status().isOk());
        client().get("/api/v1/public/plan").andExpect(jsonPath("$.addons[0]").value("CATALOG"));
        assertThat(platformJdbc.sql("SELECT count(*) FROM platform_audit_logs WHERE action = 'TENANT_ADDON_ENABLED' AND target_id = :id")
                .param("id", alpha().id().toString()).query(Integer.class).single()).isEqualTo(1);
        admin.put(addons + "/UNKNOWN", Map.of("enabled", true)).andExpect(status().isBadRequest());

        // Sınaq bitəndə əlavələr də bağlanır (platforma panelində isə qalır)
        clock.advance(Duration.ofDays(Plan.TRIAL_DAYS));
        plans.evictCaches();
        client().get("/api/v1/public/plan").andExpect(jsonPath("$.expired").value(true))
                .andExpect(jsonPath("$.addons").isEmpty());
        // Sınaq bitəndə platforma adminin sessiyası da bitib
        admin = signedInPlatformAdmin();
        admin.get(addons).andExpect(jsonPath("$.addons[0]").value("CATALOG"));

        setPlan(admin, "BASIC", "ACTIVE", 0);
        admin.put(addons + "/CATALOG", Map.of("enabled", false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.addons").isEmpty());
        client().get("/api/v1/public/plan").andExpect(jsonPath("$.addons").isEmpty());
    }

    private String planPath() {
        return "/tenants/" + alpha().id() + "/plan";
    }

    private void setPlan(AdminClient admin, String plan, String status, int extraBranches) throws Exception {
        admin.put(planPath(), Map.of("plan", plan, "status", status, "extraBranches", extraBranches))
                .andExpect(status().isOk());
    }

    @Test
    void platformStartsEachPlansTrialOnceAndRecordsPayment() throws Exception {
        AdminClient admin = signedInPlatformAdmin();
        // Paketlərdən əvvəlki şirkət — Premium, ödənişli
        admin.get(planPath()).andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM")).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.trials").isEmpty());

        String body = admin.put(planPath(), Map.of("plan", "BASIC", "status", "TRIAL", "extraBranches", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAL")).andExpect(jsonPath("$.expired").value(false))
                .andExpect(jsonPath("$.branchLimit").value(1)).andExpect(jsonPath("$.staffLimit").value(3))
                .andReturn().getResponse().getContentAsString();
        Instant ends = Instant.parse(JsonPath.read(body, "$.trialEndsAt"));
        // Baza vaxtı mikrosaniyəyə qədər saxlayır
        assertThat(Duration.between(clock.instant(), ends)).isBetween(Duration.ofDays(Plan.TRIAL_DAYS).minusSeconds(1), Duration.ofDays(Plan.TRIAL_DAYS));

        // Davam edən sınaqda eyni paket — müddət dəyişmir; başqa paketin sınağı ayrıca
        clock.advance(Duration.ofMinutes(5));
        admin.put(planPath(), Map.of("plan", "BASIC", "status", "TRIAL", "extraBranches", 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trialEndsAt").value(JsonPath.<String>read(body, "$.trialEndsAt")));
        setPlan(admin, "STANDARD", "TRIAL", 0);
        // Basic-in sınağı artıq istifadə olunub
        admin.put(planPath(), Map.of("plan", "BASIC", "status", "TRIAL", "extraBranches", 0))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_TRIAL_ALREADY_USED"));
        admin.get(planPath()).andExpect(jsonPath("$.trials.length()").value(2));

        // Ödəniş: Premium + 2 əlavə filial; əlavə filial yalnız Premium-da
        admin.put(planPath(), Map.of("plan", "PREMIUM", "status", "ACTIVE", "extraBranches", 2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.branchLimit").value(7))
                .andExpect(jsonPath("$.trialEndsAt").doesNotExist());
        admin.put(planPath(), Map.of("plan", "BASIC", "status", "ACTIVE", "extraBranches", 1))
                .andExpect(status().isBadRequest());
        admin.get("/tenants/" + alpha().id()).andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.planStatus").value("ACTIVE"));
        assertThat(platformJdbc.sql("SELECT count(*) FROM platform_audit_logs WHERE action = 'TENANT_PLAN_CHANGED' AND target_id = :id")
                .param("id", alpha().id().toString()).query(Integer.class).single()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void featuresOutsideThePlanAreLocked() throws Exception {
        AdminClient admin = signedInPlatformAdmin();
        setPlan(admin, "BASIC", "ACTIVE", 0);
        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);

        owner.get("/audit-logs").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FEATURE_UNAVAILABLE"));
        owner.get("/reminders/settings").andExpect(status().isForbidden());
        owner.get("/referrals/settings").andExpect(status().isForbidden());
        owner.get("/analytics/overview").andExpect(status().isOk());
        owner.get("/analytics/heatmap").andExpect(status().isForbidden());
        owner.post("/customers/export", Map.of()).andExpect(status().isForbidden());
        owner.post("/campaigns/preview", Map.of("type", "STAMPS", "audience", "EMPTY_CARD", "stampCount", 1, "text", "Salam"))
                .andExpect(status().isForbidden());
        client().get("/api/v1/public/push").andExpect(jsonPath("$.enabled").value(false));
        client().get("/api/v1/public/referral-program").andExpect(jsonPath("$.enabled").value(false));
        client().get("/api/v1/public/plan").andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("BASIC")).andExpect(jsonPath("$.features").isEmpty());

        // Standard: qrafiklər və dəvət açılır, filial müqayisəsi və audit yox
        setPlan(admin, "STANDARD", "ACTIVE", 0);
        owner.get("/analytics/heatmap").andExpect(status().isOk());
        owner.get("/referrals/settings").andExpect(status().isOk());
        owner.get("/analytics/overview?branchId=" + branchOf()).andExpect(status().isForbidden());
        owner.get("/audit-logs").andExpect(status().isForbidden());

        setPlan(admin, "PREMIUM", "ACTIVE", 0);
        owner.get("/audit-logs").andExpect(status().isOk());
        owner.get("/analytics/overview?branchId=" + branchOf()).andExpect(status().isOk());
    }

    private String branchOf() {
        return az.tapstamp.tenancy.TenantContext.callAs(alpha(), () -> tenantJdbc
                .sql("SELECT id::text FROM branches WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1")
                .query(String.class).single());
    }

    @Test
    void branchAndStaffLimitsFollowThePlan() throws Exception {
        AdminClient admin = signedInPlatformAdmin();
        setPlan(admin, "BASIC", "ACTIVE", 0);
        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        // Test şirkətinin artıq filialı var — Basic-in 1 filialı dolub
        owner.post("/settings/branches", Map.of("name", Map.of("az", "Yeni filial")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
        long employees = count("SELECT count(DISTINCT staff_user_id) FROM staff_roles WHERE role <> 'COMPANY_OWNER'",
                Map.of());
        if (employees >= 3) {
            owner.post("/team/invitations", Map.of("email", "limit-" + System.nanoTime() + "@example.test",
                    "role", "STAFF", "branchId", branchOf(), "language", "az"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
        }
        // Sahiblər limitə daxil deyil
        owner.post("/team/invitations", Map.of("email", "owner-" + System.nanoTime() + "@example.test",
                "role", "COMPANY_OWNER", "language", "az")).andExpect(status().isCreated());
    }

    @Test
    void expiredTrialSuspendsTheCompanyUntilPaymentIsRecorded() throws Exception {
        AdminClient admin = signedInPlatformAdmin();
        setPlan(admin, "PREMIUM", "TRIAL", 0);
        client().get("/api/v1/public/legal").andExpect(status().isOk());

        clock.advance(Duration.ofDays(Plan.TRIAL_DAYS));
        plans.evictCaches();
        // Hər şey bağlıdır: işçinin girişi, müştəri API-si
        AdminClient.staff(mvc, HOST).login("someone@example.test", STAFF_PASSWORD)
                .andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.code").value("PLAN_TRIAL_EXPIRED"));
        client().get("/api/v1/public/legal").andExpect(status().isPaymentRequired());
        client().get("/api/v1/auth/session").andExpect(status().isPaymentRequired());
        // "Xidmət dayandırılıb" ekranı üçün lazım olanlar açıqdır
        client().get("/api/v1/public/plan").andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(true)).andExpect(jsonPath("$.features").isEmpty());
        client().get("/api/v1/context").andExpect(status().isOk());
        client().get("/api/v1/public/company").andExpect(status().isOk());

        // Ödəniş qeyd olunur — xidmət bərpa olunur
        AdminClient platform = signedInPlatformAdmin();
        setPlan(platform, "PREMIUM", "ACTIVE", 0);
        client().get("/api/v1/public/legal").andExpect(status().isOk());
        platform.get(planPath()).andExpect(jsonPath("$.expired").value(false));
    }
}
