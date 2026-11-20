package az.tapstamp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.plan.PlanService;
import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.TapIntegrationTest;
import az.tapstamp.support.TestPushInbox;
import az.tapstamp.support.TestPushInbox.Browser;
import az.tapstamp.tenancy.TenantContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Endirim bildirişləri: bir bildirişdə bir neçə endirim, gündə bir elan, hər endirim bir dəfə, planlaşdırılmış endirim
 * başladığı gün, filiala aid endirim yalnız o filialın müştərisinə, son gün xatırlatması,
 * açılışın sayılması, 10:00–20:00 və saatın yoxlanması.
 */
class DealNotificationIT extends TapIntegrationTest {

    @Autowired
    @Qualifier("platformJdbc")
    private JdbcClient platformJdbc;

    @Autowired
    private PlanService plans;

    @Autowired
    private DealNotificationService deals;

    @Autowired
    private TestPushInbox inbox;

    @BeforeEach
    void enableAddon() {
        platformJdbc.sql("INSERT INTO tenant_addons (tenant_id, addon, enabled_at) VALUES (:id, 'CATALOG', now()) "
                + "ON CONFLICT DO NOTHING").param("id", alpha().id()).update();
        plans.evictCaches();
        cleanTables();
    }

    @AfterEach
    void cleanUp() {
        cleanTables();
        platformJdbc.sql("DELETE FROM tenant_addons WHERE tenant_id = :id").param("id", alpha().id()).update();
        plans.evictCaches();
    }

    private void cleanTables() {
        TenantContext.runAs(alpha(), () -> {
            tenantJdbc.sql("DELETE FROM deal_push_log").update();
            tenantJdbc.sql("DELETE FROM deal_announcements").update();
            tenantJdbc.sql("""
                    UPDATE deal_notification_settings
                    SET auto_enabled = true, send_time = '12:00', last_day_enabled = true, last_auto_run_on = NULL""")
                    .update();
            tenantJdbc.sql("DELETE FROM catalog_items").update();
            tenantJdbc.sql("DELETE FROM catalog_categories").update();
            tenantJdbc.sql("UPDATE catalog_settings SET enabled = true, title = 'MENU'").update();
        });
    }

    private AdminClient owner() throws Exception {
        return signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
    }

    private record Customer(CustomerClient client, Browser browser) {
    }

    private Customer customer() throws Exception {
        CustomerClient client = signedIn(uniquePhone());
        Browser browser = inbox.newBrowser();
        client.post("/api/v1/me/push-subscriptions", Map.of("endpoint", browser.endpoint(),
                "keys", Map.of("p256dh", browser.p256dh(), "auth", browser.authKey()))).andExpect(status().isNoContent());
        return new Customer(client, browser);
    }

    /** Saatı şirkətin verilmiş saatına gətirir (lazım olsa növbəti günə keçir). */
    private void at(LocalTime target) {
        LocalTime local = clock.instant().atZone(alpha().timezone()).toLocalTime();
        long minutes = Duration.between(local, target).toMinutes();
        clock.advance(Duration.ofMinutes(minutes <= 0 ? minutes + 24 * 60 : minutes));
    }

    private LocalDate today() {
        return clock.instant().atZone(alpha().timezone()).toLocalDate();
    }

    private String category(AdminClient owner) throws Exception {
        return JsonPath.read(body(owner.post("/catalog/categories", Map.of("name", Map.of("az", "Kofe")))),
                "$.categories[0].id");
    }

    private void item(AdminClient owner, String category, String name, Object price, Map<String, Object> discount,
                      UUID branchId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", category);
        body.put("name", Map.of("az", name, "en", name + " EN"));
        body.put("price", price);
        body.put("discount", discount);
        body.put("branchId", branchId);
        owner.post("/catalog/items", body).andExpect(status().isCreated());
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String field(Browser browser, Delivery delivery, String path) throws Exception {
        return JsonPath.read(TestPushInbox.decrypt(browser, delivery.body()), path);
    }

    private record Delivery(byte[] body) {
    }

    private Delivery push(Customer customer, int count) throws Exception {
        return new Delivery(inbox.await(customer.browser().endpoint(), count).body());
    }

    @Test
    void newDiscountsGoOutInOneNotificationOncePerDayAndScheduledOnesOnTheirStartDay() throws Exception {
        AdminClient owner = owner();
        String category = category(owner);
        UUID branch = TenantContext.callAs(alpha(), () -> tenantJdbc.sql("SELECT id FROM branches WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1")
                .query(UUID.class).single());
        at(LocalTime.of(9, 30));
        Customer customer = customer();
        Customer other = customer();

        item(owner, category, "Latte", 5, Map.of("kind", "PERCENT", "value", 20), null);
        item(owner, category, "Kapuçino", "4.50", Map.of("kind", "PRICE", "value", "3.60"), null);
        item(owner, category, "Mokko", 6, Map.of("kind", "PERCENT", "value", 10), null);
        item(owner, category, "Espresso", 3, Map.of("kind", "PERCENT", "value", 10,
                "startsOn", today().plusDays(1).toString()), null);
        // Yalnız bir filialda: bu müştəri o filiala gəlməyib
        item(owner, category, "Filial deserti", 8, Map.of("kind", "PERCENT", "value", 25), branch);

        // Önizləmə sahibin dilində
        owner.get("/catalog/deal-notifications").andExpect(status().isOk())
                .andExpect(jsonPath("$.pending.length()").value(4))
                .andExpect(jsonPath("$.preview.title").value("4 yeni endirim 🎉"))
                .andExpect(jsonPath("$.sentToday").value(false));

        at(LocalTime.of(10, 30));
        owner.post("/catalog/deal-notifications/send", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));
        Delivery first = push(customer, 1);
        assertThat(field(customer.browser(), first, "$.title")).isEqualTo("3 yeni endirim 🎉");
        assertThat(field(customer.browser(), first, "$.body"))
                .isEqualTo("Latte −20%, Kapuçino −20% və daha 1 təklif. Menyuya baxın!");
        String url = field(customer.browser(), first, "$.url");
        assertThat(url).startsWith("/menu?deal=");
        assertThat(field(other.browser(), push(other, 1), "$.title")).isEqualTo("3 yeni endirim 🎉");

        // Gündə bir elan: yeni endirim əlavə olunsa da bu gün ikinci bildiriş yoxdur
        item(owner, category, "Çizkeyk", 7, Map.of("kind", "PERCENT", "value", 15), null);
        owner.post("/catalog/deal-notifications/send", Map.of())
                .andExpect(jsonPath("$.status").value("ALREADY_SENT_TODAY"));
        at(LocalTime.of(12, 5));
        TenantContext.callAs(alpha(), deals::runForCurrentTenant);

        // Açılış sayılır (bir dəfə)
        String announcement = url.substring("/menu?deal=".length());
        customer.client().post("/api/v1/me/deal-notifications/" + announcement + "/opened", Map.of())
                .andExpect(status().isNoContent());
        customer.client().post("/api/v1/me/deal-notifications/" + announcement + "/opened", Map.of())
                .andExpect(status().isNoContent());
        owner.get("/catalog/deal-notifications")
                .andExpect(jsonPath("$.sentToday").value(true))
                .andExpect(jsonPath("$.pending.length()").value(1))
                .andExpect(jsonPath("$.recent[0].kind").value("NEW"))
                .andExpect(jsonPath("$.recent[0].trigger").value("MANUAL"))
                .andExpect(jsonPath("$.recent[0].opened").value(1));

        // Ertəsi gün avtomatik: dünən əlavə olunan və bu gün başlayan endirim; artıq elan olunanlar təkrar getmir
        at(LocalTime.of(11, 55));
        TenantContext.callAs(alpha(), deals::runForCurrentTenant);
        Thread.sleep(300);
        assertThat(inbox.to(customer.browser().endpoint())).hasSize(1);
        at(LocalTime.of(12, 0));
        assertThat(TenantContext.callAs(alpha(), deals::runForCurrentTenant).status())
                .isEqualTo(DealNotificationService.SendStatus.SENT);
        Delivery second = push(customer, 2);
        assertThat(field(customer.browser(), second, "$.title")).isEqualTo("2 yeni endirim 🎉");
        assertThat(field(customer.browser(), second, "$.body")).isEqualTo("Espresso −10%, Çizkeyk −15%. Menyuya baxın!");
        // Eyni gün ikinci yoxlama heç nə etmir
        assertThat(TenantContext.callAs(alpha(), deals::runForCurrentTenant).status())
                .isEqualTo(DealNotificationService.SendStatus.NOTHING_TO_SEND);
    }

    @Test
    void lastDayReminderGoesOnceWhenThereIsNothingNew() throws Exception {
        AdminClient owner = owner();
        String category = category(owner);
        at(LocalTime.of(11, 0));
        Customer customer = customer();
        item(owner, category, "Latte", 5, Map.of("kind", "PERCENT", "value", 30,
                "endsOn", today().plusDays(1).toString()), null);
        owner.post("/catalog/deal-notifications/send", Map.of()).andExpect(jsonPath("$.status").value("SENT"));
        push(customer, 1);

        // Ertəsi gün (endirimin son günü), yeni endirim yoxdur
        clock.advance(Duration.ofDays(1));
        at(LocalTime.of(12, 0));
        TenantContext.callAs(alpha(), deals::runForCurrentTenant);
        Delivery lastDay = push(customer, 2);
        assertThat(field(customer.browser(), lastDay, "$.title")).isEqualTo("Son gün ⏰");
        assertThat(field(customer.browser(), lastDay, "$.body"))
                .isEqualTo("Bu gün endirimin son günüdür: Latte −30%. Fürsəti qaçırmayın!");

        // Söndürülübsə son gün xatırlatması getmir
        owner.put("/catalog/deal-notifications/settings", Map.of("autoEnabled", true, "sendTime", "12:30",
                "lastDayEnabled", false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.sendTime").value("12:30"));
    }

    @Test
    void sendTimeMustBeWithinTheDaytimeWindow() throws Exception {
        AdminClient owner = owner();
        for (String time : new String[] {"12:15", "7:00", "24:00"}) {
            owner.put("/catalog/deal-notifications/settings", Map.of("autoEnabled", true, "sendTime", time,
                    "lastDayEnabled", true)).andExpect(status().isBadRequest());
        }
        owner.put("/catalog/deal-notifications/settings", Map.of("autoEnabled", false, "sendTime", "08:30",
                "lastDayEnabled", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.autoEnabled").value(false))
                .andExpect(jsonPath("$.settings.sendTime").value("08:30"));
    }

    @Test
    void customersGetTheNotificationWhileTheirBranchIsOpen() throws Exception {
        AdminClient owner = owner();
        String category = category(owner);
        UUID branch = TenantContext.callAs(alpha(), () -> tenantJdbc.sql(
                "SELECT id FROM branches WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1").query(UUID.class).single());
        String hours = TenantContext.callAs(alpha(), () -> tenantJdbc.sql(
                "SELECT business_hours::text FROM branches WHERE id = :id").param("id", branch).query(String.class)
                .optional().orElse(null));
        String open = "[[\"10:00\",\"18:00\"]]";
        String schedule = "{\"mon\":" + open + ",\"tue\":" + open + ",\"wed\":" + open + ",\"thu\":" + open
                + ",\"fri\":" + open + ",\"sat\":" + open + ",\"sun\":" + open + "}";
        try {
            Customer customer = customer();
            UUID customerId = UUID.fromString(JsonPath.read(body(customer.client().get("/api/v1/me")), "$.id"));
            TenantContext.runAs(alpha(), () -> {
                tenantJdbc.sql("UPDATE branches SET business_hours = CAST(:hours AS jsonb) WHERE id = :id")
                        .params(Map.of("hours", schedule, "id", branch)).update();
                tenantJdbc.sql("UPDATE customers SET last_branch_id = :branch WHERE id = :id")
                        .params(Map.of("branch", branch, "id", customerId)).update();
            });
            item(owner, category, "Latte", 5, Map.of("kind", "PERCENT", "value", 20), null);

            // Filial bağlıdır (08:00): bildiriş növbədə qalır
            at(LocalTime.of(8, 0));
            owner.post("/catalog/deal-notifications/send", Map.of()).andExpect(jsonPath("$.status").value("SENT"));
            Thread.sleep(300);
            assertThat(inbox.to(customer.browser().endpoint())).isEmpty();
            owner.get("/catalog/deal-notifications")
                    .andExpect(jsonPath("$.recent[0].waiting").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

            // Filial açılır (10:00): taymer göndərir
            at(LocalTime.of(10, 0));
            TenantContext.callAs(alpha(), deals::runForCurrentTenant);
            assertThat(field(customer.browser(), push(customer, 1), "$.title")).isEqualTo("Yeni endirim 🎉");
        } finally {
            TenantContext.runAs(alpha(), () -> tenantJdbc.sql(
                            "UPDATE branches SET business_hours = CAST(:hours AS jsonb) WHERE id = :id")
                    .param("hours", hours).param("id", branch).update());
        }
    }
}
