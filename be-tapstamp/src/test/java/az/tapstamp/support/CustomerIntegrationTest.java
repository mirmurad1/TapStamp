package az.tapstamp.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import az.tapstamp.tenancy.registry.TenantDirectory;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Müştəri autentifikasiyası testlərinin bazası: unikal nömrələr, SMS kodları, tam giriş axını. */
public abstract class CustomerIntegrationTest extends IntegrationTest {

    protected static final String HOST = TestTenants.ALPHA_HOST;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TestSmsInbox sms;

    @Autowired
    protected MutableClock clock;

    @Autowired
    @Qualifier("tenantJdbc")
    protected JdbcClient tenantJdbc;

    @Autowired
    protected TenantDirectory directory;

    /** Unikal Azərbaycan mobil nömrəsi (E.164): testlər bir-birinin limitlərinə toxunmur. */
    protected static String uniquePhone() {
        return "+99455" + String.format(Locale.ROOT, "%07d", ThreadLocalRandom.current().nextInt(10_000_000));
    }

    /** Eyni nömrənin yerli yazılışı: +994551234567 → 055 123 45 67. */
    protected static String localFormat(String e164) {
        String digits = e164.substring(4);
        return "0" + digits.substring(0, 2) + " " + digits.substring(2, 5) + " " + digits.substring(5, 7) + " "
                + digits.substring(7);
    }

    /** Testdə istifadə olunan yazılışlardan E.164: 055 123 45 67 → +994551234567. */
    protected static String toE164(String phone) {
        String digits = phone.replaceAll("[^0-9+]", "");
        return digits.startsWith("0") ? "+994" + digits.substring(1) : digits;
    }

    protected CustomerClient client() {
        return new CustomerClient(mvc, HOST);
    }

    protected static String json(ResultActions result, String path) throws Exception {
        Object value = JsonPath.read(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), path);
        return value == null ? null : value.toString();
    }

    /** OTP göndərir və SMS-dəki kodu qaytarır: {challengeId, code}. */
    protected String[] requestCode(CustomerClient client, String phone, String language) throws Exception {
        String e164 = toE164(phone);
        int before = sms.to(e164).size();
        ResultActions request = client.post("/api/v1/auth/otp/request", Map.of("phone", phone, "language", language))
                .andExpect(status().isAccepted());
        String challengeId = json(request, "$.challengeId");
        String code = TestSmsInbox.code(sms.await(e164, before + 1));
        // Növbəti kod üçün 60 saniyəlik gözləmə test saatı ilə keçilir
        clock.advance(Duration.ofSeconds(61));
        return new String[] {challengeId, code};
    }

    protected List<Map<String, Object>> currentDocuments(CustomerClient client) throws Exception {
        String body = client.get("/api/v1/public/legal").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> documents = JsonPath.read(body, "$");
        return documents.stream().map(d -> Map.<String, Object>of("type", d.get("type"), "version", d.get("version"))).toList();
    }

    /** Tam axın: kod → (yeni nömrədirsə) qeydiyyat. Klient daxil olmuş vəziyyətdə qalır. */
    protected CustomerClient signedIn(String phone) throws Exception {
        CustomerClient client = client();
        String[] challenge = requestCode(client, phone, "az");
        ResultActions verify = client.post("/api/v1/auth/otp/verify",
                Map.of("challengeId", challenge[0], "code", challenge[1])).andExpect(status().isOk());
        if ("REGISTRATION_REQUIRED".equals(json(verify, "$.status"))) {
            client.post("/api/v1/auth/register", Map.of(
                            "registrationToken", json(verify, "$.registrationToken"),
                            "name", "Test Müştəri",
                            "acceptedDocuments", currentDocuments(client),
                            "language", "az"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        }
        return client;
    }
}
