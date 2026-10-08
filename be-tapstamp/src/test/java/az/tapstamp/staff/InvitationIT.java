package az.tapstamp.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.notification.mail.MailMessage;
import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.AdminIntegrationTest;
import az.tapstamp.support.TestMailbox;
import az.tapstamp.support.TestTenants;
import org.junit.jupiter.api.Test;

/** İşçi dəvətləri: icazələr, birdəfəlik link, şirkətlər arası izolyasiya. */
class InvitationIT extends AdminIntegrationTest {

    private static final String HOST = TestTenants.ALPHA_HOST;
    private static final String NEW_PASSWORD = "Dəvət-Qəbul-Parolu-2026";

    private AdminClient owner() throws Exception {
        String email = uniqueEmail("owner");
        createStaff(HOST, email, AdminRole.COMPANY_OWNER, null, true);
        return signedInStaff(HOST, email, true);
    }

    private static Map<String, Object> invite(String email, AdminRole role, UUID branchId, String language) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("role", role.name());
        body.put("branchId", branchId == null ? null : branchId.toString());
        body.put("language", language);
        return body;
    }

    @Test
    void ownerInvitesManagerWhoAcceptsAndSignsIn() throws Exception {
        AdminClient owner = owner();
        String email = uniqueEmail("new-manager");
        UUID branch = branchOf(HOST);
        owner.post("/team/invitations", invite(email, AdminRole.BRANCH_MANAGER, branch, "en"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("BRANCH_MANAGER"));
        owner.get("/team/invitations").andExpect(jsonPath("$[?(@.email == '" + email + "')]").exists());

        MailMessage mail = mailbox.awaitMessage(email, 1);
        assertThat(mail.subject()).isEqualTo("Alpha Kafe EN — invitation to join the team");
        assertThat(TestMailbox.link(mail)).startsWith("https://" + HOST + "/admin/invitation#token=");
        assertThat(mail.textBody()).contains("72 hours");
        String token = TestMailbox.token(mail);

        AdminClient invitee = AdminClient.staff(mvc, HOST);
        invitee.post("/auth/invitations/inspect", Map.of("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("BRANCH_MANAGER"))
                .andExpect(jsonPath("$.companyName").value("Alpha Kafe EN"))
                .andExpect(jsonPath("$.branchName").value("Alpha Kafe — Mərkəz"))
                .andExpect(jsonPath("$.language").value("en"));

        invitee.post("/auth/invitations/accept", Map.of("token", token, "displayName", "Yeni Menecer", "password", "qisa"))
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
        invitee.post("/auth/invitations/accept", Map.of("token", token, "displayName", "Yeni Menecer", "password", NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        invitee.get("/me")
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Yeni Menecer"))
                .andExpect(jsonPath("$.preferredLanguage").value("en"))
                .andExpect(jsonPath("$.roles[0].branchId").value(branch.toString()));

        AdminClient.staff(mvc, HOST)
                .post("/auth/invitations/accept", Map.of("token", token, "displayName", "X", "password", NEW_PASSWORD))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        owner.get("/team").andExpect(jsonPath("$[?(@.email == '" + email + "')].status").value("ACTIVE"));
    }

    @Test
    void invitedOwnerSignsInWithoutMandatoryTotp() throws Exception {
        AdminClient owner = owner();
        String email = uniqueEmail("co-owner");
        owner.post("/team/invitations", invite(email, AdminRole.COMPANY_OWNER, null, "az")).andExpect(status().isCreated());
        String token = TestMailbox.token(mailbox.awaitMessage(email, 1));
        AdminClient.staff(mvc, HOST)
                .post("/auth/invitations/accept", Map.of("token", token, "displayName", "Ortaq", "password", NEW_PASSWORD))
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));
    }

    @Test
    void managerMayOnlyInviteBaristasToOwnBranch() throws Exception {
        UUID ownBranch = branchOf(HOST);
        UUID otherBranch = addBranch(HOST, "Başqa filial " + UUID.randomUUID());
        String managerEmail = uniqueEmail("manager");
        createStaff(HOST, managerEmail, AdminRole.BRANCH_MANAGER, ownBranch, false);
        AdminClient manager = signedInStaff(HOST, managerEmail, false);

        manager.post("/team/invitations", invite(uniqueEmail("b"), AdminRole.STAFF, ownBranch, "az"))
                .andExpect(status().isCreated());
        manager.post("/team/invitations", invite(uniqueEmail("b"), AdminRole.STAFF, otherBranch, "az"))
                .andExpect(status().isForbidden());
        manager.post("/team/invitations", invite(uniqueEmail("m"), AdminRole.BRANCH_MANAGER, ownBranch, "az"))
                .andExpect(status().isForbidden());
        manager.post("/team/invitations", invite(uniqueEmail("o"), AdminRole.COMPANY_OWNER, null, "az"))
                .andExpect(status().isForbidden());
        manager.post("/team/invitations", invite(uniqueEmail("s"), AdminRole.SUPER_ADMIN, null, "az"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidRoleBranchCombinationsAndDuplicatesAreRejected() throws Exception {
        AdminClient owner = owner();
        owner.post("/team/invitations", invite(uniqueEmail("x"), AdminRole.STAFF, null, "az"))
                .andExpect(status().isBadRequest());
        owner.post("/team/invitations", invite(uniqueEmail("x"), AdminRole.COMPANY_OWNER, branchOf(HOST), "az"))
                .andExpect(status().isBadRequest());
        owner.post("/team/invitations", invite(uniqueEmail("x"), AdminRole.STAFF, UUID.randomUUID(), "az"))
                .andExpect(status().isNotFound());

        String existing = uniqueEmail("existing");
        createStaff(HOST, existing, AdminRole.STAFF, branchOf(HOST), false);
        owner.post("/team/invitations", invite(existing, AdminRole.STAFF, branchOf(HOST), "az"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_EXISTS"));
    }

    @Test
    void invitationTokenIsBoundToItsCompany() throws Exception {
        AdminClient owner = owner();
        String email = uniqueEmail("isolated");
        owner.post("/team/invitations", invite(email, AdminRole.STAFF, branchOf(HOST), "az")).andExpect(status().isCreated());
        String token = TestMailbox.token(mailbox.awaitMessage(email, 1));
        AdminClient.staff(mvc, TestTenants.BETA_HOST).post("/auth/invitations/inspect", Map.of("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void revokedAndExpiredInvitationsCannotBeUsed() throws Exception {
        AdminClient owner = owner();
        String revokedEmail = uniqueEmail("revoked");
        String id = json(owner.post("/team/invitations", invite(revokedEmail, AdminRole.STAFF, branchOf(HOST), "az")), "$.id");
        String revokedToken = TestMailbox.token(mailbox.awaitMessage(revokedEmail, 1));
        owner.delete("/team/invitations/" + id).andExpect(status().isNoContent());
        AdminClient.staff(mvc, HOST).post("/auth/invitations/inspect", Map.of("token", revokedToken))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        String expiredEmail = uniqueEmail("expired");
        owner.post("/team/invitations", invite(expiredEmail, AdminRole.STAFF, branchOf(HOST), "az"));
        String expiredToken = TestMailbox.token(mailbox.awaitMessage(expiredEmail, 1));
        clock.advance(Duration.ofHours(73));
        AdminClient.staff(mvc, HOST).post("/auth/invitations/inspect", Map.of("token", expiredToken))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void reinvitingTheSameEmailInvalidatesThePreviousLink() throws Exception {
        AdminClient owner = owner();
        String email = uniqueEmail("twice");
        owner.post("/team/invitations", invite(email, AdminRole.STAFF, branchOf(HOST), "az"));
        String first = TestMailbox.token(mailbox.awaitMessage(email, 1));
        owner.post("/team/invitations", invite(email, AdminRole.STAFF, branchOf(HOST), "ru"));
        MailMessage second = mailbox.awaitMessage(email, 2);
        assertThat(second.subject()).isEqualTo("Alpha Kafe RU — приглашение в команду");

        AdminClient.staff(mvc, HOST).post("/auth/invitations/inspect", Map.of("token", first))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        AdminClient.staff(mvc, HOST).post("/auth/invitations/inspect", Map.of("token", TestMailbox.token(second)))
                .andExpect(status().isOk());
    }
}
