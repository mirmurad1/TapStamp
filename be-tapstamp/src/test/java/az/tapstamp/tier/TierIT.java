package az.tapstamp.tier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.TapIntegrationTest;
import az.tapstamp.tenancy.TenantContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Səviyyələr: ziyarət sayına görə avtomatik səviyyə, səviyyə bonusu (işçi təsdiqi rejimində də), müştəri üçün
 * irəliləyiş, bölgü, yoxlamalar. Səviyyələr bütün şirkətə aiddir — testdən sonra silinir.
 */
class TierIT extends TapIntegrationTest {

    private AdminClient owner() throws Exception {
        return signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
    }

    @AfterEach
    void removeTiers() {
        TenantContext.runAs(alpha(), () -> {
            tenantJdbc.sql("DELETE FROM stamps WHERE tier_id IS NOT NULL").update();
            tenantJdbc.sql("DELETE FROM tier_reach_grants").update();
            tenantJdbc.sql("DELETE FROM loyalty_tiers").update();
            tenantJdbc.sql("UPDATE tier_settings SET enabled = true").update();
        });
    }

    private static Map<String, Object> tier(String name, int minVisits, int bonus) {
        return Map.of("name", Map.of("az", name), "color", "#CD7F32", "minVisits", minVisits, "bonusStamps", bonus);
    }

    private static <T> T read(CustomerClient customer, String path, String jsonPath) throws Exception {
        String body = customer.get(path).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, jsonPath);
    }

    private void claim(CustomerClient customer, TestTag tag, int counter) throws Exception {
        customer.post(claimPath(tapToken(customer, tag, counter))).andExpect(status().isOk());
    }

    @Test
    void tierFollowsVisitsAndAddsItsBonus() throws Exception {
        AdminClient owner = owner();
        owner.post("/tiers", tier("Bürünc", 1, 0)).andExpect(status().isCreated());
        owner.post("/tiers", tier("Gümüş", 2, 1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tiers[1].name.az").value("Gümüş"));
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient customer = signedIn(uniquePhone());

        customer.get("/api/v1/me/tier").andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.visits").value(0))
                .andExpect(jsonPath("$.current").doesNotExist())
                .andExpect(jsonPath("$.next.name.az").value("Bürünc"))
                .andExpect(jsonPath("$.visitsToNext").value(1))
                .andExpect(jsonPath("$.tiers[*].name.az").value(org.hamcrest.Matchers.contains("Bürünc", "Gümüş")));

        // 1-ci ziyarət: Bürünc (bonussuz)
        claim(customer, tag, 1);
        assertThat((Integer) read(customer, "/api/v1/me", "$.card.totalStamps")).isEqualTo(1);
        // 2-ci ziyarət: Gümüş — bu möhürə artıq +1
        claim(customer, tag, 2);
        assertThat((Integer) read(customer, "/api/v1/me", "$.card.totalStamps")).isEqualTo(3);
        customer.get("/api/v1/me/tier").andExpect(jsonPath("$.current.name.az").value("Gümüş"))
                .andExpect(jsonPath("$.next").doesNotExist());

        String customerId = read(customer, "/api/v1/me", "$.id");
        owner.get("/customers/" + customerId + "/timeline?size=50")
                .andExpect(jsonPath("$.items[?(@.source == 'TIER')]").isNotEmpty());
        owner.get("/tiers").andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers[1].customers").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void inApprovalModeTierBonusIsGrantedWhenStaffConfirms() throws Exception {
        owner().post("/tiers", tier("Hamı", 0, 2)).andExpect(status().isCreated());
        UUID branchId = newBranch(new Policy(0, 50, true, 30, false, null));
        TestTag tag = newTag(branchId);
        CustomerClient customer = signedIn(uniquePhone());
        claim(customer, tag, 1);
        assertThat((Integer) read(customer, "/api/v1/me", "$.card.totalStamps")).isZero();

        AdminClient barista = signedInBarista(branchId);
        String pending = barista.get("/staff-console/pending-stamps?branchId=" + branchId)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String stampId = JsonPath.read(pending, "$.items[0].id");
        barista.post("/staff-console/pending-stamps/" + stampId + "/confirm").andExpect(status().isOk());
        assertThat((Integer) read(customer, "/api/v1/me", "$.card.totalStamps")).isEqualTo(3);
    }

    @Test
    void usedTierCannotBeDeletedAndThresholdsAreUnique() throws Exception {
        AdminClient owner = owner();
        String body = owner.post("/tiers", tier("Hamı", 0, 1)).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        String used = JsonPath.read(body, "$.tiers[0].id");
        owner.post("/tiers", tier("Təkrar", 0, 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIER_THRESHOLD_TAKEN"));
        String other = JsonPath.read(owner.post("/tiers", tier("Qızıl", 50, 2)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8), "$.tiers[1].id");

        claim(signedIn(uniquePhone()), newTag(newBranch(Policy.relaxed())), 1);
        owner.delete("/tiers/" + used).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIER_IN_USE"));
        owner.delete("/tiers/" + other).andExpect(status().isOk()).andExpect(jsonPath("$.tiers.length()").value(1));
    }

    @Test
    void switchedOffProgramHidesTiersAndGivesNoBonus() throws Exception {
        AdminClient owner = owner();
        owner.post("/tiers", tier("Hamı", 0, 2)).andExpect(status().isCreated());
        owner.put("/tiers/settings", Map.of("enabled", false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.tiers.length()").value(1));
        CustomerClient customer = signedIn(uniquePhone());
        customer.get("/api/v1/me/tier").andExpect(jsonPath("$.enabled").value(false));
        claim(customer, newTag(newBranch(Policy.relaxed())), 1);
        assertThat((Integer) read(customer, "/api/v1/me", "$.card.totalStamps")).isEqualTo(1);

        owner.put("/tiers/settings", Map.of("enabled", true)).andExpect(jsonPath("$.enabled").value(true));
        customer.get("/api/v1/me/tier").andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.current.name.az").value("Hamı"));
    }

    @Test
    void reachingATierGivesTheGiftAndOneTimeStampsOnce() throws Exception {
        Map<String, Object> silver = new java.util.HashMap<>(tier("Gümüş", 2, 0));
        silver.put("reachGift", true);
        silver.put("reachStamps", 2);
        owner().post("/tiers", silver).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tiers[0].reachGift").value(true))
                .andExpect(jsonPath("$.tiers[0].reachStamps").value(2));
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient customer = signedIn(uniquePhone());

        claim(customer, tag, 1);
        assertThat((Integer) read(customer, "/api/v1/me", "$.card.totalStamps")).isEqualTo(1);
        // 2-ci ziyarət: Gümüşə çatdı — 1 (toxunuş) + 2 (bir dəfəlik) və hədiyyə kartı səbəbi ilə
        customer.post(claimPath(tapToken(customer, tag, 2))).andExpect(status().isOk())
                .andExpect(jsonPath("$.reachedTier.az").value("Gümüş"))
                .andExpect(jsonPath("$.reachedTierStamps").value(2))
                .andExpect(jsonPath("$.tierGiftCardId").isNotEmpty())
                .andExpect(jsonPath("$.giftCardId").doesNotExist());
        assertThat((Integer) read(customer, "/api/v1/me", "$.card.totalStamps")).isEqualTo(4);
        customer.get("/api/v1/me/gift-cards").andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.source == 'TIER')].tierName.az")
                        .value(org.hamcrest.Matchers.contains("Gümüş")));
        // 3-cü ziyarət: təkrar verilmir
        claim(customer, tag, 3);
        assertThat((Integer) read(customer, "/api/v1/me", "$.card.totalStamps")).isEqualTo(5);
        customer.get("/api/v1/me/gift-cards")
                .andExpect(jsonPath("$[?(@.source == 'TIER')]").value(org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void requestsAreValidatedAndOnlyForOwner() throws Exception {
        owner().post("/tiers", Map.of("name", Map.of("az", "Rəng"), "color", "red", "minVisits", 1,
                "bonusStamps", 0)).andExpect(status().isBadRequest());
        owner().post("/tiers", tier("Çox bonus", 1, 4)).andExpect(status().isBadRequest());
        signedInStaff(HOST, AdminRole.BRANCH_MANAGER, newBranch(Policy.relaxed()))
                .get("/tiers").andExpect(status().isForbidden());
    }
}
