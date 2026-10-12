package az.tapstamp.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import az.tapstamp.notification.sms.SmsMessage;
import az.tapstamp.notification.sms.SmsSegments;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.CustomerIntegrationTest;
import az.tapstamp.tenancy.TenantContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * OTP: kod bazada açıq saxlanılmır, 5 dəqiqəlik ömür, 5 cəhd (paralel sorğularla da), yeni kod köhnəni etibarsız edir,
 * qeydiyyatlı və qeydiyyatsız nömrəyə eyni cavab, limitlər, qeydiyyat və razılıqlar.
 */
class OtpIT extends CustomerIntegrationTest {

    private ResultActions verify(CustomerClient client, String challengeId, String code) throws Exception {
        return client.post("/api/v1/auth/otp/verify", Map.of("challengeId", challengeId, "code", code));
    }

    private int count(String sql, Object... params) {
        return TenantContext.callAs(directory.findByHost(HOST).orElseThrow(), () -> {
            var spec = tenantJdbc.sql(sql);
            for (int i = 0; i < params.length; i++) {
                spec = spec.param(i + 1, params[i]);
            }
            return spec.query(Integer.class).single();
        });
    }

    @Test
    void responseIsIdenticalForRegisteredAndUnknownNumbers() throws Exception {
        String registered = uniquePhone();
        signedIn(registered);
        clock.advance(Duration.ofSeconds(61));

        String registeredBody = client().post("/api/v1/auth/otp/request", Map.of("phone", registered, "language", "az"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknownBody = client().post("/api/v1/auth/otp/request", Map.of("phone", uniquePhone(), "language", "az"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        Map<String, Object> first = JsonPath.read(registeredBody, "$");
        Map<String, Object> second = JsonPath.read(unknownBody, "$");
        assertThat(first.keySet()).isEqualTo(second.keySet()).containsExactlyInAnyOrder("challengeId", "expiresAt",
                "resendAvailableAt");
    }

    @Test
    void codeIsNeverStoredInPlainTextAndSmsIsLanguageIndependent() throws Exception {
        String phone = uniquePhone();
        String[] challenge = requestCode(client(), phone, "ru");
        SmsMessage message = sms.to(phone).getFirst();
        // Müştəri rus dilini seçsə də, mətn eynidir: "<şirkətin adı> OTP: <kod>"
        assertThat(message.text()).isEqualTo("Alpha Kafe OTP: " + challenge[1]);
        assertThat(SmsSegments.analyze(message.text()).segments()).isEqualTo(1);

        UUID id = UUID.fromString(challenge[0]);
        assertThat(count("SELECT count(*) FROM otp_challenges WHERE id = ? AND position(convert_to(?, 'UTF8') in code_hash) > 0",
                id, challenge[1])).isZero();
        assertThat(count("SELECT count(*) FROM otp_challenges WHERE id = ? AND position(convert_to(?, 'UTF8') in phone_enc) > 0",
                id, phone.substring(4))).isZero();
        assertThat(count("SELECT count(*) FROM otp_challenges WHERE id = ? AND octet_length(code_hash) = 32", id)).isOne();
    }

    @Test
    void newNumberRegistersWithConsentsAndThenSignsIn() throws Exception {
        String phone = uniquePhone();
        CustomerClient client = client();
        String[] challenge = requestCode(client, phone, "az");
        ResultActions verified = verify(client, challenge[0], challenge[1])
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_REQUIRED"))
                .andExpect(jsonPath("$.registrationToken").isString());
        assertThat(client.cookie("__Host-cl_at")).isNull();
        String token = json(verified, "$.registrationToken");

        // Razılıq olmadan qeydiyyat mümkün deyil; token qüvvədə qalır
        client.post("/api/v1/auth/register", Map.of("registrationToken", token, "acceptedDocuments", List.of(),
                        "language", "en"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        client.post("/api/v1/auth/register", Map.of("registrationToken", token, "name", "  Aysel  ",
                        "acceptedDocuments", currentDocuments(client), "language", "en"))
                .andExpect(status().isCreated());
        client.post("/api/v1/auth/register", Map.of("registrationToken", token, "acceptedDocuments",
                        currentDocuments(client), "language", "en"))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        client.get("/api/v1/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aysel"))
                .andExpect(jsonPath("$.preferredLanguage").value("en"))
                .andExpect(jsonPath("$.phone").value(phone))
                .andExpect(jsonPath("$.card.currentStamps").value(0))
                .andExpect(jsonPath("$.card.stampsRequired").value(9))
                .andExpect(jsonPath("$.pendingConsents.length()").value(0));
        assertThat(count("SELECT count(*) FROM consents c JOIN customers u ON u.id = c.customer_id "
                + "WHERE u.name = 'Aysel' AND c.language = 'en' AND c.ip IS NOT NULL")).isGreaterThanOrEqualTo(2);

        // Eyni nömrə başqa yazılışla: mövcud müştəri dərhal daxil olur
        CustomerClient again = client();
        String[] second = requestCode(again, localFormat(phone), "az");
        verify(again, second[0], second[1])
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.registrationToken").doesNotExist());
        assertThat(again.cookie("__Host-cl_at")).isNotNull();
        again.get("/api/v1/me").andExpect(jsonPath("$.name").value("Aysel"));
    }

    @Test
    void wrongCodesAreCountedAndTheFifthLocksTheChallenge() throws Exception {
        String phone = uniquePhone();
        CustomerClient client = client();
        String[] challenge = requestCode(client, phone, "az");
        String wrong = challenge[1].equals("000000") ? "111111" : "000000";
        for (int remaining = 4; remaining >= 1; remaining--) {
            verify(client, challenge[0], wrong)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("OTP_INVALID"))
                    .andExpect(jsonPath("$.attemptsRemaining").value(remaining));
        }
        verify(client, challenge[0], wrong).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
        verify(client, challenge[0], challenge[1]).andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
    }

    @Test
    void parallelAttemptsCannotExceedTheLimit() throws Exception {
        String phone = uniquePhone();
        String[] challenge = requestCode(client(), phone, "az");
        String wrong = challenge[1].equals("000000") ? "111111" : "000000";
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                results.add(pool.submit(() -> json(verify(client(), challenge[0], wrong), "$.code")));
            }
            List<String> codes = new ArrayList<>();
            for (Future<String> result : results) {
                codes.add(result.get());
            }
            assertThat(codes.stream().filter("OTP_INVALID"::equals).count()).isLessThanOrEqualTo(4);
            assertThat(count("SELECT attempts FROM otp_challenges WHERE id = ?", UUID.fromString(challenge[0])))
                    .isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
        verify(client(), challenge[0], challenge[1]).andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
    }

    @Test
    void codeExpiresAfterFiveMinutesAndNewCodeSupersedesTheOld() throws Exception {
        String phone = uniquePhone();
        CustomerClient client = client();
        String[] first = requestCode(client, phone, "az");
        String[] second = requestCode(client, phone, "az");
        verify(client, first[0], first[1]).andExpect(jsonPath("$.code").value("OTP_EXPIRED"));

        clock.advance(Duration.ofMinutes(5));
        verify(client, second[0], second[1]).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
    }

    @Test
    void resendIsAllowedOnlyAfterTheCooldownAndAtMostThreeTimesPerQuarterHour() throws Exception {
        String phone = uniquePhone();
        CustomerClient client = client();
        client.post("/api/v1/auth/otp/request", Map.of("phone", phone, "language", "az")).andExpect(status().isAccepted());
        client.post("/api/v1/auth/otp/request", Map.of("phone", phone, "language", "az"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
        clock.advance(Duration.ofSeconds(61));
        client.post("/api/v1/auth/otp/request", Map.of("phone", phone, "language", "az")).andExpect(status().isAccepted());
        clock.advance(Duration.ofSeconds(61));
        client.post("/api/v1/auth/otp/request", Map.of("phone", phone, "language", "az")).andExpect(status().isAccepted());
        clock.advance(Duration.ofSeconds(61));
        // Dördüncü: telefon üzrə 15 dəqiqədə 3 limiti (başqa IP-dən də)
        client().post("/api/v1/auth/otp/request", Map.of("phone", phone, "language", "az"))
                .andExpect(status().isTooManyRequests());
        sms.await(phone, 3);
        Thread.sleep(200);
        assertThat(sms.to(phone)).hasSize(3);
    }

    @Test
    void invalidAndForeignNumbersAreRejectedWithoutSendingSms() throws Exception {
        client().post("/api/v1/auth/otp/request", Map.of("phone", "012 497 00 00", "language", "az"))
                .andExpect(jsonPath("$.code").value("PHONE_INVALID"));
        client().post("/api/v1/auth/otp/request", Map.of("phone", "+7 912 345 67 89", "language", "az"))
                .andExpect(jsonPath("$.code").value("PHONE_REGION_NOT_ALLOWED"));
        client().post("/api/v1/auth/otp/request", Map.of("phone", "", "language", "az"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(sms.to("+79123456789")).isEmpty();
    }

    @Test
    void blockedCustomerCannotSignIn() throws Exception {
        String phone = uniquePhone();
        signedIn(phone);
        TenantContext.runAs(directory.findByHost(HOST).orElseThrow(), () -> tenantJdbc.sql(
                        "UPDATE customers SET status = 'BLOCKED' WHERE name = 'Test Müştəri' AND phone_masked = :masked")
                .param("masked", "+994 55 ***-**-" + phone.substring(11))
                .update());
        CustomerClient client = client();
        String[] challenge = requestCode(client, phone, "az");
        verify(client, challenge[0], challenge[1]).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CUSTOMER_BLOCKED"));
    }

    @Test
    void challengeFromOneCompanyIsUnknownInAnother() throws Exception {
        String phone = uniquePhone();
        String[] challenge = requestCode(client(), phone, "az");
        new CustomerClient(mvc, "beta.test").post("/api/v1/auth/otp/verify",
                        Map.of("challengeId", challenge[0], "code", challenge[1]))
                .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
    }
}
