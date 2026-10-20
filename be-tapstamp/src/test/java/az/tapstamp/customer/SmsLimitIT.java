package az.tapstamp.customer;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import az.tapstamp.customer.auth.OtpBudget;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.TapIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Giriş kodlarının SMS limitləri: nömrə üzrə gündə 3 kod; şirkətin gündəlik limiti yalnız yeni nömrələrə (qeydiyyatlı
 * müştəri limitdən asılı deyil); platformanın gündəlik büdcəsi bütün kodlara; limit dolanda platformaya xəbərdarlıq.
 */
class SmsLimitIT extends TapIntegrationTest {

    @Autowired
    private OtpBudget budget;

    private ResultActions requestOtp(CustomerClient client, String phone) throws Exception {
        return client.post("/api/v1/auth/otp/request", Map.of("phone", phone, "language", "az"));
    }

    @Test
    void onePhoneGetsAtMostThreeLoginCodesPerDay() throws Exception {
        String phone = uniquePhone();
        for (int i = 0; i < 3; i++) {
            requestCode(client(), phone, "az");
            clock.advance(Duration.ofMinutes(16));
        }
        requestOtp(client(), phone).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void tenantLimitCountsOnlyNewNumbersAndCanBeChangedByThePlatform() throws Exception {
        String registered = uniquePhone();
        signedIn(registered);
        clock.advance(Duration.ofMinutes(2));
        AdminClient platform = signedInPlatformAdmin();
        String path = "/tenants/" + alpha().id() + "/sms-limit";
        platform.put(path, Map.of("limit", 10)).andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.custom").value(true));
        platform.put(path, Map.of("limit", 5)).andExpect(status().isBadRequest());
        try {
            // Yeni nömrələr limitə qədər kod alır, sonra SMS_DAILY_LIMIT_REACHED
            boolean blocked = false;
            for (int i = 0; i < 12 && !blocked; i++) {
                int status = requestOtp(client(), uniquePhone()).andReturn().getResponse().getStatus();
                blocked = status == 429;
            }
            requestOtp(client(), uniquePhone()).andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("SMS_DAILY_LIMIT_REACHED"));
            // Qeydiyyatlı müştəri limitdən asılı deyil
            requestOtp(client(), registered).andExpect(status().isAccepted());

            platform.get(path).andExpect(jsonPath("$.usedToday").value(org.hamcrest.Matchers.greaterThanOrEqualTo(10)));
            platform.get("/audit-logs?action=SMS_LIMIT_ALERT&size=50").andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.targetId == 'alpha')]").isNotEmpty());
        } finally {
            Map<String, Object> reset = new HashMap<>();
            reset.put("limit", null);
            platform.put(path, reset).andExpect(status().isOk()).andExpect(jsonPath("$.custom").value(false));
        }
    }

    @Test
    void platformBudgetBlocksAllCodesWhenExhausted() throws Exception {
        String registered = uniquePhone();
        signedIn(registered);
        clock.advance(Duration.ofMinutes(2));
        long original = budget.platformDaily();
        ReflectionTestUtils.setField(budget, "platformDaily", 0L);
        try {
            requestOtp(client(), registered).andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("SMS_DAILY_LIMIT_REACHED"));
            signedInPlatformAdmin().get("/sms-usage/budget").andExpect(status().isOk())
                    .andExpect(jsonPath("$.dailyBudget").value(0));
        } finally {
            ReflectionTestUtils.setField(budget, "platformDaily", original);
        }
        requestOtp(client(), registered).andExpect(status().isAccepted());
    }
}
