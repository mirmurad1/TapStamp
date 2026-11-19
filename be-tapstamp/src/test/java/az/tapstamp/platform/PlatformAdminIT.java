package az.tapstamp.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import az.tapstamp.notification.mail.MailMessage;
import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.AdminIntegrationTest;
import az.tapstamp.support.TestMailbox;
import az.tapstamp.support.TestTenants;
import az.tapstamp.tenancy.TenantContext;
import org.junit.jupiter.api.Test;

/** Platforma adminləri: yalnız platforma domeni, məcburi TOTP, şirkətlərin yaradılması və idarəsi. */
class PlatformAdminIT extends AdminIntegrationTest {

    private AdminClient superAdmin() throws Exception {
        String email = uniqueEmail("super");
        createPlatformAdmin(email, true);
        return signedInPlatformAdmin(email);
    }

    @Test
    void superAdminSignsInWithTotpOnPlatformDomainOnly() throws Exception {
        String email = uniqueEmail("super");
        createPlatformAdmin(email, true);

        AdminClient client = AdminClient.platform(mvc);
        client.login(email, "Yanlış-şifrə-12345").andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        client.login(email, PASSWORD).andExpect(jsonPath("$.status").value("MFA_REQUIRED"));
        client.post("/auth/mfa/verify", Map.of("code", freshTotp(TOTP_SECRET)))
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        client.get("/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realm").value("PLATFORM"))
                .andExpect(jsonPath("$.roles[0].role").value("SUPER_ADMIN"));

        // Platforma girişi şirkət domenində mövcud deyil
        AdminClient onTenant = AdminClient.platform(mvc).withSessionOn(TestTenants.ALPHA_HOST, AdminRealm.PLATFORM);
        onTenant.post("/auth/login", Map.of("email", email, "password", PASSWORD)).andExpect(status().isNotFound());
        onTenant.get("/me").andExpect(status().isNotFound());
    }

    @Test
    void platformAdminWithoutTotpSignsInDirectly() throws Exception {
        String email = uniqueEmail("super-new");
        createPlatformAdmin(email, false);
        AdminClient client = AdminClient.platform(mvc);
        client.login(email, PASSWORD).andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        client.get("/tenants").andExpect(status().isOk());
    }

    @Test
    void staffAccountsCannotSignInOnPlatformAndViceVersa() throws Exception {
        String staffEmail = uniqueEmail("staff");
        createStaff(TestTenants.ALPHA_HOST, staffEmail, AdminRole.COMPANY_OWNER, null, true);
        AdminClient.platform(mvc).login(staffEmail, PASSWORD).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));

        String platformEmail = uniqueEmail("super");
        createPlatformAdmin(platformEmail, true);
        AdminClient.staff(mvc, TestTenants.ALPHA_HOST).login(platformEmail, PASSWORD)
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        // Şirkət API-si platforma domenində mövcud deyil
        AdminClient.staff(mvc, TestTenants.PLATFORM_HOST).login(platformEmail, PASSWORD).andExpect(status().isNotFound());
    }

    @Test
    void platformSessionCookieDoesNotWorkOnCompanyDomain() throws Exception {
        AdminClient admin = superAdmin();
        // Eyni token işçi cookie-si kimi şirkət domeninə göndərilsə də, şirkətin bazasında belə sessiya yoxdur
        AdminClient forged = admin.withSessionOn(TestTenants.ALPHA_HOST, AdminRealm.STAFF);
        forged.get("/me").andExpect(status().isUnauthorized());
    }

    @Test
    void listsCompaniesWithoutCustomerData() throws Exception {
        AdminClient admin = superAdmin();
        admin.get("/tenants?size=50")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.slug == 'alpha')].primaryDomain").value(TestTenants.ALPHA_HOST))
                .andExpect(jsonPath("$.items[?(@.slug == 'beta')].status").value("ACTIVE"));
        String alphaId = tenant(TestTenants.ALPHA_HOST).id().toString();
        String body = admin.get("/tenants/" + alphaId + "/stats").andExpect(status().isOk())
                .andExpect(jsonPath("$.branches").isNumber())
                .andExpect(jsonPath("$.totalCustomers").isNumber())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("phone", "+994");
        // Loqo yalnız mövcud şirkət üçün; yoxdursa 404 (panel onda adın ilk hərfini göstərir)
        admin.get("/tenants/" + UUID.randomUUID() + "/logo").andExpect(status().isNotFound());
        // DB şifrələri və adları heç vaxt qaytarılmır
        assertThat(admin.get("/tenants").andReturn().getResponse().getContentAsString())
                .doesNotContain("password", "dbName", "loyalty_t_");
    }

    @Test
    void provisioningNewCompanyRequiresStepUpAndOwnerInvitationLandsOnItsDomain() throws Exception {
        AdminClient admin = superAdmin();
        String slug = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String domain = slug + ".test";
        Map<String, Object> request = Map.of(
                "slug", slug,
                "displayName", Map.of("az", "Yeni Kafe", "en", "New Cafe"),
                "primaryDomain", domain,
                "defaultLanguage", "az",
                "timezone", "Asia/Baku");

        admin.post("/tenants", request).andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        admin.post("/auth/step-up", Map.of("code", freshTotp(TOTP_SECRET))).andExpect(status().isNoContent());
        String tenantId = json(admin.post("/tenants", request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.primaryDomain").value(domain)), "$.id");
        admin.post("/tenants", request).andExpect(jsonPath("$.code").value("ALREADY_EXISTS"));

        String ownerEmail = uniqueEmail("owner");
        admin.post("/tenants/" + tenantId + "/owner-invitations", Map.of("email", ownerEmail, "language", "az"))
                .andExpect(status().isCreated());
        MailMessage mail = mailbox.awaitMessage(ownerEmail, 1);
        assertThat(TestMailbox.link(mail)).startsWith("https://" + domain + "/admin/invitation#token=");
        assertThat(mail.subject()).isEqualTo("Yeni Kafe — komandaya dəvət");

        AdminClient owner = AdminClient.staff(mvc, domain);
        owner.post("/auth/invitations/accept",
                        Map.of("token", TestMailbox.token(mail), "displayName", "Sahib", "password", PASSWORD))
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        admin.get("/tenants/" + tenantId + "/owners")
                .andExpect(jsonPath("$[0].email").value(ownerEmail))
                .andExpect(jsonPath("$[0].totpEnabled").value(false));

        // Dayandırılmış şirkətin domeni xidmət göstərmir; bərpadan sonra yenidən işləyir
        admin.patch("/tenants/" + tenantId + "/status", Map.of("status", "SUSPENDED"))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/v1/public/company").with(r -> {
            r.setServerName(domain);
            return r;
        })).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TENANT_UNAVAILABLE"));
        admin.patch("/tenants/" + tenantId + "/status", Map.of("status", "ACTIVE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/public/company").with(r -> {
            r.setServerName(domain);
            return r;
        })).andExpect(status().isOk()).andExpect(jsonPath("$.name.en").value("New Cafe"));
    }

    @Test
    void ownerWhoLostTotpAndRecoveryCodesIsResetByPlatformWithStepUp() throws Exception {
        String ownerEmail = uniqueEmail("lost-phone");
        UUID ownerId = createStaff(TestTenants.ALPHA_HOST, ownerEmail, AdminRole.COMPANY_OWNER, null, true);
        AdminClient ownerSession = signedInStaff(TestTenants.ALPHA_HOST, ownerEmail, true);
        String alphaId = tenant(TestTenants.ALPHA_HOST).id().toString();

        AdminClient admin = superAdmin();
        admin.post("/tenants/" + alphaId + "/owners/" + ownerId + "/mfa-reset")
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        admin.post("/auth/step-up", Map.of("code", freshTotp(TOTP_SECRET)));
        admin.post("/tenants/" + alphaId + "/owners/" + UUID.randomUUID() + "/mfa-reset").andExpect(status().isNotFound());
        admin.post("/tenants/" + alphaId + "/owners/" + ownerId + "/mfa-reset").andExpect(status().isNoContent());

        ownerSession.get("/me").andExpect(status().isUnauthorized());
        AdminClient.staff(mvc, TestTenants.ALPHA_HOST).login(ownerEmail, PASSWORD)
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        // Şirkətin audit jurnalında platformanın müdaxiləsi görünür
        Integer audited = TenantContext.callAs(tenant(TestTenants.ALPHA_HOST), () -> tenantJdbc.sql("""
                        SELECT count(*) FROM audit_logs
                        WHERE action = 'STAFF_MFA_RESET' AND actor_type = 'PLATFORM' AND target_id = :id""")
                .param("id", ownerId.toString()).query(Integer.class).single());
        assertThat(audited).isEqualTo(1);

        // Platforma paneli şirkətin jurnalını görür: platformanın müdaxiləsi "PLATFORM" icraçısı ilə, filtr işləyir
        admin.get("/tenants/" + alphaId + "/audit-logs?action=STAFF_MFA_RESET&size=50")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.targetId == '" + ownerId + "')].actorType").value("PLATFORM"))
                .andExpect(jsonPath("$.items[*].action").value(everyItem(is("STAFF_MFA_RESET"))));
        admin.get("/tenants/" + alphaId + "/audit-logs/actions")
                .andExpect(jsonPath("$").value(hasItem("STAFF_MFA_RESET")));
        admin.get("/tenants/" + alphaId + "/audit-logs?action=bad-value").andExpect(status().isBadRequest());
    }

    @Test
    void ownerWhoForgotPasswordGetsResetLinkFromPlatform() throws Exception {
        String ownerEmail = uniqueEmail("forgot");
        UUID ownerId = createStaff(TestTenants.ALPHA_HOST, ownerEmail, AdminRole.COMPANY_OWNER, null, false);
        String path = "/tenants/" + tenant(TestTenants.ALPHA_HOST).id() + "/owners/" + ownerId + "/password-reset-link";

        AdminClient admin = superAdmin();
        admin.post(path).andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        admin.post("/auth/step-up", Map.of("code", freshTotp(TOTP_SECRET)));
        admin.post(path.replace(ownerId.toString(), UUID.randomUUID().toString())).andExpect(status().isNotFound());
        String link = json(admin.post(path).andExpect(status().isOk()), "$.link");
        assertThat(link).startsWith("https://" + TestTenants.ALPHA_HOST + "/admin/reset-password#token=");

        // Link adi bərpa linki kimi işləyir və bir dəfəlikdir
        String token = link.substring(link.indexOf("#token=") + "#token=".length());
        String newPassword = "Yeni-Sahib-Parolu-2026";
        AdminClient anonymous = AdminClient.staff(mvc, TestTenants.ALPHA_HOST);
        anonymous.post("/auth/password/reset", Map.of("token", token, "newPassword", newPassword)).andExpect(status().isNoContent());
        anonymous.post("/auth/password/reset", Map.of("token", token, "newPassword", newPassword + "!"))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        AdminClient.staff(mvc, TestTenants.ALPHA_HOST).login(ownerEmail, newPassword).andExpect(status().isOk());
    }
}
