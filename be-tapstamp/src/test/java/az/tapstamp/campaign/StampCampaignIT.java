package az.tapstamp.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;

import az.tapstamp.notification.sms.SmsDeliveryException.Kind;
import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.TapIntegrationTest;
import az.tapstamp.support.TestSmsFailures;
import az.tapstamp.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Möhür kampaniyası: kartı boş olan müştərilərə SMS + möhür. Möhür yalnız SMS göndəriləndə (eyni tranzaksiyada)
 * verilir; müvəqqəti xətada təkrar cəhd, nömrə rədd ediləndə möhür yoxdur, hesab xətasında kampaniya dayanır,
 * nəticə məlum deyilsə SMS getmiş sayılır və möhür verilir.
 */
class StampCampaignIT extends TapIntegrationTest {

    /** Təsdiqə göndərmək (yaratma və dəyişmə) cari şifrə tələb edir. */
    private static Map<String, Object> confirmed(Map<String, ?> body) {
        Map<String, Object> withPassword = new java.util.HashMap<>(body);
        withPassword.put("currentPassword", STAFF_PASSWORD);
        return withPassword;
    }

    @Autowired
    private CampaignSender sender;

    @Autowired
    private TestSmsFailures smsFailures;

    @AfterEach
    void resetFailures() {
        smsFailures.clear();
    }

    private record Customer(String phone, CustomerClient client) {
        int stamps() throws Exception {
            String body = client.get("/api/v1/me").andReturn().getResponse().getContentAsString();
            return com.jayway.jsonpath.JsonPath.read(body, "$.card.currentStamps");
        }
    }

    private Customer customer() throws Exception {
        String phone = uniquePhone();
        return new Customer(phone, signedIn(phone));
    }

    private Map<String, Object> request(int stamps) {
        return Map.of("type", "STAMPS", "stampCount", stamps, "audience", "EMPTY_CARD",
                "text", "Kartiniza hediyye mohur elave etdik!");
    }

    private String approvedCampaign(AdminClient owner, int stamps) throws Exception {
        String id = json(owner.post("/campaigns", confirmed(request(stamps))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("STAMPS"))
                .andExpect(jsonPath("$.stampCount").value(stamps)), "$.id");
        signedInPlatformAdmin().post("/campaigns/" + alpha().id() + "/" + id + "/approve").andExpect(status().isOk());
        return id;
    }

    @Test
    void stampsAreGrantedTogetherWithTheSms() throws Exception {
        Customer first = customer();
        Customer second = customer();
        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);

        owner.post("/campaigns/preview", request(2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.stampCount").value(2))
                .andExpect(jsonPath("$.totalStamps").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.maxStamps").isNumber());
        // Ən çox "hədd − 1"; auditoriya yalnız kartı boş olanlar
        owner.post("/campaigns/preview", request(50)).andExpect(status().isBadRequest());
        owner.post("/campaigns/preview", Map.of("type", "STAMPS", "stampCount", 1, "audience", "ALL", "text", "x"))
                .andExpect(status().isBadRequest());

        String id = approvedCampaign(owner, 2);
        assertThat(first.stamps()).isZero();
        sendUntilDone();

        assertThat(first.stamps()).isEqualTo(2);
        assertThat(second.stamps()).isEqualTo(2);
        assertThat(sms.to(first.phone())).anySatisfy(m -> assertThat(m.text()).contains("hediyye mohur"));
        owner.get("/campaigns/" + id).andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.stampsGranted").value(jsonInt(owner, id, "$.sentCount")));
        owner.get("/campaigns?size=50&type=STAMPS").andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").isNotEmpty());
        owner.get("/campaigns?size=50&type=SMS").andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").isEmpty());
    }

    @Test
    void stampsFollowTheSmsOutcome() throws Exception {
        Customer rejected = customer();
        Customer flaky = customer();
        Customer unknown = customer();
        smsFailures.failNext(rejected.phone(), Kind.RECIPIENT);
        smsFailures.failNext(flaky.phone(), Kind.TRANSIENT);
        smsFailures.failNext(unknown.phone(), Kind.UNKNOWN);
        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        String id = approvedCampaign(owner, 1);

        sendUntilDone();
        // Nömrə rədd edildi — möhür yoxdur; müvəqqəti xəta — hələ gözləyir; nəticə məlum deyil — möhür verilib
        assertThat(rejected.stamps()).isZero();
        assertThat(flaky.stamps()).isZero();
        assertThat(unknown.stamps()).isEqualTo(1);
        owner.get("/campaigns/" + id).andExpect(jsonPath("$.status").value("SENDING"))
                .andExpect(jsonPath("$.pendingCount").value(1));

        // 10 dəqiqə sonra təkrar cəhd uğurlu olur
        clock.advance(Duration.ofMinutes(11));
        sendUntilDone();
        assertThat(flaky.stamps()).isEqualTo(1);
        owner.get("/campaigns/" + id).andExpect(jsonPath("$.status").value("SENT"));
        owner.get("/campaigns/" + id + "/failures").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.phone == '" + rejected.phone() + "')].error")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.containsString("RECIPIENT"))));
    }

    @Test
    void accountErrorPausesTheCampaignUntilThePlatformResumesIt() throws Exception {
        Customer customer = customer();
        smsFailures.failNext(customer.phone(), Kind.ACCOUNT);
        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        String id = approvedCampaign(owner, 1);

        sendUntilDone();
        owner.get("/campaigns/" + id).andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.pauseReason").value(org.hamcrest.Matchers.containsString("ACCOUNT")));
        assertThat(customer.stamps()).isZero();

        // Platforma 48 saatdan gec davam etdirsə də, gözləyən alıcılar itmir (son müddət yenidən başlayır)
        clock.advance(Duration.ofHours(49));
        AdminClient platform = signedInPlatformAdmin();
        platform.get("/campaigns?size=50").andExpect(jsonPath("$.items[?(@.id == '" + id + "')].status")
                .value(org.hamcrest.Matchers.contains("PAUSED")));
        platform.post("/campaigns/" + alpha().id() + "/" + id + "/resume").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENDING"));
        sendUntilDone();
        // 49 saat keçib — müştəri sessiyası bitib, yenidən daxil olur
        assertThat(new Customer(customer.phone(), signedIn(customer.phone())).stamps()).isEqualTo(1);
        signedInStaff(HOST, AdminRole.COMPANY_OWNER, null).get("/campaigns/" + id).andExpect(jsonPath("$.status").value("SENT"));
    }

    private static int jsonInt(AdminClient client, String id, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                client.get("/campaigns/" + id).andReturn().getResponse().getContentAsString(), path);
    }

    /** Saatı 09:00–21:00 pəncərəsinə gətirib göndərişi (hazır olan alıcılar üçün) bitirir. */
    private void sendUntilDone() {
        LocalTime local = clock.instant().atZone(alpha().timezone()).toLocalTime();
        if (local.isBefore(LocalTime.of(10, 0)) || !local.isBefore(LocalTime.of(20, 0))) {
            long minutes = Duration.between(local, LocalTime.of(12, 0)).toMinutes();
            clock.advance(Duration.ofMinutes(minutes < 0 ? minutes + 24 * 60 : minutes));
        }
        for (int i = 0; i < 50; i++) {
            TenantContext.runAs(alpha(), sender::sendForCurrentTenant);
        }
    }
}
