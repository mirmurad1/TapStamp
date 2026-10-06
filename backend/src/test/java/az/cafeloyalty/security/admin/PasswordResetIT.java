package az.cafeloyalty.security.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import az.cafeloyalty.notification.mail.MailMessage;
import az.cafeloyalty.support.AdminClient;
import az.cafeloyalty.support.AdminIntegrationTest;
import az.cafeloyalty.support.TestMailbox;
import az.cafeloyalty.support.TestTenants;
import org.junit.jupiter.api.Test;

/** Şifrə bərpası: eyni cavab, birdəfəlik və müddətli link, bərpadan sonra bütün sessiyaların ləğvi. */
class PasswordResetIT extends AdminIntegrationTest {

    private static final String HOST = TestTenants.ALPHA_HOST;
    private static final String NEW_PASSWORD = "Bərpa-Olunmuş-Parol-2027";

    private String newManager() {
        String email = uniqueEmail("reset");
        createStaff(HOST, email, AdminRole.BRANCH_MANAGER, branchOf(HOST), false);
        return email;
    }

    @Test
    void responseIsIdenticalForUnknownEmailsAndNoMailIsSent() throws Exception {
        String unknown = uniqueEmail("unknown");
        AdminClient.staff(mvc, HOST).post("/auth/password/forgot", Map.of("email", unknown))
                .andExpect(status().isAccepted());
        String known = newManager();
        AdminClient.staff(mvc, HOST).post("/auth/password/forgot", Map.of("email", known))
                .andExpect(status().isAccepted());
        mailbox.awaitMessage(known, 1);
        assertThat(mailbox.to(unknown)).isEmpty();
    }

    @Test
    void resetLinkWorksOnceAndSignsOutEverywhere() throws Exception {
        String email = newManager();
        AdminClient session = signedInStaff(HOST, email, false);

        AdminClient.staff(mvc, HOST).post("/auth/password/forgot", Map.of("email", email.toUpperCase(java.util.Locale.ROOT)))
                .andExpect(status().isAccepted());
        MailMessage mail = mailbox.awaitMessage(email, 1);
        // Link reyestrdəki əsas domendədir (Host başlığından qurulmur) və token URL fraqmentindədir
        assertThat(TestMailbox.link(mail)).startsWith("https://" + HOST + "/admin/reset-password#token=");
        assertThat(mail.subject()).isEqualTo("Şifrənin bərpası");
        assertThat(mail.textBody()).contains("30 dəqiqə");
        String token = TestMailbox.token(mail);

        AdminClient anonymous = AdminClient.staff(mvc, HOST);
        anonymous.post("/auth/password/reset", Map.of("token", token, "newPassword", "password1234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.violations[0]").value("COMMON_PASSWORD"));
        // Siyasət pozulanda token istifadə olunmuş sayılmır
        anonymous.post("/auth/password/reset", Map.of("token", token, "newPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());
        anonymous.post("/auth/password/reset", Map.of("token", token, "newPassword", NEW_PASSWORD + "!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        session.get("/me").andExpect(status().isUnauthorized());
        AdminClient.staff(mvc, HOST).login(email, PASSWORD).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        AdminClient.staff(mvc, HOST).login(email, NEW_PASSWORD).andExpect(status().isOk());
        assertThat(mailbox.awaitMessage(email, 2).subject()).isEqualTo("Şifrəniz dəyişdirildi");
    }

    @Test
    void onlyTheLatestLinkIsValid() throws Exception {
        String email = newManager();
        AdminClient client = AdminClient.staff(mvc, HOST);
        client.post("/auth/password/forgot", Map.of("email", email));
        String first = TestMailbox.token(mailbox.awaitMessage(email, 1));
        client.post("/auth/password/forgot", Map.of("email", email));
        String second = TestMailbox.token(mailbox.awaitMessage(email, 2));

        client.post("/auth/password/reset", Map.of("token", first, "newPassword", NEW_PASSWORD))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        client.post("/auth/password/reset", Map.of("token", second, "newPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());
    }

    @Test
    void linkExpiresAfterThirtyMinutes() throws Exception {
        String email = newManager();
        AdminClient client = AdminClient.staff(mvc, HOST);
        client.post("/auth/password/forgot", Map.of("email", email));
        String token = TestMailbox.token(mailbox.awaitMessage(email, 1));
        clock.advance(Duration.ofMinutes(31));
        client.post("/auth/password/reset", Map.of("token", token, "newPassword", NEW_PASSWORD))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void tokenFromOneCompanyDoesNotWorkInAnother() throws Exception {
        String email = newManager();
        AdminClient.staff(mvc, HOST).post("/auth/password/forgot", Map.of("email", email));
        String token = TestMailbox.token(mailbox.awaitMessage(email, 1));
        AdminClient.staff(mvc, TestTenants.BETA_HOST)
                .post("/auth/password/reset", Map.of("token", token, "newPassword", NEW_PASSWORD))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void resetRequestsPerEmailAreLimitedSilently() throws Exception {
        String email = newManager();
        for (int i = 0; i < 5; i++) {
            AdminClient.staff(mvc, HOST).post("/auth/password/forgot", Map.of("email", email))
                    .andExpect(status().isAccepted());
        }
        mailbox.awaitMessage(email, 3);
        Thread.sleep(300);
        assertThat(mailbox.to(email)).hasSize(3);
    }
}
