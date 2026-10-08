package az.cafeloyalty.analytics;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import az.cafeloyalty.security.admin.AdminRole;
import az.cafeloyalty.shared.id.Ids;
import az.cafeloyalty.support.AdminClient;
import az.cafeloyalty.support.AdminIntegrationTest;
import az.cafeloyalty.support.TestTenants;
import az.cafeloyalty.tenancy.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Analitika sorğuları. Hər test öz yeni filialında məlumat yaradır və nəticəni həmin filiala görə süzür — şirkətin
 * digər testlərdən qalan məlumatı nəticəyə təsir etmir.
 */
class AnalyticsIT extends AdminIntegrationTest {

    private static final String HOST = TestTenants.ALPHA_HOST;

    private UUID branch;
    private ZoneId zone;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        branch = addBranch(HOST, "Analitika " + UUID.randomUUID());
        zone = TenantContext.callAs(tenant(HOST), () -> ZoneId.of(tenantJdbc.sql(
                "SELECT timezone FROM company_profile WHERE singleton").query(String.class).single()));
        today = LocalDate.now(clock.withZone(zone));
    }

    @Test
    void metricsAreComputedPerBranchInCompanyTimezone() throws Exception {
        UUID aysel = customer("az", today.minusDays(10));
        UUID ivan = customer("ru", today.minusDays(8));
        UUID john = customer("en", today.minusDays(200));
        stamp(aysel, today.minusDays(10), 10, "CONFIRMED", null);
        UUID reward = reward(aysel, today.minusDays(3), today.minusDays(1));
        stamp(aysel, today.minusDays(3), 18, "CONFIRMED", reward);
        stamp(aysel, today.minusDays(2), 12, "REJECTED", null);
        stamp(ivan, today.minusDays(8), 9, "CONFIRMED", null);
        stamp(john, today.minusDays(200), 9, "CONFIRMED", null);

        AdminClient owner = owner();
        String scope = "?branchId=" + branch;
        owner.get("/analytics/overview" + scope)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCustomers").value(3))
                .andExpect(jsonPath("$.active7").value(1))
                .andExpect(jsonPath("$.active30").value(2))
                .andExpect(jsonPath("$.active90").value(2))
                .andExpect(jsonPath("$.stampsInPeriod").value(3))
                .andExpect(jsonPath("$.newCustomersInPeriod").value(2))
                .andExpect(jsonPath("$.rewardsEarned").value(1))
                .andExpect(jsonPath("$.rewardsRedeemed").value(1))
                .andExpect(jsonPath("$.redemptionRate").value(1.0));

        owner.get("/analytics/stamps-series" + scope)
                .andExpect(jsonPath("$.length()").value(30))
                .andExpect(jsonPath("$[29].date").value(today.toString()))
                .andExpect(jsonPath("$[?(@.date == '" + today.minusDays(3) + "')].stamps").value(1))
                .andExpect(jsonPath("$[?(@.date == '" + today.minusDays(1) + "')].rewardsRedeemed").value(1));
        owner.get("/analytics/stamps-series" + scope + "&granularity=WEEK&from=" + today.minusDays(13) + "&to=" + today)
                .andExpect(status().isOk());

        int dow = today.minusDays(3).getDayOfWeek().getValue();
        owner.get("/analytics/heatmap" + scope)
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.dayOfWeek == " + dow + " && @.hour == 18)].stamps").value(1));

        // Dövrdə qeydiyyat: Aysel və İvan; yalnız Aysel 30 gün ərzində başqa gün qayıdıb
        owner.get("/analytics/retention" + scope)
                .andExpect(jsonPath("$.cohort").value(2))
                .andExpect(jsonPath("$.returned").value(1))
                .andExpect(jsonPath("$.rate").value(0.5));

        owner.get("/analytics/languages" + scope)
                .andExpect(jsonPath("$[?(@.language == 'az')].customers").value(1))
                .andExpect(jsonPath("$[?(@.language == 'en')].customers").value(1))
                .andExpect(jsonPath("$[?(@.language == 'ru')].customers").value(1));
    }

    @Test
    void accessIsLimitedByRoleAndBranch() throws Exception {
        String managerEmail = uniqueEmail("manager");
        createStaff(HOST, managerEmail, AdminRole.BRANCH_MANAGER, branch, false);
        AdminClient manager = signedInStaff(HOST, managerEmail, false);
        manager.get("/analytics/overview").andExpect(status().isOk());
        manager.get("/analytics/overview?branchId=" + branchOf(HOST)).andExpect(status().isNotFound());

        String baristaEmail = uniqueEmail("barista");
        createStaff(HOST, baristaEmail, AdminRole.STAFF, branch, false);
        signedInStaff(HOST, baristaEmail, false).get("/analytics/overview").andExpect(status().isForbidden());

        AdminClient owner = owner();
        owner.get("/analytics/overview?from=" + today + "&to=" + today.minusDays(1)).andExpect(status().isBadRequest());
        owner.get("/analytics/overview?from=" + today.minusDays(400) + "&to=" + today).andExpect(status().isBadRequest());
        owner.get("/analytics/stamps-series?granularity=YEAR").andExpect(status().isBadRequest());
    }

    private AdminClient owner() throws Exception {
        String email = uniqueEmail("owner");
        createStaff(HOST, email, AdminRole.COMPANY_OWNER, null, false);
        return signedInStaff(HOST, email, false);
    }

    private ZonedDateTime at(LocalDate day, int hour) {
        return ZonedDateTime.of(day, LocalTime.of(hour, 0), zone);
    }

    private UUID customer(String language, LocalDate registered) {
        UUID id = Ids.newId();
        TenantContext.runAs(tenant(HOST), () -> tenantJdbc.sql("""
                        INSERT INTO customers (id, phone_hash, phone_masked, preferred_language, status, registered_at,
                                               created_at, updated_at)
                        VALUES (:id, :hash, '+994 50 ***-**-00', :lang, 'ACTIVE', :at, :at, :at)""")
                .param("id", id)
                .param("hash", UUID.randomUUID().toString().getBytes())
                .param("lang", language)
                .param("at", at(registered, 8).toOffsetDateTime())
                .update());
        return id;
    }

    private void stamp(UUID customerId, LocalDate day, int hour, String status, UUID rewardId) {
        TenantContext.runAs(tenant(HOST), () -> tenantJdbc.sql("""
                        INSERT INTO stamps (id, branch_id, customer_id, source, status, local_date, reward_id, created_at,
                                            confirmed_at, revoke_reason)
                        VALUES (:id, :branch, :customer, 'NFC_TAP', :status, :day, :reward, :at,
                                CASE WHEN :status = 'CONFIRMED' THEN CAST(:at AS timestamptz) END, NULL)""")
                .param("id", Ids.newId())
                .param("branch", branch)
                .param("customer", customerId)
                .param("status", status)
                .param("day", day)
                .param("reward", rewardId)
                .param("at", at(day, hour).toOffsetDateTime())
                .update());
    }

    private UUID reward(UUID customerId, LocalDate earned, LocalDate redeemed) {
        UUID id = Ids.newId();
        TenantContext.runAs(tenant(HOST), () -> tenantJdbc.sql("""
                        INSERT INTO rewards (id, customer_id, rule_id, stamps_required_snapshot, reward_name_snapshot,
                                             status, earned_at, redeemed_at, redeemed_branch_id, created_at)
                        SELECT :id, :customer, r.id, r.stamps_required, r.reward_name, 'REDEEMED', :earned, :redeemed,
                               :branch, :earned
                        FROM reward_rules r WHERE r.active""")
                .param("id", id)
                .param("customer", customerId)
                .param("earned", at(earned, 18).toOffsetDateTime())
                .param("redeemed", at(redeemed, 12).toOffsetDateTime())
                .param("branch", branch)
                .update());
        return id;
    }
}
