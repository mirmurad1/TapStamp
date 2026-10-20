package az.tapstamp.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.support.AdminClient;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.TapIntegrationTest;
import az.tapstamp.support.TestPushInbox;
import az.tapstamp.support.TestPushInbox.Browser;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Web push: abunə (yalnız tanınmış push xidmətləri), işçi möhürü təsdiqləyəndə şifrəli və VAPID imzalı bildiriş,
 * etibarsız abunənin silinməsi, söndürmə.
 */
class PushIT extends TapIntegrationTest {

    @Autowired
    private TestPushInbox inbox;

    private static Map<String, Object> subscription(Browser browser) {
        return Map.of("endpoint", browser.endpoint(), "keys", Map.of("p256dh", browser.p256dh(), "auth", browser.authKey()));
    }

    private String pendingStampId(AdminClient barista, UUID branchId) throws Exception {
        String body = barista.get("/staff-console/pending-stamps?branchId=" + branchId).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.items[0].id");
    }

    @Test
    void customerGetsAnEncryptedNotificationWhenStaffConfirmsTheStamp() throws Exception {
        client().get("/api/v1/public/push").andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.publicKey").isString());
        UUID branchId = newBranch(new Policy(0, 50, true, 30, false, null));
        TestTag tag = newTag(branchId);
        CustomerClient customer = signedIn(uniquePhone());
        Browser browser = inbox.newBrowser();

        // Yalnız tanınmış push xidmətləri (SSRF qorunması) və düzgün açarlar
        customer.post("/api/v1/me/push-subscriptions", Map.of("endpoint", "https://evil.example/push",
                "keys", Map.of("p256dh", browser.p256dh(), "auth", browser.authKey()))).andExpect(status().isBadRequest());
        customer.post("/api/v1/me/push-subscriptions", Map.of("endpoint", browser.endpoint(),
                "keys", Map.of("p256dh", "short", "auth", browser.authKey()))).andExpect(status().isBadRequest());
        customer.post("/api/v1/me/push-subscriptions", subscription(browser)).andExpect(status().isNoContent());

        customer.post(claimPath(tapToken(customer, tag, 1))).andExpect(status().isOk());
        AdminClient barista = signedInBarista(branchId);
        barista.post("/staff-console/pending-stamps/" + pendingStampId(barista, branchId) + "/confirm")
                .andExpect(status().isOk());

        TestPushInbox.Delivery delivery = inbox.await(browser.endpoint(), 1);
        assertThat(delivery.headers()).containsEntry("Content-Encoding", "aes128gcm");
        assertThat(delivery.headers().get("Authorization")).startsWith("vapid t=").contains(", k=");
        String payload = TestPushInbox.decrypt(browser, delivery.body());
        assertThat((String) JsonPath.read(payload, "$.title")).isEqualTo("Möhür təsdiqləndi ✅");
        assertThat((String) JsonPath.read(payload, "$.body")).startsWith("Kartınızda 1/");
        assertThat((String) JsonPath.read(payload, "$.url")).isEqualTo("/");
    }

    @Test
    void goneSubscriptionsAreRemovedAndCustomerCanTurnNotificationsOff() throws Exception {
        UUID branchId = newBranch(new Policy(0, 50, true, 30, false, null));
        TestTag tag = newTag(branchId);
        CustomerClient customer = signedIn(uniquePhone());
        Browser gone = inbox.newBrowser();
        Browser off = inbox.newBrowser();
        customer.post("/api/v1/me/push-subscriptions", subscription(gone)).andExpect(status().isNoContent());
        customer.post("/api/v1/me/push-subscriptions", subscription(off)).andExpect(status().isNoContent());
        customer.post("/api/v1/me/push-subscriptions/remove", Map.of("endpoint", off.endpoint()))
                .andExpect(status().isNoContent());
        inbox.respond(gone.endpoint(), 410);
        AdminClient barista = signedInBarista(branchId);

        customer.post(claimPath(tapToken(customer, tag, 1))).andExpect(status().isOk());
        barista.post("/staff-console/pending-stamps/" + pendingStampId(barista, branchId) + "/confirm")
                .andExpect(status().isOk());
        inbox.await(gone.endpoint(), 1);
        // 410 — abunə silinib: ikinci bildiriş həmin ünvana getmir
        customer.post(claimPath(tapToken(customer, tag, 2))).andExpect(status().isOk());
        barista.post("/staff-console/pending-stamps/" + pendingStampId(barista, branchId) + "/confirm")
                .andExpect(status().isOk());
        Thread.sleep(500);
        assertThat(inbox.to(gone.endpoint())).hasSize(1);
        assertThat(inbox.to(off.endpoint())).isEmpty();
        assertThat(count("SELECT count(*) FROM push_subscriptions WHERE endpoint = :e", Map.of("e", gone.endpoint())))
                .isZero();
    }
}
