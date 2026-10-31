package az.tapstamp.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.TapIntegrationTest;
import az.tapstamp.tenancy.TenantContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Ziyarətdən sonra rəy: NFC möhüründən 15 dəqiqə sonra sorğu, tezlik, cavab, admin siyahısı (tam telefonla), pis
 * rəyin həlli və yoxlamalar.
 */
class FeedbackIT extends TapIntegrationTest {

    @Autowired
    private FeedbackService feedback;

    private AdminClient owner() throws Exception {
        return signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
    }

    private void settings(boolean enabled, int frequencyDays) throws Exception {
        owner().put("/feedback/settings", Map.of("enabled", enabled, "frequencyDays", frequencyDays))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(enabled));
    }

    @AfterEach
    void disable() throws Exception {
        settings(false, 7);
    }

    private void claim(CustomerClient customer, TestTag tag, int counter) throws Exception {
        customer.post(claimPath(tapToken(customer, tag, counter))).andExpect(status().isOk());
    }

    /** 15 dəqiqə keçmiş kimi: saatı irəli çəkmək müştərinin giriş tokeninin (15 dəq) vaxtını keçirərdi. */
    private void elapse() {
        TenantContext.runAs(alpha(), () -> tenantJdbc
                .sql("UPDATE feedback_requests SET due_at = due_at - interval '16 minutes' WHERE sent_at IS NULL")
                .update());
    }

    private int sendDue() {
        return TenantContext.callAs(alpha(), feedback::sendDueForCurrentTenant);
    }

    @Test
    void visitIsFollowedByARequestAndABadAnswerReachesTheOwner() throws Exception {
        settings(true, 7);
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        String phone = uniquePhone();
        CustomerClient customer = signedIn(phone);

        claim(customer, tag, 1);
        sendDue();
        customer.get("/api/v1/me/feedback-request").andExpect(status().isNoContent());

        // 15 dəqiqə sonra sorğu göndərilir və tətbiqdə görünür
        elapse();
        assertThat(sendDue()).isGreaterThanOrEqualTo(1);
        String body = customer.get("/api/v1/me/feedback-request").andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        String requestId = JsonPath.read(body, "$.id");

        // Tezlik: eyni həftədə ikinci ziyarət yeni sorğu yaratmır
        claim(customer, tag, 2);
        elapse();
        sendDue();

        customer.post("/api/v1/me/feedback-request/" + requestId,
                Map.of("rating", "BAD", "reasons", List.of("WAIT", "SERVICE"), "comment", "Çox gözlədim"))
                .andExpect(status().isNoContent());
        customer.get("/api/v1/me/feedback-request").andExpect(status().isNoContent());
        // Təkrar cavab yoxdur
        customer.post("/api/v1/me/feedback-request/" + requestId, Map.of("rating", "GOOD"))
                .andExpect(status().isNotFound());

        AdminClient owner = owner();
        String customerId = JsonPath.read(customer.get("/api/v1/me").andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8), "$.id");
        String list = owner.get("/feedback?unresolved=true&rating=BAD&size=50").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> phones = JsonPath.read(list, "$.items[?(@.customerId == '" + customerId + "')].phone");
        assertThat(phones).containsExactly(phone);
        List<String> ids = JsonPath.read(list, "$.items[?(@.customerId == '" + customerId + "')].id");
        owner.get("/feedback/summary").andExpect(jsonPath("$.unresolvedBad")
                .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        // Dövr üzrə (göstəricilər paneli): bu günün rəyi bu günə düşür, keçən ilə düşmür; səhv dövr — 400
        java.time.LocalDate today = clock.instant().atZone(alpha().timezone()).toLocalDate();
        owner.get("/feedback/summary?from=" + today + "&to=" + today).andExpect(status().isOk())
                .andExpect(jsonPath("$.bad").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.sent").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        java.time.LocalDate lastYear = today.minusYears(1);
        owner.get("/feedback/summary?from=" + lastYear + "&to=" + lastYear.plusDays(6))
                .andExpect(jsonPath("$.answered").value(0)).andExpect(jsonPath("$.sent").value(0));
        owner.get("/feedback/summary?from=" + today + "&to=" + today.minusDays(1)).andExpect(status().isBadRequest());

        owner.post("/feedback/" + ids.get(0) + "/resolve", Map.of("note", "Zəng etdik")).andExpect(status().isNoContent());
        owner.get("/feedback?unresolved=true&size=50")
                .andExpect(jsonPath("$.items[?(@.id == '" + ids.get(0) + "')]").isEmpty());
    }

    @Test
    void disabledProgramCreatesNoRequestsAndInputIsValidated() throws Exception {
        settings(false, 7);
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient customer = signedIn(uniquePhone());
        claim(customer, tag, 1);
        elapse();
        sendDue();
        customer.get("/api/v1/me/feedback-request").andExpect(status().isNoContent());

        owner().put("/feedback/settings", Map.of("enabled", true, "frequencyDays", 3))
                .andExpect(status().isBadRequest());
        signedInStaff(HOST, AdminRole.BRANCH_MANAGER, newBranch(Policy.relaxed()))
                .get("/feedback").andExpect(status().isForbidden());
    }
}
