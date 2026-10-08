package az.tapstamp.security.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.security.totp.Base32;
import az.tapstamp.security.totp.TotpCodes;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.AdminIntegrationTest;
import az.tapstamp.support.TestTenants;
import az.tapstamp.tenancy.TenantContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/** İşçilərin girişi: şifrə, bloklanma, TOTP, bərpa kodları, step-up, sessiya müddətləri və rotasiyası. */
class StaffAuthIT extends AdminIntegrationTest {

    private static final String HOST = TestTenants.ALPHA_HOST;

    private String newManager() {
        String email = uniqueEmail("manager");
        createStaff(HOST, email, AdminRole.BRANCH_MANAGER, branchOf(HOST), false);
        return email;
    }

    private String newOwner(boolean totp) {
        String email = uniqueEmail("owner");
        createStaff(HOST, email, AdminRole.COMPANY_OWNER, null, totp);
        return email;
    }

    @Test
    void managerSignsInWithPasswordAndGetsSecureSessionCookie() throws Exception {
        String email = newManager();
        AdminClient client = AdminClient.staff(mvc, HOST);

        ResultActions login = client.login(email.toUpperCase(Locale.ROOT), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        String setCookie = sessionCookieHeader(login);
        assertThat(setCookie).startsWith("__Host-cl_staff=")
                .contains("Path=/", "Secure", "HttpOnly", "SameSite=Strict")
                .contains("Max-Age=" + Duration.ofHours(8).toSeconds());
        // Token cavabın body-sində yoxdur (yalnız HttpOnly cookie-də)
        assertThat(login.andReturn().getResponse().getContentAsString()).doesNotContain(client.session());

        client.get("/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.realm").value("STAFF"))
                .andExpect(jsonPath("$.roles[0].role").value("BRANCH_MANAGER"))
                .andExpect(jsonPath("$.roles[0].branchId").value(branchOf(HOST).toString()))
                .andExpect(jsonPath("$.totpEnabled").value(false))
                .andExpect(jsonPath("$.features.devTools").value(false));
    }

    @Test
    void unknownEmailAndWrongPasswordAreIndistinguishable() throws Exception {
        String email = newManager();
        String wrongPassword = AdminClient.staff(mvc, HOST).login(email, "Yanlış-şifrə-12345")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String unknownEmail = AdminClient.staff(mvc, HOST).login(uniqueEmail("nobody"), "Yanlış-şifrə-12345")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(stripTraceId(wrongPassword)).isEqualTo(stripTraceId(unknownEmail));
    }

    @Test
    void accountIsTemporarilyLockedAfterFiveFailuresEvenWithCorrectPassword() throws Exception {
        String email = newManager();
        AdminClient client = AdminClient.staff(mvc, HOST);
        for (int i = 0; i < 5; i++) {
            client.login(email, "Yanlış-şifrə-12345").andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }
        client.login(email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_TEMPORARILY_BLOCKED"));

        // Bloklanma müvəqqətidir: 1 dəqiqədən sonra düzgün şifrə qəbul olunur
        clock.advance(Duration.ofMinutes(2));
        client.login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownEmailsAreBlockedTheSameWayAsRealAccounts() throws Exception {
        String unknown = uniqueEmail("ghost");
        AdminClient client = AdminClient.staff(mvc, HOST);
        for (int i = 0; i < 5; i++) {
            client.login(unknown, "Yanlış-şifrə-12345").andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }
        client.login(unknown, "Yanlış-şifrə-12345").andExpect(jsonPath("$.code").value("LOGIN_TEMPORARILY_BLOCKED"));
    }

    @Test
    void ownerMayEnrollTotpVoluntarily() throws Exception {
        String email = newOwner(false);
        AdminClient client = AdminClient.staff(mvc, HOST);
        client.login(email, PASSWORD).andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        String enrollmentSession = client.session();

        // 2FA könüllüdür: sahib onsuz da panelə daxil olur
        client.get("/me").andExpect(status().isOk()).andExpect(jsonPath("$.totpEnabled").value(false));
        client.get("/team").andExpect(status().isOk());

        ResultActions start = client.post("/auth/mfa/enrollment").andExpect(status().isOk());
        String secret = json(start, "$.secret");
        assertThat(json(start, "$.otpauthUri")).startsWith("otpauth://totp/").contains("secret=" + secret);

        client.post("/auth/mfa/enrollment/confirm", Map.of("code", "000000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_INVALID"));

        ResultActions confirm = client.post("/auth/mfa/enrollment/confirm",
                        Map.of("code", freshTotp(Base32.decode(secret))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10));
        assertThat(JsonPath.<List<String>>read(confirm.andReturn().getResponse().getContentAsString(),
                "$.recoveryCodes")).allMatch(code -> code.matches("^[a-z2-9]{5}-[a-z2-9]{5}$"));

        // Sessiya tokeni yeniləndi (session fixation): köhnə token artıq işləmir
        assertThat(client.session()).isNotEqualTo(enrollmentSession);
        client.get("/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.totpEnabled").value(true))
                .andExpect(jsonPath("$.recoveryCodesRemaining").value(10));
        AdminClient stale = AdminClient.staff(mvc, HOST);
        stale.useSession(enrollmentSession);
        stale.get("/me").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void totpLoginRotatesSessionAndRejectsReusedCodes() throws Exception {
        String email = newOwner(true);
        AdminClient client = AdminClient.staff(mvc, HOST);
        client.login(email, PASSWORD).andExpect(jsonPath("$.status").value("MFA_REQUIRED"));
        String pending = client.session();
        client.get("/me").andExpect(status().isForbidden());

        client.post("/auth/mfa/verify", Map.of("code", "123456"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_INVALID"));

        String code = freshTotp(TOTP_SECRET);
        client.post("/auth/mfa/verify", Map.of("code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        assertThat(client.session()).isNotEqualTo(pending);
        client.get("/me").andExpect(status().isOk()).andExpect(jsonPath("$.roles[0].role").value("COMPANY_OWNER"));

        // Eyni kod (eyni 30 saniyəlik addım) ikinci dəfə qəbul olunmur
        AdminClient attacker = AdminClient.staff(mvc, HOST);
        attacker.login(email, PASSWORD).andExpect(jsonPath("$.status").value("MFA_REQUIRED"));
        attacker.post("/auth/mfa/verify", Map.of("code", code)).andExpect(jsonPath("$.code").value("MFA_INVALID"));
    }

    @Test
    void pendingMfaSessionIsRevokedAfterFiveWrongCodes() throws Exception {
        String email = newOwner(true);
        AdminClient client = AdminClient.staff(mvc, HOST);
        client.login(email, PASSWORD);
        for (int i = 0; i < 5; i++) {
            client.post("/auth/mfa/verify", Map.of("code", "00000" + i)).andExpect(jsonPath("$.code").value("MFA_INVALID"));
        }
        client.post("/auth/mfa/verify", Map.of("code", freshTotp(TOTP_SECRET)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void recoveryCodeWorksExactlyOnceAndStepUpIsRequiredToRegenerate() throws Exception {
        String email = newOwner(false);
        AdminClient client = AdminClient.staff(mvc, HOST);
        client.login(email, PASSWORD);
        String secret = json(client.post("/auth/mfa/enrollment"), "$.secret");
        String recovery = JsonPath.<List<String>>read(client.post("/auth/mfa/enrollment/confirm",
                        Map.of("code", freshTotp(Base32.decode(secret)))).andReturn().getResponse().getContentAsString(),
                "$.recoveryCodes").getFirst();

        client.post("/me/mfa/recovery-codes")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        client.post("/auth/step-up", Map.of("code", freshTotp(Base32.decode(secret)))).andExpect(status().isNoContent());
        client.post("/me/mfa/recovery-codes").andExpect(status().isOk()).andExpect(jsonPath("$.recoveryCodes.length()").value(10));

        // Köhnə bərpa kodları yenilərini yaradandan sonra etibarsızdır
        AdminClient other = AdminClient.staff(mvc, HOST);
        other.login(email, PASSWORD);
        other.post("/auth/mfa/verify", Map.of("code", recovery.toUpperCase(Locale.ROOT)))
                .andExpect(jsonPath("$.code").value("MFA_INVALID"));
    }

    @Test
    void freshRecoveryCodeSignsInOnce() throws Exception {
        String email = newOwner(false);
        AdminClient client = AdminClient.staff(mvc, HOST);
        client.login(email, PASSWORD);
        String secret = json(client.post("/auth/mfa/enrollment"), "$.secret");
        String recovery = JsonPath.<List<String>>read(client.post("/auth/mfa/enrollment/confirm",
                        Map.of("code", freshTotp(Base32.decode(secret)))).andReturn().getResponse().getContentAsString(),
                "$.recoveryCodes").get(3);

        AdminClient first = AdminClient.staff(mvc, HOST);
        first.login(email, PASSWORD);
        first.post("/auth/mfa/verify", Map.of("code", " " + recovery.toUpperCase(Locale.ROOT) + " "))
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        first.get("/me").andExpect(jsonPath("$.recoveryCodesRemaining").value(9));

        AdminClient second = AdminClient.staff(mvc, HOST);
        second.login(email, PASSWORD);
        second.post("/auth/mfa/verify", Map.of("code", recovery)).andExpect(jsonPath("$.code").value("MFA_INVALID"));
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        AdminClient client = signedInStaff(HOST, newManager(), false);
        String token = client.session();
        String setCookie = sessionCookieHeader(client.post("/auth/logout").andExpect(status().isNoContent()));
        assertThat(setCookie).startsWith("__Host-cl_staff=;").contains("Max-Age=0");

        AdminClient replay = AdminClient.staff(mvc, HOST);
        replay.useSession(token);
        replay.get("/me").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        // Sessiyası olmayan çıxış da xətasızdır
        AdminClient.staff(mvc, HOST).post("/auth/logout").andExpect(status().isNoContent());
    }

    @Test
    void idleSessionExpiresAndBackgroundRequestsDoNotExtendIt() throws Exception {
        AdminClient client = signedInStaff(HOST, newManager(), false);
        clock.advance(Duration.ofMinutes(20));
        client.getBackground("/me").andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(11));
        client.get("/me").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void userActivityKeepsTheSessionAliveUntilTheAbsoluteLimit() throws Exception {
        AdminClient client = signedInStaff(HOST, newManager(), false);
        for (int i = 0; i < 19; i++) {
            clock.advance(Duration.ofMinutes(25));
            client.get("/me").andExpect(status().isOk());
        }
        // 19 × 25 = 475 dəqiqə fəal iş; 8 saatlıq (480 dəq) mütləq limit fəaliyyətdən asılı deyil
        clock.advance(Duration.ofMinutes(10));
        client.get("/me").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void changingPasswordSignsOutOtherDevicesAndRotatesTheCurrentSession() throws Exception {
        String email = newManager();
        AdminClient laptop = signedInStaff(HOST, email, false);
        AdminClient phone = signedInStaff(HOST, email, false);
        laptop.get("/me/sessions").andExpect(jsonPath("$.length()").value(2));
        String before = laptop.session();

        laptop.post("/me/password", Map.of("currentPassword", "Yanlış-şifrə-12345", "newPassword", "Yeni-Qəhvə-Parolu-2027"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
        laptop.post("/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "qisa"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.violations[0]").value("TOO_SHORT"));
        laptop.post("/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "Yeni-Qəhvə-Parolu-2027"))
                .andExpect(status().isNoContent());

        assertThat(laptop.session()).isNotEqualTo(before);
        laptop.get("/me").andExpect(status().isOk());
        phone.get("/me").andExpect(status().isUnauthorized());
        assertThat(mailbox.awaitMessage(email, 1).subject()).isEqualTo("Şifrəniz dəyişdirildi");

        AdminClient.staff(mvc, HOST).login(email, PASSWORD).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        AdminClient.staff(mvc, HOST).login(email, "Yeni-Qəhvə-Parolu-2027").andExpect(status().isOk());
    }

    @Test
    void userCanListAndRevokeOwnSessionsOnly() throws Exception {
        String email = newManager();
        AdminClient laptop = signedInStaff(HOST, email, false);
        AdminClient phone = signedInStaff(HOST, email, false);
        ResultActions list = laptop.get("/me/sessions").andExpect(status().isOk());
        List<Map<String, Object>> sessions = JsonPath.read(list.andReturn().getResponse().getContentAsString(), "$");
        String phoneSessionId = sessions.stream().filter(s -> !(Boolean) s.get("current"))
                .map(s -> (String) s.get("id")).findFirst().orElseThrow();
        assertThat(sessions).allSatisfy(s -> assertThat(s).doesNotContainKey("tokenHash"));

        // Başqa istifadəçinin sessiyasını ləğv etmək olmur (mövcudluğu da açıqlanmır)
        AdminClient stranger = signedInStaff(HOST, newManager(), false);
        stranger.delete("/me/sessions/" + phoneSessionId).andExpect(status().isNotFound());

        laptop.delete("/me/sessions/" + phoneSessionId).andExpect(status().isNoContent());
        phone.get("/me").andExpect(status().isUnauthorized());
        laptop.get("/me").andExpect(status().isOk());
    }

    @Test
    void preferredLanguageChangesErrorLanguage() throws Exception {
        String email = uniqueEmail("barista");
        createStaff(HOST, email, AdminRole.STAFF, branchOf(HOST), false);
        AdminClient client = signedInStaff(HOST, email, false);

        client.get("/team").andExpect(status().isForbidden()).andExpect(jsonPath("$.title").value("İcazə yoxdur"));
        client.patch("/me", Map.of("preferredLanguage", "ru")).andExpect(jsonPath("$.preferredLanguage").value("ru"));
        client.get("/team").andExpect(jsonPath("$.title").value("Нет доступа"));
        client.patch("/me", Map.of("preferredLanguage", "de")).andExpect(status().isBadRequest());
    }

    @Test
    void disabledAccountLosesAccessImmediately() throws Exception {
        String ownerEmail = newOwner(true);
        AdminClient owner = signedInStaff(HOST, ownerEmail, true);
        String baristaEmail = uniqueEmail("barista");
        UUID baristaId = createStaff(HOST, baristaEmail, AdminRole.STAFF, branchOf(HOST), false);
        AdminClient barista = signedInStaff(HOST, baristaEmail, false);
        barista.get("/me").andExpect(status().isOk());

        owner.patch("/team/members/" + baristaId + "/status", Map.of("status", "DISABLED")).andExpect(status().isNoContent());
        barista.get("/me").andExpect(status().isUnauthorized());
        AdminClient.staff(mvc, HOST).login(baristaEmail, PASSWORD).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));

        owner.patch("/team/members/" + baristaId + "/status", Map.of("status", "ACTIVE")).andExpect(status().isNoContent());
        AdminClient.staff(mvc, HOST).login(baristaEmail, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void totpCanBePausedResumedAndRemovedWithPassword() throws Exception {
        String email = newOwner(true);
        AdminClient client = signedInStaff(HOST, email, true);

        // Söndürmək üçün 2FA kodu yox, cari şifrə tələb olunur
        client.post("/me/mfa/pause", Map.of("password", "Yanlış-şifrə-12345"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
        client.post("/me/mfa/pause", Map.of("password", PASSWORD)).andExpect(status().isNoContent());
        client.get("/me").andExpect(jsonPath("$.totpEnabled").value(false)).andExpect(jsonPath("$.totpPaused").value(true));
        AdminClient.staff(mvc, HOST).login(email, PASSWORD).andExpect(jsonPath("$.status").value("AUTHENTICATED"));

        // Eyni tətbiqin kodu ilə yenidən aktivləşir (yeni QR lazım deyil)
        client.post("/me/mfa/resume", Map.of("code", "000000")).andExpect(jsonPath("$.code").value("MFA_INVALID"));
        client.post("/me/mfa/resume", Map.of("code", freshTotp(TOTP_SECRET))).andExpect(status().isNoContent());
        client.get("/me").andExpect(jsonPath("$.totpEnabled").value(true)).andExpect(jsonPath("$.totpPaused").value(false));
        AdminClient.staff(mvc, HOST).login(email, PASSWORD).andExpect(jsonPath("$.status").value("MFA_REQUIRED"));

        // Tam silinmə: sirr və bərpa kodları gedir
        client.post("/me/mfa/remove", Map.of("password", PASSWORD)).andExpect(status().isNoContent());
        client.get("/me")
                .andExpect(jsonPath("$.totpEnabled").value(false))
                .andExpect(jsonPath("$.totpPaused").value(false))
                .andExpect(jsonPath("$.recoveryCodesRemaining").value(0));
        client.post("/me/mfa/resume", Map.of("code", freshTotp(TOTP_SECRET))).andExpect(jsonPath("$.code").value("MFA_NOT_ENABLED"));
        client.post("/me/mfa/remove", Map.of("password", PASSWORD)).andExpect(jsonPath("$.code").value("MFA_NOT_ENABLED"));

        // 2FA olmadan step-up şifrə ilə olur
        client.post("/auth/step-up", Map.of("code", "123456")).andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
        client.post("/auth/step-up", Map.of("password", PASSWORD)).andExpect(status().isNoContent());
    }

    @Test
    void ownerResetsLostTotpOfAnotherOwnerWithStepUp() throws Exception {
        AdminClient owner = signedInStaff(HOST, newOwner(true), true);
        String otherEmail = newOwner(true);
        UUID otherId = TenantContext.callAs(tenant(HOST),
                () -> realms.accounts(AdminRealm.STAFF).findByEmail(otherEmail).orElseThrow().id());

        owner.post("/team/members/" + otherId + "/mfa-reset").andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        owner.post("/auth/step-up", Map.of("code", freshTotp(TOTP_SECRET))).andExpect(status().isNoContent());
        owner.post("/team/members/" + otherId + "/mfa-reset").andExpect(status().isNoContent());

        AdminClient.staff(mvc, HOST).login(otherEmail, PASSWORD)
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        // Step-up 10 dəqiqə etibarlıdır
        clock.advance(Duration.ofMinutes(11));
        owner.post("/team/members/" + otherId + "/mfa-reset").andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
    }

    @Test
    void totpCodeFromOtherSecretIsRejected() throws Exception {
        String email = newOwner(true);
        AdminClient client = AdminClient.staff(mvc, HOST);
        client.login(email, PASSWORD);
        clock.advance(Duration.ofSeconds(30));
        String foreign = TotpCodes.at("another-secret-bytes".getBytes(StandardCharsets.US_ASCII), clock.instant());
        client.post("/auth/mfa/verify", Map.of("code", foreign)).andExpect(jsonPath("$.code").value("MFA_INVALID"));
    }

    private static String sessionCookieHeader(ResultActions result) {
        return result.andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(AdminRealm.STAFF.cookieName() + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No session cookie in response"));
    }

    private static String stripTraceId(String body) {
        return body.replaceAll("\"traceId\":\"[^\"]*\"", "");
    }
}
