package az.cafeloyalty.loyalty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import az.cafeloyalty.loyalty.stamp.StampApprovalService;
import az.cafeloyalty.nfc.BrowserIdCookie;
import az.cafeloyalty.support.AdminClient;
import az.cafeloyalty.support.CustomerClient;
import az.cafeloyalty.support.TapIntegrationTest;
import az.cafeloyalty.support.TestSmsInbox;
import az.cafeloyalty.tenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/** Möhürün alınması: qaydalar, idempotentlik, paralellik, brauzer bağlılığı, işçi təsdiqi, hədiyyə kartı. */
class StampClaimIT extends TapIntegrationTest {

    private static final ZoneId BAKU = ZoneId.of("Asia/Baku");

    @Autowired
    private StampApprovalService approvals;

    @Test
    void claimAddsStampAndRepeatedClaimReturnsSameResult() throws Exception {
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient customer = signedIn(uniquePhone());
        String token = tapToken(customer, tag, 1);

        customer.get("/api/v1/taps/" + token).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.sameBrowser").value(true))
                .andExpect(jsonPath("$.branchName.az").exists());
        ResultActions first = customer.post(claimPath(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.currentStamps").value(1))
                .andExpect(jsonPath("$.stampsRequired").value(9));
        String stampId = json(first, "$.stampId");

        // Təkrar göndəriş (məs. səhifə yeniləndi) — eyni nəticə, yeni möhür yoxdur
        customer.post(claimPath(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.stampId").value(stampId))
                .andExpect(jsonPath("$.currentStamps").value(1));
        customer.get("/api/v1/me").andExpect(jsonPath("$.card.currentStamps").value(1));
        customer.get("/api/v1/taps/" + token).andExpect(jsonPath("$.status").value("CONSUMED"));
    }

    @Test
    void parallelClaimsOfOneTokenCreateOneStamp() throws Exception {
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient customer = signedIn(uniquePhone());
        String token = tapToken(customer, tag, 1);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return customer.post(claimPath(token)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(200);
            }
        }
        assertThat(count("SELECT count(*) FROM stamps WHERE tag_id = :tagId", Map.of("tagId", tag.id()))).isEqualTo(1);
        customer.get("/api/v1/me").andExpect(jsonPath("$.card.currentStamps").value(1));
    }

    @Test
    void tokenIsBoundToBrowserAndCannotBeUsedByAnotherCustomer() throws Exception {
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient owner = signedIn(uniquePhone());
        CustomerClient friend = signedIn(uniquePhone());
        String token = tapToken(owner, tag, 1);

        // Link başqa telefona göndərilib: brauzer identifikatoru fərqlidir
        friend.post(claimPath(token)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TAP_BINDING_MISMATCH"));
        assertThat(count("SELECT count(*) FROM fraud_flags WHERE tag_id = :tagId AND type = 'BINDING_MISMATCH'",
                Map.of("tagId", tag.id()))).isEqualTo(1);

        // Sahibi möhürü alır (uğursuz cəhd tokeni yandırmır)
        owner.post(claimPath(token)).andExpect(status().isOk());
        // Eyni brauzerdə başqa hesab (cookie kopyalanıb) — token artıq istifadə olunub
        friend.setCookie(BrowserIdCookie.NAME, owner.cookie(BrowserIdCookie.NAME), "/");
        friend.post(claimPath(token)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAP_TOKEN_USED"));
    }

    @Test
    void tokenExpiresAfterSixtySeconds() throws Exception {
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient customer = signedIn(uniquePhone());
        String token = tapToken(customer, tag, 1);
        clock.advance(Duration.ofSeconds(61));

        customer.post(claimPath(token)).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("TAP_TOKEN_EXPIRED"));
        customer.post(claimPath("unknown-token")).andExpect(jsonPath("$.code").value("TAP_TOKEN_EXPIRED"));
        // Daxil olmamış istifadəçi claim edə bilmir
        client().post(claimPath(token)).andExpect(status().isUnauthorized());
    }

    @Test
    void cooldownRejectsWithRetryAfterAndRejectedTokenStaysRejected() throws Exception {
        TestTag tag = newTag(newBranch(new Policy(20, 50, false, 30, false, null)));
        CustomerClient customer = signedIn(uniquePhone());
        customer.post(claimPath(tapToken(customer, tag, 1))).andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(5));
        String early = tapToken(customer, tag, 2);
        ResultActions rejected = customer.post(claimPath(early)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAMP_COOLDOWN_ACTIVE"));
        long retryAfter = Long.parseLong(json(rejected, "$.retryAfterSeconds"));
        assertThat(retryAfter).isBetween(14 * 60L, 15 * 60L + 2);
        // Səhifə yenilənəndə eyni xəta (token REJECTED olub)
        customer.post(claimPath(early)).andExpect(jsonPath("$.code").value("STAMP_COOLDOWN_ACTIVE"));
        customer.get("/api/v1/taps/" + early).andExpect(jsonPath("$.status").value("REJECTED"));

        clock.advance(Duration.ofMinutes(16));
        customer.post("/api/v1/auth/refresh").andExpect(status().isOk());
        customer.post(claimPath(tapToken(customer, tag, 3))).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStamps").value(2));
        assertThat(count("SELECT count(*) FROM tap_events WHERE stage = 'CLAIM' AND reason = 'STAMP_COOLDOWN_ACTIVE' "
                + "AND tag_id = :tagId", Map.of("tagId", tag.id()))).isEqualTo(1);
    }

    @Test
    void cooldownAppliesAcrossBranches() throws Exception {
        TestTag centre = newTag(newBranch(new Policy(20, 50, false, 30, false, null)));
        TestTag other = newTag(newBranch(new Policy(20, 50, false, 30, false, null)));
        CustomerClient customer = signedIn(uniquePhone());
        customer.post(claimPath(tapToken(customer, centre, 1))).andExpect(status().isOk());

        // Bir filialda möhür alınıb — vaxt limiti bitməyənə qədər digər filialda da alınmır
        clock.advance(Duration.ofMinutes(2));
        customer.post(claimPath(tapToken(customer, other, 1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAMP_COOLDOWN_ACTIVE"));

        clock.advance(Duration.ofMinutes(19));
        customer.post("/api/v1/auth/refresh").andExpect(status().isOk());
        customer.post(claimPath(tapToken(customer, other, 2))).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStamps").value(2));
    }

    @Test
    void dailyLimitFollowsCompanyTimezoneMidnight() throws Exception {
        TestTag tag = newTag(newBranch(new Policy(0, 2, false, 30, false, null)));
        CustomerClient customer = signedIn(uniquePhone());
        Instant now = clock.instant();
        Instant bakuMidnight = now.atZone(BAKU).toLocalDate().plusDays(1).atStartOfDay(BAKU).toInstant();
        // Bakıda gecəyarısı 00:00 UTC deyil, 20:00 UTC-dir
        assertThat(bakuMidnight.atOffset(ZoneOffset.UTC).getHour()).isEqualTo(20);
        clock.advance(Duration.between(now, bakuMidnight).minusMinutes(3));
        customer.post("/api/v1/auth/refresh").andExpect(status().isOk());

        customer.post(claimPath(tapToken(customer, tag, 1))).andExpect(status().isOk());
        customer.post(claimPath(tapToken(customer, tag, 2))).andExpect(status().isOk());
        ResultActions limited = customer.post(claimPath(tapToken(customer, tag, 3))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAMP_DAILY_LIMIT_REACHED"));
        assertThat(Long.parseLong(json(limited, "$.retryAfterSeconds"))).isBetween(60L, 3 * 60L + 2);

        clock.advance(Duration.ofMinutes(4));
        customer.post("/api/v1/auth/refresh").andExpect(status().isOk());
        customer.post(claimPath(tapToken(customer, tag, 4))).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStamps").value(3));

        LocalDate before = bakuMidnight.minusSeconds(1).atZone(BAKU).toLocalDate();
        Map<String, Object> byTag = Map.of("tagId", tag.id(), "day", before);
        assertThat(count("SELECT count(*) FROM stamps WHERE tag_id = :tagId AND local_date = :day", byTag)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM stamps WHERE tag_id = :tagId AND local_date = :day + 1", byTag))
                .isEqualTo(1);
    }

    @Test
    void businessHoursRuleRejectsWhenBranchIsClosed() throws Exception {
        // Heç vaxt açıq olmayan filial (qrafik boşdur)
        TestTag tag = newTag(newBranch(new Policy(0, 50, false, 30, true, "{}")));
        CustomerClient customer = signedIn(uniquePhone());
        customer.post(claimPath(tapToken(customer, tag, 1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_BUSINESS_HOURS"));
    }

    @Test
    void completingTheCardIssuesGiftCardWithUniqueCode() throws Exception {
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient customer = signedIn(uniquePhone());
        for (int counter = 1; counter <= 8; counter++) {
            customer.post(claimPath(tapToken(customer, tag, counter))).andExpect(jsonPath("$.giftCardId").doesNotExist());
        }
        ResultActions last = customer.post(claimPath(tapToken(customer, tag, 9))).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStamps").value(0))
                .andExpect(jsonPath("$.giftCardId").exists());
        String giftCardId = json(last, "$.giftCardId");

        ResultActions cards = customer.get("/api/v1/me/gift-cards").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(giftCardId))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].name.az").value("Hədiyyə qəhvə"));
        String code = json(cards, "$[0].code");
        assertThat(code).matches("^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$");
        // Kod sabitdir və bazada açıq saxlanılmır
        customer.get("/api/v1/me/gift-cards").andExpect(jsonPath("$[0].code").value(code));
        assertThat(count("SELECT count(*) FROM rewards WHERE id = CAST(:id AS uuid) AND code_lookup IS NOT NULL",
                Map.of("id", giftCardId))).isEqualTo(1);
        customer.get("/api/v1/me").andExpect(jsonPath("$.availableRewards[0].id").value(giftCardId));
    }

    @Test
    void staffApprovalModeKeepsStampPendingUntilConfirmed() throws Exception {
        UUID branchId = newBranch(new Policy(0, 50, true, 30, false, null));
        TestTag tag = newTag(branchId);
        CustomerClient customer = signedIn(uniquePhone());
        AdminClient barista = signedInBarista(branchId);
        AdminClient otherBarista = signedInBarista(newBranch(Policy.relaxed()));

        String stampId = json(customer.post(claimPath(tapToken(customer, tag, 1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currentStamps").value(0)), "$.stampId");
        customer.get("/api/v1/me").andExpect(jsonPath("$.card.pendingStamps").value(1))
                .andExpect(jsonPath("$.card.currentStamps").value(0));

        barista.get("/staff-console/pending-stamps?branchId=" + branchId).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(stampId))
                .andExpect(jsonPath("$[0].phoneMasked").exists());
        // Başqa filialın işçisi bu möhürü görmür və təsdiqləyə bilmir
        otherBarista.get("/staff-console/pending-stamps?branchId=" + branchId).andExpect(status().isNotFound());
        otherBarista.post("/staff-console/pending-stamps/" + stampId + "/confirm").andExpect(status().isNotFound());

        barista.post("/staff-console/pending-stamps/" + stampId + "/confirm").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        barista.post("/staff-console/pending-stamps/" + stampId + "/confirm").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAMP_NOT_PENDING"));
        customer.get("/api/v1/me").andExpect(jsonPath("$.card.pendingStamps").value(0))
                .andExpect(jsonPath("$.card.currentStamps").value(1));

        // Rədd: səbəb məcburidir
        String second = json(customer.post(claimPath(tapToken(customer, tag, 2))), "$.stampId");
        barista.post("/staff-console/pending-stamps/" + second + "/reject", Map.of("reason", " "))
                .andExpect(status().isBadRequest());
        barista.post("/staff-console/pending-stamps/" + second + "/reject", Map.of("reason", "Sifariş yox idi"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        customer.get("/api/v1/me").andExpect(jsonPath("$.card.currentStamps").value(1));
    }

    @Test
    void pendingStampsTimeOut() throws Exception {
        TestTag tag = newTag(newBranch(new Policy(0, 50, true, 5, false, null)));
        CustomerClient customer = signedIn(uniquePhone());
        String stampId = json(customer.post(claimPath(tapToken(customer, tag, 1))), "$.stampId");

        clock.advance(Duration.ofMinutes(6));
        TenantContext.runAs(alpha(), approvals::rejectTimedOut);

        assertThat(count("SELECT count(*) FROM stamps WHERE id = CAST(:id AS uuid) AND status = 'REJECTED' "
                + "AND revoke_reason = 'PENDING_TIMEOUT'", Map.of("id", stampId))).isEqualTo(1);
    }

    @Test
    void guestTapIsReservedDuringLoginAndClaimedAfterRegistration() throws Exception {
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient guest = client();
        String token = tapToken(guest, tag, 1);
        String phone = uniquePhone();

        int before = sms.to(phone).size();
        String challengeId = json(guest.post("/api/v1/auth/otp/request",
                        Map.of("phone", phone, "language", "az", "tapToken", token)).andExpect(status().isAccepted()),
                "$.challengeId");
        String code = TestSmsInbox.code(sms.await(phone, before + 1));
        guest.get("/api/v1/taps/" + token).andExpect(jsonPath("$.status").value("RESERVED"));

        // SMS gec gəldi: tokenin 60 saniyəsi keçib, rezerv isə 10 dəqiqədir
        clock.advance(Duration.ofMinutes(3));
        // Başqa hesab rezerv olunmuş tokeni istifadə edə bilmir (eyni brauzerdə olsa belə)
        CustomerClient other = signedIn(uniquePhone());
        other.setCookie(BrowserIdCookie.NAME, guest.cookie(BrowserIdCookie.NAME), "/");
        other.post(claimPath(token)).andExpect(jsonPath("$.code").value("TAP_TOKEN_USED"));

        ResultActions verify = guest.post("/api/v1/auth/otp/verify", Map.of("challengeId", challengeId, "code", code))
                .andExpect(jsonPath("$.status").value("REGISTRATION_REQUIRED"));
        guest.post("/api/v1/auth/register", Map.of(
                "registrationToken", json(verify, "$.registrationToken"),
                "acceptedDocuments", currentDocuments(guest),
                "language", "az")).andExpect(status().isCreated());

        guest.post(claimPath(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.currentStamps").value(1));
    }
}
