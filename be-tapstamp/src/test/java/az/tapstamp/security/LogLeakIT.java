package az.tapstamp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import az.tapstamp.customer.auth.CustomerCookies;
import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.TapIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Loglarda sirr yoxdur: OTP kodu, tam telefon nömrəsi, sessiya/refresh tokenləri, NFC toxunuş tokeni və işçi şifrəsi
 * tam axın (qeydiyyat, toxunuş, möhür, admin girişi, tam nömrəyə baxış) zamanı heç bir log sətrinə düşmür.
 *
 * <p>Yeganə istisna — dev/test-dəki konsol SMS provayderinin sətri ("SMS (konsol, yalnız dev)"): o, SMS-i göndərmək
 * əvəzinə mətnini göstərir və prod-da qadağandır (ProdSafetyGuard).
 */
@ExtendWith(OutputCaptureExtension.class)
class LogLeakIT extends TapIntegrationTest {

    @Test
    void secretsNeverReachTheLogs(CapturedOutput output) throws Exception {
        String phone = uniquePhone();
        CustomerClient customer = client();
        String[] challenge = requestCode(customer, phone, "az");
        ResultActions verify = customer.post("/api/v1/auth/otp/verify", Map.of("challengeId", challenge[0], "code", challenge[1]));
        customer.post("/api/v1/auth/register", Map.of(
                "registrationToken", json(verify, "$.registrationToken"),
                "name", "Log Testi",
                "acceptedDocuments", currentDocuments(customer),
                "language", "az")).andExpect(status().isCreated());

        UUID branch = newBranch(Policy.relaxed());
        TestTag tag = newTag(branch);
        String tapToken = tapToken(customer, tag, 1);
        customer.post(claimPath(tapToken)).andExpect(status().isOk());
        String customerId = json(customer.get("/api/v1/me"), "$.id");

        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        owner.post("/auth/step-up", Map.of("password", STAFF_PASSWORD)).andExpect(status().isNoContent());
        owner.post("/customers/" + customerId + "/phone-reveal").andExpect(status().isOk());

        String logs = Arrays.stream(output.getAll().split("\\R"))
                .filter(line -> !line.contains("SMS (konsol, yalnız dev)"))
                .collect(Collectors.joining("\n"));
        Map<String, String> secrets = new java.util.LinkedHashMap<>();
        secrets.put("OTP kodu", challenge[1]);
        secrets.put("tam telefon nömrəsi", phone);
        secrets.put("telefon (+ olmadan)", phone.substring(1));
        secrets.put("müştəri access tokeni", customer.cookie(CustomerCookies.ACCESS));
        secrets.put("müştəri refresh tokeni", customer.cookie(CustomerCookies.REFRESH));
        secrets.put("NFC toxunuş tokeni", tapToken);
        secrets.put("işçi sessiyası", owner.session());
        secrets.put("işçi şifrəsi", STAFF_PASSWORD);
        assertThat(secrets.get("müştəri access tokeni")).isNotBlank();
        secrets.forEach((name, value) -> {
            if (value != null) {
                assertThat(logs).as(name).doesNotContain(value);
            }
        });
    }
}
