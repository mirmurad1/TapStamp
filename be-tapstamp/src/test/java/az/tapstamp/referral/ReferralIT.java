package az.tapstamp.referral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.TapIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Dost dəvəti: kod → yeni müştərinin qeydiyyatı → dostun ilk NFC möhürü → hər ikisinə bonus (bir dəfə). Qorunma:
 * köhnə müştəri, etibarsız kod, söndürülmüş proqram, aylıq limit, müddətin bitməsi; işçi təsdiqi rejimində bonus
 * təsdiq anında verilir.
 */
class ReferralIT extends TapIntegrationTest {

    private AdminClient owner() throws Exception {
        return signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
    }

    private void settings(boolean enabled, int referrerStamps, int friendStamps, int monthlyCap, int validDays)
            throws Exception {
        settings(enabled, "FIRST_VISIT", referrerStamps, friendStamps, monthlyCap, validDays);
    }

    private void settings(boolean enabled, String rewardOn, int referrerStamps, int friendStamps, int monthlyCap,
                          int validDays) throws Exception {
        owner().put("/referrals/settings", Map.of("enabled", enabled, "rewardOn", rewardOn, "referrerStamps", referrerStamps,
                        "friendStamps", friendStamps, "monthlyCap", monthlyCap, "validDays", validDays))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(enabled));
    }

    @AfterEach
    void disable() throws Exception {
        settings(false, 1, 1, 5, 30);
    }

    private static String referralCode(CustomerClient customer) throws Exception {
        return read(customer, "/api/v1/me/referral", "$.code");
    }

    private static int totalStamps(CustomerClient customer) throws Exception {
        Integer total = read(customer, "/api/v1/me", "$.card.totalStamps");
        return total;
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
    void friendsFirstVisitRewardsBothOnce() throws Exception {
        settings(true, 2, 1, 5, 30);
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient referrer = signedIn(uniquePhone());
        String code = referralCode(referrer);
        assertThat(code).matches("[A-Z2-9]{8}");
        assertThat(referralCode(referrer)).isEqualTo(code);
        client().get("/api/v1/public/referral-program").andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.friendStamps").value(1));

        // Kiçik hərflə də qəbul olunur
        CustomerClient friend = signedIn(uniquePhone(), code.toLowerCase(java.util.Locale.ROOT));
        referrer.get("/api/v1/me/referral").andExpect(jsonPath("$.invited").value(1))
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.rewarded").value(0));
        assertThat(totalStamps(friend)).isZero();
        int referrerBefore = totalStamps(referrer);

        // Dostun ilk NFC möhürü: 1 (toxunuş) + 1 (bonus); dəvət edənə 2 bonus
        claim(friend, tag, 1);
        assertThat(totalStamps(friend)).isEqualTo(2);
        assertThat(totalStamps(referrer)).isEqualTo(referrerBefore + 2);
        referrer.get("/api/v1/me/referral").andExpect(jsonPath("$.rewarded").value(1))
                .andExpect(jsonPath("$.pending").value(0));

        // İkinci gəliş — bonus təkrarlanmır
        claim(friend, tag, 2);
        assertThat(totalStamps(friend)).isEqualTo(3);
        assertThat(totalStamps(referrer)).isEqualTo(referrerBefore + 2);

        AdminClient owner = owner();
        String friendId = read(friend, "/api/v1/me", "$.id");
        owner.get("/referrals?size=50").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.friendId == '" + friendId + "')].status")
                        .value(org.hamcrest.Matchers.contains("REWARDED")));
        owner.get("/referrals/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.rewarded").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        owner.get("/customers/" + friendId + "/timeline?size=50")
                .andExpect(jsonPath("$.items[?(@.source == 'REFERRAL')]").isNotEmpty());
    }

    @Test
    void onlyNewCustomersWithValidCodeAndWithinLimitsAreRewarded() throws Exception {
        settings(true, 1, 1, 1, 30);
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient referrer = signedIn(uniquePhone());
        String code = referralCode(referrer);

        // Köhnə müştəri dəvət oluna bilməz
        String existingPhone = uniquePhone();
        signedIn(existingPhone);
        CustomerClient existing = signedIn(existingPhone, code);
        // Etibarsız kod qeydiyyata mane olmur
        CustomerClient invalid = signedIn(uniquePhone(), "ZZZZZZZZ");
        // Aylıq limit 1: birinci dost sayılır, ikincisi LIMIT_REACHED
        CustomerClient first = signedIn(uniquePhone(), code);
        CustomerClient second = signedIn(uniquePhone(), code);
        referrer.get("/api/v1/me/referral").andExpect(jsonPath("$.invited").value(1));

        claim(existing, tag, 1);
        claim(invalid, tag, 2);
        claim(second, tag, 3);
        assertThat(totalStamps(existing)).isEqualTo(1);
        assertThat(totalStamps(invalid)).isEqualTo(1);
        assertThat(totalStamps(second)).isEqualTo(1);
        claim(first, tag, 4);
        assertThat(totalStamps(first)).isEqualTo(2);

        String secondId = read(second, "/api/v1/me", "$.id");
        owner().get("/referrals?size=50")
                .andExpect(jsonPath("$.items[?(@.friendId == '" + secondId + "')].status")
                        .value(org.hamcrest.Matchers.contains("LIMIT_REACHED")));

        // Proqram söndürülübsə, kod nəzərə alınmır
        settings(false, 1, 1, 5, 30);
        CustomerClient late = signedIn(uniquePhone(), code);
        late.get("/api/v1/me/referral").andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.code").doesNotExist());
        settings(true, 1, 1, 5, 30);
        claim(late, tag, 5);
        assertThat(totalStamps(late)).isEqualTo(1);
    }

    @Test
    void invitationExpiresWhenFriendDoesNotComeInTime() throws Exception {
        settings(true, 1, 1, 5, 7);
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient referrer = signedIn(uniquePhone());
        String friendPhone = uniquePhone();
        signedIn(friendPhone, referralCode(referrer));

        clock.advance(Duration.ofDays(8));
        // Uzun fasilədən sonra yenidən daxil olur
        CustomerClient friend = signedIn(friendPhone);
        claim(friend, tag, 1);
        assertThat(totalStamps(friend)).isEqualTo(1);
        String friendId = read(friend, "/api/v1/me", "$.id");
        owner().get("/referrals?size=50")
                .andExpect(jsonPath("$.items[?(@.friendId == '" + friendId + "')].status")
                        .value(org.hamcrest.Matchers.contains("EXPIRED")));
    }

    @Test
    void inApprovalModeBonusIsGrantedWhenStaffConfirms() throws Exception {
        settings(true, 1, 2, 5, 30);
        UUID branchId = newBranch(new Policy(0, 50, true, 30, false, null));
        TestTag tag = newTag(branchId);
        CustomerClient referrer = signedIn(uniquePhone());
        CustomerClient friend = signedIn(uniquePhone(), referralCode(referrer));

        claim(friend, tag, 1);
        assertThat(totalStamps(friend)).isZero();
        AdminClient barista = signedInBarista(branchId);
        String pending = barista.get("/staff-console/pending-stamps?branchId=" + branchId)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String stampId = JsonPath.read(pending, "$.items[0].id");
        barista.post("/staff-console/pending-stamps/" + stampId + "/confirm").andExpect(status().isOk());
        assertThat(totalStamps(friend)).isEqualTo(3);
        referrer.get("/api/v1/me/referral").andExpect(jsonPath("$.rewarded").value(1));
    }

    @Test
    void dashboardCountsInvitedCameAndReturnedFriends() throws Exception {
        settings(true, 1, 1, 50, 30);
        UUID branchId = newBranch(Policy.relaxed());
        TestTag tag = newTag(branchId);
        CustomerClient referrer = signedIn(uniquePhone());
        String code = referralCode(referrer);
        CustomerClient once = signedIn(uniquePhone(), code);
        String returningPhone = uniquePhone();
        CustomerClient returning = signedIn(returningPhone, code);
        CustomerClient absent = signedIn(uniquePhone(), code);
        String returningId = read(returning, "/api/v1/me", "$.id");
        String absentId = read(absent, "/api/v1/me", "$.id");

        claim(once, tag, 1);
        claim(returning, tag, 2);
        // Eyni gün ikinci möhür "qayıtmaq" sayılmır; növbəti gün sayılır
        claim(returning, tag, 3);
        clock.advance(Duration.ofDays(1));
        // Bir gündən sonra müştəri yenidən daxil olur (access token-in ömrü bitib)
        claim(signedIn(returningPhone), tag, 4);

        // Yalnız bu filialın gəlişləri: bu testin dostları
        AdminClient owner = owner();
        owner.get("/referrals/dashboard?branchId=" + branchId).andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.came").value(2))
                .andExpect(jsonPath("$.stats.returned").value(1))
                .andExpect(jsonPath("$.stats.visits").value(4))
                .andExpect(jsonPath("$.stats.invited").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.topReferrers[0].came").value(2))
                .andExpect(jsonPath("$.topReferrers[0].returned").value(1));

        owner.get("/referrals?activity=RETURNED&size=50")
                .andExpect(jsonPath("$.items[?(@.friendId == '" + returningId + "')].visits")
                        .value(org.hamcrest.Matchers.contains(3)))
                .andExpect(jsonPath("$.items[?(@.friendId == '" + absentId + "')]").isEmpty());
        owner.get("/referrals?activity=NONE&size=50")
                .andExpect(jsonPath("$.items[?(@.friendId == '" + absentId + "')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.friendId == '" + returningId + "')]").isEmpty());
        owner.get("/referrals?activity=SOMETIMES").andExpect(status().isBadRequest());
        signedInStaff(HOST, AdminRole.BRANCH_MANAGER, branchId).get("/referrals/dashboard")
                .andExpect(status().isForbidden());
    }

    @Test
    void companyCanGrantTheBonusRightAtRegistration() throws Exception {
        settings(true, "REGISTRATION", 2, 1, 5, 30);
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient referrer = signedIn(uniquePhone());
        referrer.get("/api/v1/me/referral").andExpect(jsonPath("$.rewardOn").value("REGISTRATION"));
        int referrerBefore = totalStamps(referrer);

        // Qeydiyyat anında: dosta 1, dəvət edənə 2 bonus
        CustomerClient friend = signedIn(uniquePhone(), referralCode(referrer));
        assertThat(totalStamps(friend)).isEqualTo(1);
        assertThat(totalStamps(referrer)).isEqualTo(referrerBefore + 2);
        referrer.get("/api/v1/me/referral").andExpect(jsonPath("$.rewarded").value(1));

        // İlk gəlişdə bonus təkrarlanmır
        claim(friend, tag, 1);
        assertThat(totalStamps(friend)).isEqualTo(2);
        assertThat(totalStamps(referrer)).isEqualTo(referrerBefore + 2);
    }

    @Test
    void settingsAreValidatedAndOnlyForOwner() throws Exception {
        owner().put("/referrals/settings", Map.of("enabled", true, "rewardOn", "FIRST_VISIT", "referrerStamps", 4,
                "friendStamps", 1, "monthlyCap", 5, "validDays", 30)).andExpect(status().isBadRequest());
        owner().put("/referrals/settings", Map.of("enabled", true, "rewardOn", "ANYTIME", "referrerStamps", 1,
                "friendStamps", 1, "monthlyCap", 5, "validDays", 30)).andExpect(status().isBadRequest());
        signedInStaff(HOST, AdminRole.BRANCH_MANAGER, newBranch(Policy.relaxed()))
                .get("/referrals/settings").andExpect(status().isForbidden());
    }
}
