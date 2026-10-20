package az.tapstamp.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Avtomatik xatırlatmalar: yalnız push-u açıq müştərilərə, yalnız açılmış növlər, 10:00–20:00, həftədə bir və hər
 * dövrdə bir dəfə; mətnlər müştərinin dilində.
 */
class ReminderIT extends TapIntegrationTest {

    @Autowired
    private ReminderService reminders;

    @Autowired
    private TestPushInbox inbox;

    private AdminClient owner() throws Exception {
        return signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
    }

    private void settings(boolean nearReward, boolean winback, int winbackDays, boolean unusedGift) throws Exception {
        owner().put("/reminders/settings", Map.of("nearRewardEnabled", nearReward, "winbackEnabled", winback,
                "winbackDays", winbackDays, "unusedGiftEnabled", unusedGift)).andExpect(status().isOk());
    }

    @AfterEach
    void disable() throws Exception {
        settings(false, false, 30, false);
    }

    /** Müştəri: push abunəsi, kartda verilmiş sayda möhür, son gəliş — {@code daysAgo} gün əvvəl. */
    private Browser customer(int stamps, int daysAgo) throws Exception {
        CustomerClient client = signedIn(uniquePhone());
        Browser browser = inbox.newBrowser();
        client.post("/api/v1/me/push-subscriptions", Map.of("endpoint", browser.endpoint(),
                "keys", Map.of("p256dh", browser.p256dh(), "auth", browser.authKey()))).andExpect(status().isNoContent());
        String body = client.get("/api/v1/me").andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        Instant lastVisit = clock.instant().minus(Duration.ofDays(daysAgo));
        TenantContext.runAs(alpha(), () -> {
            tenantJdbc.sql("UPDATE customers SET last_visit_at = :at, registered_at = :at WHERE id = :id")
                    .params(Map.of("at", java.sql.Timestamp.from(lastVisit), "id", id)).update();
            tenantJdbc.sql("""
                    INSERT INTO loyalty_accounts (id, customer_id, current_stamps, total_stamps, created_at, updated_at)
                    VALUES (gen_random_uuid(), :id, :stamps, :stamps, now(), now())
                    ON CONFLICT (customer_id) DO UPDATE SET current_stamps = :stamps""")
                    .params(Map.of("id", id, "stamps", stamps)).update();
        });
        return browser;
    }

    private int requiredStamps() {
        return count("SELECT stamps_required FROM reward_rules WHERE active", Map.of());
    }

    /** Saatı şirkətin 12:00-na gətirib bir addım işlədir. */
    private int run() {
        LocalTime local = clock.instant().atZone(alpha().timezone()).toLocalTime();
        if (local.isBefore(LocalTime.of(11, 0)) || !local.isBefore(LocalTime.of(19, 0))) {
            long minutes = Duration.between(local, LocalTime.of(12, 0)).toMinutes();
            clock.advance(Duration.ofMinutes(minutes < 0 ? minutes + 24 * 60 : minutes));
        }
        return TenantContext.callAs(alpha(), reminders::runForCurrentTenant);
    }

    private String title(Browser browser, int count) throws Exception {
        return JsonPath.read(TestPushInbox.decrypt(browser, inbox.await(browser.endpoint(), count).body()), "$.title");
    }

    @Test
    void nearRewardReminderIsSentOncePerCycleAndOnlyWhenEnabled() throws Exception {
        int required = requiredStamps();
        Browser near = customer(required - 1, 8);
        Browser recent = customer(required - 1, 2);
        run();
        assertThat(inbox.to(near.endpoint())).isEmpty();

        settings(true, false, 30, false);
        run();
        assertThat(title(near, 1)).isEqualTo("Hədiyyəyə az qalıb 🎁");
        // 7 gündən az əvvəl gəlib — hələ yox
        assertThat(inbox.to(recent.endpoint())).isEmpty();

        // Eyni dövrdə təkrar getmir (bir həftə sonra da)
        clock.advance(Duration.ofDays(8));
        run();
        Thread.sleep(300);
        assertThat(inbox.to(near.endpoint())).hasSize(1);
    }

    @Test
    void winbackUsesTheEmptyCardTextAndRespectsTheWeeklyLimit() throws Exception {
        Browser empty = customer(0, 31);
        Browser withStamps = customer(1, 31);
        settings(false, true, 30, false);
        run();
        assertThat(title(empty, 1)).isEqualTo("Sizi yenidən görmək istərdik 👋");
        String body = JsonPath.read(TestPushInbox.decrypt(empty, inbox.await(empty.endpoint(), 1).body()), "$.body");
        assertThat(body).startsWith("Növbəti ziyarətinizdə");
        String withBody = JsonPath.read(TestPushInbox.decrypt(withStamps, inbox.await(withStamps.endpoint(), 1).body()),
                "$.body");
        assertThat(withBody).startsWith("Kartınızda 1/");

        // Eyni həftədə ikinci xatırlatma yoxdur (hədiyyəyə az qalıb açılsa da)
        settings(true, true, 30, false);
        run();
        Thread.sleep(300);
        assertThat(inbox.to(withStamps.endpoint())).hasSize(1);

        owner().get("/reminders/stats").andExpect(status().isOk())
                .andExpect(jsonPath("$.customersWithPush").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.kinds[?(@.kind == 'WINBACK')].sent")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.greaterThanOrEqualTo(2))));
    }

    @Test
    void unusedGiftReminderOpensTheGiftCardsPage() throws Exception {
        Browser browser = customer(0, 8);
        UUID customerId = TenantContext.callAs(alpha(), () -> tenantJdbc.sql(
                "SELECT customer_id FROM push_subscriptions WHERE endpoint = :e").param("e", browser.endpoint())
                .query(UUID.class).single());
        TenantContext.runAs(alpha(), () -> tenantJdbc.sql("""
                INSERT INTO rewards (id, customer_id, rule_id, stamps_required_snapshot, reward_name_snapshot, status,
                                     earned_at, created_at)
                SELECT gen_random_uuid(), :id, id, stamps_required, reward_name, 'AVAILABLE', now(), now()
                FROM reward_rules WHERE active""").param("id", customerId).update());
        settings(false, false, 30, true);
        run();
        String payload = TestPushInbox.decrypt(browser, inbox.await(browser.endpoint(), 1).body());
        assertThat((String) JsonPath.read(payload, "$.title")).isEqualTo("Hədiyyəniz sizi gözləyir 🎁");
        assertThat((String) JsonPath.read(payload, "$.url")).isEqualTo("/gift-cards");
    }

    @Test
    void settingsAreValidatedAndOnlyForOwner() throws Exception {
        owner().put("/reminders/settings", Map.of("nearRewardEnabled", true, "winbackEnabled", true,
                "winbackDays", 45, "unusedGiftEnabled", true)).andExpect(status().isBadRequest());
        signedInStaff(HOST, AdminRole.BRANCH_MANAGER, newBranch(Policy.relaxed()))
                .get("/reminders/settings").andExpect(status().isForbidden());
    }
}
