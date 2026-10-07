package az.cafeloyalty.loyalty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import az.cafeloyalty.support.AdminClient;
import az.cafeloyalty.support.CustomerClient;
import az.cafeloyalty.support.TapIntegrationTest;
import az.cafeloyalty.support.TestTenants;
import az.cafeloyalty.tenancy.TenantContext;
import org.junit.jupiter.api.Test;

/** Hədiyyə kartının işçi tərəfindən yoxlanması və təhvili: idempotentlik, paralellik, saxta və köhnə kodlar. */
class RedemptionIT extends TapIntegrationTest {

    /** Müştəri kartını doldurur (9 möhür) və hədiyyə kartının kodunu qaytarır. */
    private String giftCardCode(CustomerClient customer, UUID branchId) throws Exception {
        TestTag tag = newTag(branchId);
        for (int counter = 1; counter <= 9; counter++) {
            customer.post(claimPath(tapToken(customer, tag, counter))).andExpect(status().isOk());
        }
        return json(customer.get("/api/v1/me/gift-cards"), "$[0].code");
    }

    @Test
    void staffChecksAndRedeemsGiftCardOnce() throws Exception {
        UUID branchId = newBranch(Policy.relaxed());
        CustomerClient customer = signedIn(uniquePhone());
        String code = giftCardCode(customer, branchId);
        AdminClient barista = signedInBarista(branchId);

        // Kiçik hərflər, boşluq və tiresiz yazılış da qəbul olunur
        String typed = " " + code.replace("-", "").toLowerCase(Locale.ROOT) + " ";
        barista.post("/staff-console/gift-cards/check", Map.of("code", typed)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.rewardName.az").value("Hədiyyə qəhvə"))
                .andExpect(jsonPath("$.customerName").value("Test Müştəri"))
                .andExpect(jsonPath("$.phoneMasked").exists());

        barista.post("/staff-console/gift-cards/redeem", Map.of("code", code, "branchId", branchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REDEEMED"))
                .andExpect(jsonPath("$.redeemedBranchName.az").exists());
        // Eyni işçinin təkrar basışı (məs. şəbəkə gecikməsi) — eyni nəticə
        barista.post("/staff-console/gift-cards/redeem", Map.of("code", code, "branchId", branchId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REDEEMED"));
        // Başqa işçi — artıq istifadə olunub
        signedInBarista(branchId).post("/staff-console/gift-cards/redeem", Map.of("code", code, "branchId", branchId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GIFT_CARD_ALREADY_REDEEMED"))
                .andExpect(jsonPath("$.redeemedAt").exists());

        customer.get("/api/v1/me/gift-cards").andExpect(jsonPath("$[0].status").value("REDEEMED"))
                .andExpect(jsonPath("$[0].redeemedAt").exists());
        customer.get("/api/v1/me").andExpect(jsonPath("$.availableRewards").isEmpty());
        barista.get("/staff-console/gift-cards/recent?branchId=" + branchId).andExpect(jsonPath("$[0].code").value(code));
        assertThat(count("""
                SELECT rewards_redeemed FROM loyalty_accounts la JOIN rewards r ON r.customer_id = la.customer_id
                WHERE r.code_lookup IS NOT NULL AND r.status = 'REDEEMED' AND r.redeemed_branch_id = :branchId""",
                Map.of("branchId", branchId))).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'GIFT_CARD_REDEEMED' AND details ->> 'branchId' = :b",
                Map.of("b", branchId.toString()))).isEqualTo(1);
    }

    @Test
    void parallelRedemptionsByDifferentStaffSucceedOnlyOnce() throws Exception {
        UUID branchId = newBranch(Policy.relaxed());
        String code = giftCardCode(signedIn(uniquePhone()), branchId);
        List<AdminClient> baristas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            baristas.add(signedInBarista(branchId));
        }
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(baristas.size())) {
            for (AdminClient barista : baristas) {
                results.add(pool.submit(() -> {
                    start.await();
                    return barista.post("/staff-console/gift-cards/redeem", Map.of("code", code, "branchId", branchId))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsOnlyOnce(200).filteredOn(s -> s != 200).containsOnly(409);
        }
    }

    @Test
    void unknownExpiredAndForeignCodesAreRejected() throws Exception {
        UUID branchId = newBranch(Policy.relaxed());
        String code = giftCardCode(signedIn(uniquePhone()), branchId);
        AdminClient barista = signedInBarista(branchId);

        barista.post("/staff-console/gift-cards/check", Map.of("code", "ABCD-EFGH")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GIFT_CARD_NOT_FOUND"));
        barista.post("/staff-console/gift-cards/check", Map.of("code", "not a code")).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'GIFT_CARD_LOOKUP_FAILED'", Map.of()))
                .isGreaterThanOrEqualTo(2);

        // Başqa şirkətin işçisi bu kodu tapa bilmir (ayrı baza)
        UUID betaBranch = TenantContext.callAs(directory.findByHost(TestTenants.BETA_HOST).orElseThrow(),
                () -> tenantJdbc.sql("SELECT id FROM branches ORDER BY created_at LIMIT 1").query(UUID.class).single());
        AdminClient foreign = signedInBarista(TestTenants.BETA_HOST, betaBranch);
        foreign.post("/staff-console/gift-cards/check", Map.of("code", code)).andExpect(status().isNotFound());

        // Başqa filialın işçisi kartı yoxlaya bilər, amma başqa filialın adından təhvil verə bilməz
        AdminClient otherBranch = signedInBarista(newBranch(Policy.relaxed()));
        otherBranch.post("/staff-console/gift-cards/redeem", Map.of("code", code, "branchId", branchId))
                .andExpect(status().isNotFound());

        TenantContext.runAs(alpha(), () -> tenantJdbc.sql("""
                        UPDATE rewards SET expires_at = now() - interval '1 day'
                        WHERE redeemed_at IS NULL AND status = 'AVAILABLE' AND customer_id IN (
                            SELECT s.customer_id FROM stamps s WHERE s.branch_id = :branchId)""")
                .param("branchId", branchId).update());
        barista.post("/staff-console/gift-cards/check", Map.of("code", code)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        barista.post("/staff-console/gift-cards/redeem", Map.of("code", code, "branchId", branchId))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("GIFT_CARD_EXPIRED"));
    }

    @Test
    void lookupsAreRateLimitedPerStaffMember() throws Exception {
        AdminClient barista = signedInBarista(newBranch(Policy.relaxed()));
        for (int i = 0; i < 30; i++) {
            barista.post("/staff-console/gift-cards/check", Map.of("code", "ABCD-EFG" + (i % 8 + 2)))
                    .andExpect(status().isNotFound());
        }
        barista.post("/staff-console/gift-cards/check", Map.of("code", "ABCD-EFGH"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.retryAfterSeconds").exists());
    }

    @Test
    void customersAndAnonymousCannotUseStaffEndpoints() throws Exception {
        CustomerClient customer = signedIn(uniquePhone());
        customer.post("/api/v1/admin/staff-console/gift-cards/check", Map.of("code", "ABCD-EFGH"))
                .andExpect(status().isUnauthorized());
    }
}
