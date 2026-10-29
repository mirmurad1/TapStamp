package az.tapstamp.bonus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.TapIntegrationTest;
import az.tapstamp.support.TestPushInbox;
import az.tapstamp.support.TestPushInbox.Browser;
import az.tapstamp.tenancy.TenantContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Bonus qaydaları: uyğun gələn NFC möhürünə bonus möhür (ən böyük bonus, toplanmır), günə/filiala görə süzgəc, işçi
 * təsdiqi rejimində təsdiq anında; müştəri üçün siyahı, statistika, silmə/söndürmə və yoxlamalar. Hər test öz
 * filialına bağlı qaydalar yaradır — başqa testlərin möhürlərinə təsir etmir.
 */
class BonusRuleIT extends TapIntegrationTest {

    private AdminClient owner() throws Exception {
        return signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
    }

    private static Map<String, Object> rule(String name, int bonus, List<Integer> days, UUID branchId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", Map.of("az", name));
        body.put("bonusStamps", bonus);
        body.put("days", days);
        body.put("branchId", branchId);
        body.put("active", true);
        body.put("notifyOnStart", false);
        return body;
    }

    private String create(Map<String, Object> body) throws Exception {
        String json = owner().post("/bonus-rules", body).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(json, "$.id");
    }

    private int today() {
        return clock.instant().atZone(alpha().timezone()).getDayOfWeek().getValue();
    }

    private static int totalStamps(CustomerClient customer) throws Exception {
        String body = customer.get("/api/v1/me").andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Integer total = JsonPath.read(body, "$.card.totalStamps");
        return total;
    }

    private void claim(CustomerClient customer, TestTag tag, int counter) throws Exception {
        customer.post(claimPath(tapToken(customer, tag, counter))).andExpect(status().isOk());
    }

    @Test
    void matchingRuleAddsTheLargestBonusOnly() throws Exception {
        UUID branchId = newBranch(Policy.relaxed());
        TestTag tag = newTag(branchId);
        List<Integer> everyDay = List.of(1, 2, 3, 4, 5, 6, 7);
        create(rule("İkiqat möhür", 1, everyDay, branchId));
        String bigger = create(rule("Üçqat möhür", 2, everyDay, branchId));
        // Başqa filialın qaydası tətbiq olunmur
        create(rule("Başqa filial", 5, everyDay, newBranch(Policy.relaxed())));

        CustomerClient customer = signedIn(uniquePhone());
        claim(customer, tag, 1);
        assertThat(totalStamps(customer)).isEqualTo(3);

        String customerId = JsonPath.read(customer.get("/api/v1/me").andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8), "$.id");
        owner().get("/customers/" + customerId + "/timeline?size=50")
                .andExpect(jsonPath("$.items[?(@.source == 'BONUS')]").isNotEmpty());
        owner().get("/bonus-rules").andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + bigger + "')].bonusStamps30d")
                        .value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$[?(@.id == '" + bigger + "')].status")
                        .value(org.hamcrest.Matchers.contains("ACTIVE")));
        client().get("/api/v1/public/bonus-rules").andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + bigger + "')].runningNow")
                        .value(org.hamcrest.Matchers.contains(true)));
    }

    @Test
    void ruleForOtherDaysOrFutureDatesGivesNoBonus() throws Exception {
        UUID branchId = newBranch(Policy.relaxed());
        TestTag tag = newTag(branchId);
        int other = today() % 7 + 1;
        create(rule("Başqa gün", 1, List.of(other), branchId));
        Map<String, Object> future = rule("Gələcək kampaniya", 1, List.of(1, 2, 3, 4, 5, 6, 7), branchId);
        LocalDate tomorrow = clock.instant().atZone(alpha().timezone()).toLocalDate().plusDays(1);
        future.put("startsOn", tomorrow.toString());
        future.put("endsOn", tomorrow.plusDays(7).toString());
        String futureId = create(future);

        CustomerClient customer = signedIn(uniquePhone());
        claim(customer, tag, 1);
        assertThat(totalStamps(customer)).isEqualTo(1);
        owner().get("/bonus-rules").andExpect(jsonPath("$[?(@.id == '" + futureId + "')].status")
                .value(org.hamcrest.Matchers.contains("SCHEDULED")));
    }

    @Test
    void inApprovalModeBonusIsGrantedWhenStaffConfirms() throws Exception {
        UUID branchId = newBranch(new Policy(0, 50, true, 30, false, null));
        TestTag tag = newTag(branchId);
        create(rule("İkiqat möhür", 1, List.of(today()), branchId));

        CustomerClient customer = signedIn(uniquePhone());
        claim(customer, tag, 1);
        assertThat(totalStamps(customer)).isZero();
        AdminClient barista = signedInBarista(branchId);
        String pending = barista.get("/staff-console/pending-stamps?branchId=" + branchId)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String stampId = JsonPath.read(pending, "$.items[0].id");
        barista.post("/staff-console/pending-stamps/" + stampId + "/confirm").andExpect(status().isOk());
        assertThat(totalStamps(customer)).isEqualTo(2);
    }

    @Test
    void usedRuleIsDisabledInsteadOfDeleted() throws Exception {
        UUID branchId = newBranch(Policy.relaxed());
        TestTag tag = newTag(branchId);
        String unused = create(rule("İstifadəsiz", 1, List.of(today() % 7 + 1), branchId));
        String used = create(rule("İstifadə olunub", 1, List.of(today()), branchId));
        claim(signedIn(uniquePhone()), tag, 1);

        owner().delete("/bonus-rules/" + unused).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true));
        owner().delete("/bonus-rules/" + used).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(false));
        owner().get("/bonus-rules").andExpect(jsonPath("$[?(@.id == '" + unused + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.id == '" + used + "')].status").value(org.hamcrest.Matchers.contains("OFF")));
        client().get("/api/v1/public/bonus-rules")
                .andExpect(jsonPath("$[?(@.id == '" + used + "')]").isEmpty());
    }

    @Autowired
    private BonusRuleService bonusRules;

    @Autowired
    private TestPushInbox inbox;

    @Test
    void startNotificationIsSentRightAwayAndOnlyOnce() throws Exception {
        // Saatı şirkətin 23:00-na gətirir: kampaniya bildirişi saatdan asılı olmayaraq gedir (gecə də).
        // Girişdən əvvəl — əks halda sessiyanın vaxtı keçər
        LocalTime local = clock.instant().atZone(alpha().timezone()).toLocalTime();
        long minutes = Duration.between(local, LocalTime.of(23, 0)).toMinutes();
        clock.advance(Duration.ofMinutes(minutes < 0 ? minutes + 24 * 60 : minutes));
        CustomerClient customer = signedIn(uniquePhone());
        Browser browser = inbox.newBrowser();
        customer.post("/api/v1/me/push-subscriptions", Map.of("endpoint", browser.endpoint(),
                "keys", Map.of("p256dh", browser.p256dh(), "auth", browser.authKey()))).andExpect(status().isNoContent());
        // Bu gün başlayan qayda — bildiriş yaradılan kimi, fon işini gözləmədən
        Map<String, Object> body = rule("Novruz həftəsi", 1, List.of(1, 2, 3, 4, 5, 6, 7), newBranch(Policy.relaxed()));
        body.put("notifyOnStart", true);
        String id = create(body);
        String title = JsonPath.read(TestPushInbox.decrypt(browser, inbox.await(browser.endpoint(), 1).body()), "$.title");
        assertThat(title).isEqualTo("Novruz həftəsi başladı 🎉");

        // Fon işi onu təkrar göndərmir
        TenantContext.callAs(alpha(), bonusRules::notifyStartedForCurrentTenant);
        Thread.sleep(300);
        assertThat(inbox.to(browser.endpoint())).hasSize(1);
        owner().get("/bonus-rules").andExpect(jsonPath("$[?(@.id == '" + id + "')].notifiedAt").isNotEmpty());

        // Çatdırılma hesabatı: nəticə arxa planda yazılır
        String customerId = JsonPath.read(customer.get("/api/v1/me").andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8), "$.id");
        String deliveries = "";
        for (int i = 0; i < 50 && !deliveries.contains("DELIVERED"); i++) {
            Thread.sleep(100);
            deliveries = owner().get("/bonus-rules/" + id + "/deliveries?status=DELIVERED&size=50").andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        }
        assertThat(JsonPath.<java.util.List<String>>read(deliveries, "$.items[*].customerId")).contains(customerId);
        owner().get("/bonus-rules").andExpect(jsonPath("$[?(@.id == '" + id + "')].notifyDelivered")
                .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));
        owner().get("/bonus-rules/" + id + "/deliveries?status=WRONG").andExpect(status().isBadRequest());
    }

    @Test
    void rulesAreValidatedAndOnlyForOwner() throws Exception {
        UUID branchId = newBranch(Policy.relaxed());
        Map<String, Object> tooBig = rule("Çox", 6, List.of(1), branchId);
        owner().post("/bonus-rules", tooBig).andExpect(status().isBadRequest());
        Map<String, Object> noDays = rule("Günsüz", 1, List.of(), branchId);
        owner().post("/bonus-rules", noDays).andExpect(status().isBadRequest());
        Map<String, Object> halfTime = rule("Yarım saat", 1, List.of(1), branchId);
        halfTime.put("startTime", "14:00");
        owner().post("/bonus-rules", halfTime).andExpect(status().isBadRequest());
        Map<String, Object> reversed = rule("Tərs saat", 1, List.of(1), branchId);
        reversed.put("startTime", "17:00");
        reversed.put("endTime", "14:00");
        owner().post("/bonus-rules", reversed).andExpect(status().isBadRequest());
        Map<String, Object> unknownBranch = rule("Naməlum filial", 1, List.of(1), UUID.randomUUID());
        owner().post("/bonus-rules", unknownBranch).andExpect(status().isBadRequest());

        signedInStaff(HOST, AdminRole.BRANCH_MANAGER, branchId).get("/bonus-rules").andExpect(status().isForbidden());
    }
}
