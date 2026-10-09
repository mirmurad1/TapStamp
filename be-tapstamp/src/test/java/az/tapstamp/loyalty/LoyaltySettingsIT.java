package az.tapstamp.loyalty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.AdminIntegrationTest;
import az.tapstamp.support.TestTenants;
import az.tapstamp.tenancy.TenantContext;
import org.junit.jupiter.api.Test;

/**
 * Sahibin loyallıq parametrləri: möhür qaydası və hədiyyə (dinamik möhür sayı və adı). Beta şirkətində aparılır —
 * alpha-dakı möhür testlərinin qaydalarına toxunmur; sonda standart dəyərlər bərpa olunur.
 */
class LoyaltySettingsIT extends AdminIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private az.tapstamp.loyalty.stamp.StampLedger ledger;

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
                            "validityDays", 30,
                            "existingCards", "APPLY_NOW"))
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
            owner.put("/settings/reward-rule", Map.of("stampsRequired", 9, "existingCards", "APPLY_NOW",
                    "rewardName", Map.of("az", "Hədiyyə qəhvə", "en", "Free coffee"))).andExpect(status().isOk());
        }
        assertThat(count("SELECT count(*) FROM reward_rules WHERE active")).isEqualTo(1);
    }

    @Test
    void byDefaultCardsInProgressFinishOnPreviousRule() throws Exception {
        AdminClient owner = owner();
        UUID full = customerWithStamps(8);
        UUID partial = customerWithStamps(3);
        UUID empty = customerWithStamps(0);
        // Başlanğıc: hədd 9 (bütün kartlar aktiv qaydada)
        owner.put("/settings/reward-rule", Map.of("stampsRequired", 9, "existingCards", "APPLY_NOW",
                "rewardName", Map.of("az", "Hədiyyə qəhvə"))).andExpect(status().isOk());
        try {
            // Təsir: yarımçıq kartlar və qayda dərhal tətbiq olunsa veriləcək hədiyyələr
            owner.get("/settings/reward-rule/impact?stampsRequired=5")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentStampsRequired").isNumber())
                    .andExpect(jsonPath("$.cardsInProgress").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.giftCardsIfAppliedNow").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
            owner.get("/settings/reward-rule/impact?stampsRequired=0").andExpect(status().isBadRequest());

            // "Hamıya dərhal" kimliyin təsdiqi olmadan rədd edilir (standart rejim təsdiqsiz işləyir)
            String unconfirmed = uniqueEmail("owner");
            createStaff(HOST, unconfirmed, AdminRole.COMPANY_OWNER, null, false);
            signedInStaff(HOST, unconfirmed, false).put("/settings/reward-rule", Map.of("stampsRequired", 6,
                            "rewardName", Map.of("az", "Hədiyyə qəhvə"), "existingCards", "APPLY_NOW"))
                    .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
            assertThat(count("SELECT count(*) FROM reward_rules WHERE active AND stamps_required = 6")).isZero();

            // Standart: heç kimə dərhal hədiyyə verilmir, yarımçıq kartlar köhnə (9) qaydaya bağlanır
            owner.put("/settings/reward-rule", Map.of("stampsRequired", 5, "rewardName", Map.of("az", "Yeni hədiyyə")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stampsRequired").value(5))
                    .andExpect(jsonPath("$.giftCardsIssued").value(0));
            assertThat(count("SELECT count(*) FROM rewards WHERE customer_id = :id", Map.of("id", full))).isZero();
            String pinned = """
                    SELECT count(*) FROM loyalty_accounts la JOIN reward_rules r ON r.id = la.reward_rule_id
                    WHERE la.customer_id = :id AND r.stamps_required = 9""";
            assertThat(count(pinned, Map.of("id", full))).isEqualTo(1);
            assertThat(count(pinned, Map.of("id", partial))).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM loyalty_accounts WHERE customer_id = :id AND reward_rule_id IS NULL",
                    Map.of("id", empty))).isEqualTo(1);

            // 9-cu möhürdə köhnə qayda ilə hədiyyə; kart sonra yeni qaydaya (5) keçir
            TenantContext.runAs(tenant(HOST), () -> ledger.applyConfirmed(full, Ids.newId(), java.time.Instant.now()));
            assertThat(count("""
                    SELECT count(*) FROM rewards WHERE customer_id = :id AND stamps_required_snapshot = 9
                      AND reward_name_snapshot ->> 'az' <> 'Yeni hədiyyə'""", Map.of("id", full))).isEqualTo(1);
            assertThat(count("""
                    SELECT count(*) FROM loyalty_accounts
                    WHERE customer_id = :id AND current_stamps = 0 AND reward_rule_id IS NULL""", Map.of("id", full)))
                    .isEqualTo(1);
            // Yarımçıq kartın növbəti möhürü hələ köhnə qayda ilə sayılır (4/9, hədiyyə yoxdur)
            TenantContext.runAs(tenant(HOST), () -> ledger.applyConfirmed(partial, Ids.newId(), java.time.Instant.now()));
            assertThat(count("SELECT count(*) FROM rewards WHERE customer_id = :id", Map.of("id", partial))).isZero();

            // "Hamıya dərhal": bağlı kartlar da yeni qaydaya keçir
            owner.put("/settings/reward-rule", Map.of("stampsRequired", 4, "rewardName", Map.of("az", "Yeni hədiyyə"),
                            "existingCards", "APPLY_NOW"))
                    .andExpect(jsonPath("$.giftCardsIssued").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
            assertThat(count("SELECT count(*) FROM rewards WHERE customer_id = :id", Map.of("id", partial))).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM loyalty_accounts WHERE reward_rule_id IS NOT NULL")).isZero();
        } finally {
            owner.put("/settings/reward-rule", Map.of("stampsRequired", 9, "existingCards", "APPLY_NOW",
                    "rewardName", Map.of("az", "Hədiyyə qəhvə", "en", "Free coffee"))).andExpect(status().isOk());
        }
    }

    private AdminClient owner() throws Exception {
        String email = uniqueEmail("owner");
        createStaff(HOST, email, AdminRole.COMPANY_OWNER, null, false);
        AdminClient owner = signedInStaff(HOST, email, false);
        // "Hamıya dərhal" (APPLY_NOW) kimliyin təsdiqini tələb edir: 2FA yoxdur — şifrə ilə
        owner.post("/auth/step-up", Map.of("password", PASSWORD)).andExpect(status().isNoContent());
        return owner;
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
