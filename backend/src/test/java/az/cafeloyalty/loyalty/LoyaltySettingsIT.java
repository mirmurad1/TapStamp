package az.cafeloyalty.loyalty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import az.cafeloyalty.security.admin.AdminRole;
import az.cafeloyalty.shared.id.Ids;
import az.cafeloyalty.support.AdminClient;
import az.cafeloyalty.support.AdminIntegrationTest;
import az.cafeloyalty.support.TestTenants;
import az.cafeloyalty.tenancy.TenantContext;
import org.junit.jupiter.api.Test;

/**
 * Sahibin loyallıq parametrləri: möhür qaydası və hədiyyə (dinamik möhür sayı və adı). Beta şirkətində aparılır —
 * alpha-dakı möhür testlərinin qaydalarına toxunmur; sonda standart dəyərlər bərpa olunur.
 */
class LoyaltySettingsIT extends AdminIntegrationTest {

    private static final String HOST = TestTenants.BETA_HOST;

    @Test
    void ownerChangesStampPolicyAndOthersCannot() throws Exception {
        AdminClient owner = owner();
        AdminClient staff = signedInStaff(HOST, staffAccount(AdminRole.STAFF), false);
        AdminClient manager = signedInStaff(HOST, staffAccount(AdminRole.BRANCH_MANAGER), false);
        try {
            owner.put("/settings/stamp-policy", Map.of("minIntervalMinutes", 45, "maxStampsPerDay", 2,
                            "requireStaffApproval", true, "pendingTimeoutMinutes", 15))
                    .andExpect(status().isOk());
            owner.get("/settings/stamp-policy").andExpect(jsonPath("$.minIntervalMinutes").value(45))
                    .andExpect(jsonPath("$.maxStampsPerDay").value(2))
                    .andExpect(jsonPath("$.requireStaffApproval").value(true));
            assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'STAMP_POLICY_UPDATED'")).isPositive();

            owner.put("/settings/stamp-policy", Map.of("minIntervalMinutes", 2000, "maxStampsPerDay", 0,
                    "requireStaffApproval", false, "pendingTimeoutMinutes", 30)).andExpect(status().isBadRequest());
            staff.get("/settings/stamp-policy").andExpect(status().isForbidden());
            manager.put("/settings/stamp-policy", Map.of("minIntervalMinutes", 0, "maxStampsPerDay", 50,
                    "requireStaffApproval", false, "pendingTimeoutMinutes", 30)).andExpect(status().isForbidden());
        } finally {
            owner.put("/settings/stamp-policy", Map.of("minIntervalMinutes", 20, "maxStampsPerDay", 3,
                    "requireStaffApproval", false, "pendingTimeoutMinutes", 30)).andExpect(status().isOk());
        }
    }

    @Test
    void ownerChangesRewardAndCustomersOverNewThresholdGetGiftCardsImmediately() throws Exception {
        AdminClient owner = owner();
        UUID full = customerWithStamps(7);
        UUID partial = customerWithStamps(3);
        try {
            owner.put("/settings/reward-rule", Map.of(
                            "stampsRequired", 5,
                            "rewardName", Map.of("az", "Pulsuz yuma", "en", "Free wash", "ru", " "),
                            "validityDays", 30))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stampsRequired").value(5))
                    .andExpect(jsonPath("$.rewardName.az").value("Pulsuz yuma"))
                    .andExpect(jsonPath("$.rewardName.ru").doesNotExist())
                    .andExpect(jsonPath("$.validityDays").value(30))
                    .andExpect(jsonPath("$.giftCardsIssued").value(1));

            Map<String, Object> byCustomer = Map.of("id", full);
            assertThat(count("SELECT current_stamps FROM loyalty_accounts WHERE customer_id = :id", byCustomer))
                    .isEqualTo(2);
            assertThat(count("""
                    SELECT count(*) FROM rewards WHERE customer_id = :id AND status = 'AVAILABLE'
                      AND reward_name_snapshot ->> 'az' = 'Pulsuz yuma' AND expires_at IS NOT NULL""", byCustomer))
                    .isEqualTo(1);
            assertThat(count("SELECT current_stamps FROM loyalty_accounts WHERE customer_id = :id",
                    Map.of("id", partial))).isEqualTo(3);
            // Müştəri tətbiqi yeni qaydanı görür
            mvc.perform(get("/api/v1/public/company")
                            .with(request -> {
                                request.setServerName(HOST);
                                return request;
                            }))
                    .andExpect(jsonPath("$.rewardRule.stampsRequired").value(5))
                    .andExpect(jsonPath("$.rewardRule.rewardName.az").value("Pulsuz yuma"));

            owner.put("/settings/reward-rule", Map.of("stampsRequired", 0, "rewardName", Map.of("az", "X")))
                    .andExpect(status().isBadRequest());
            owner.put("/settings/reward-rule", Map.of("stampsRequired", 5, "rewardName", Map.of("az", " ")))
                    .andExpect(status().isBadRequest());
        } finally {
            owner.put("/settings/reward-rule", Map.of("stampsRequired", 9,
                    "rewardName", Map.of("az", "Hədiyyə qəhvə", "en", "Free coffee"))).andExpect(status().isOk());
        }
        assertThat(count("SELECT count(*) FROM reward_rules WHERE active")).isEqualTo(1);
    }

    private AdminClient owner() throws Exception {
        String email = uniqueEmail("owner");
        createStaff(HOST, email, AdminRole.COMPANY_OWNER, null, true);
        return signedInStaff(HOST, email, true);
    }

    private String staffAccount(AdminRole role) {
        String email = uniqueEmail(role.name().toLowerCase(Locale.ROOT));
        createStaff(HOST, email, role, branchOf(HOST), false);
        return email;
    }

    private UUID customerWithStamps(int stamps) {
        UUID id = Ids.newId();
        TenantContext.runAs(tenant(HOST), () -> {
            byte[] hash = new byte[32];
            new SecureRandom().nextBytes(hash);
            tenantJdbc.sql("""
                            INSERT INTO customers (id, phone_hash, preferred_language, status, registered_at, created_at,
                                                   updated_at)
                            VALUES (:id, :hash, 'az', 'ACTIVE', now(), now(), now())""")
                    .param("id", id).param("hash", hash).update();
            tenantJdbc.sql("""
                            INSERT INTO loyalty_accounts (id, customer_id, current_stamps, total_stamps, created_at,
                                                          updated_at)
                            VALUES (:id, :customerId, :stamps, :stamps, now(), now())""")
                    .param("id", Ids.newId()).param("customerId", id).param("stamps", stamps).update();
        });
        return id;
    }

    private int count(String sql) {
        return count(sql, Map.of());
    }

    private int count(String sql, Map<String, ?> params) {
        return TenantContext.callAs(tenant(HOST), () -> tenantJdbc.sql(sql).params(params).query(Integer.class).single());
    }
}
