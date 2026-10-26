package az.tapstamp.security.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import az.tapstamp.support.AdminClient;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.support.AdminIntegrationTest;
import az.tapstamp.support.TestTenants;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Giriş nəzarəti: bütün admin/platforma endpoint-lərinin inventarı (sessiyasız heç biri işləmir), rol matrisi,
 * sessiyanın başqa şirkətdə və MFA tamamlanmadan işləməməsi.
 */
class AdminAccessIT extends AdminIntegrationTest {

    /** Sessiyasız açıq olan admin endpoint-ləri (giriş, şifrə bərpası, dəvət). Siyahıdan kənar hər şey 401 olmalıdır. */
    private static final Set<String> PUBLIC_AUTH_ENDPOINTS = Set.of(
            "GET /api/v1/admin/auth/session",
            "GET /api/v1/platform/auth/session",
            "POST /api/v1/admin/auth/login",
            "POST /api/v1/admin/auth/logout",
            "POST /api/v1/admin/auth/password/forgot",
            "POST /api/v1/admin/auth/password/reset",
            "POST /api/v1/admin/auth/invitations/inspect",
            "POST /api/v1/admin/auth/invitations/accept",
            "POST /api/v1/platform/auth/login",
            "POST /api/v1/platform/auth/logout",
            "POST /api/v1/platform/auth/password/forgot",
            "POST /api/v1/platform/auth/password/reset");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private record Endpoint(HttpMethod method, String pattern) {
        String key() {
            return method.name() + " " + pattern;
        }

        String concretePath() {
            return pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        }
    }

    private List<Endpoint> endpoints(String prefix) {
        List<Endpoint> result = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith(prefix)) {
                    continue;
                }
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                for (RequestMethod method : methods) {
                    result.add(new Endpoint(method.asHttpMethod(), pattern));
                }
            }
        }
        return result;
    }

    @Test
    void everyProtectedEndpointRejectsAnonymousRequests() throws Exception {
        List<Endpoint> staff = endpoints("/api/v1/admin/");
        List<Endpoint> platform = endpoints("/api/v1/platform/");
        assertThat(staff).hasSizeGreaterThan(15);
        assertThat(platform).hasSizeGreaterThan(15);

        for (Endpoint endpoint : staff) {
            if (!PUBLIC_AUTH_ENDPOINTS.contains(endpoint.key())) {
                AdminClient.staff(mvc, TestTenants.ALPHA_HOST)
                        .perform(MockMvcRequestBuilders.request(endpoint.method(), endpoint.concretePath())
                                .contentType("application/json").content("{}"))
                        .andExpect(status().isUnauthorized());
            }
        }
        for (Endpoint endpoint : platform) {
            if (!PUBLIC_AUTH_ENDPOINTS.contains(endpoint.key())) {
                AdminClient.platform(mvc)
                        .perform(MockMvcRequestBuilders.request(endpoint.method(), endpoint.concretePath())
                                .contentType("application/json").content("{}"))
                        .andExpect(status().isUnauthorized());
                // Platforma endpoint-ləri şirkət domenində ümumiyyətlə mövcud deyil
                AdminClient.staff(mvc, TestTenants.ALPHA_HOST)
                        .perform(MockMvcRequestBuilders.request(endpoint.method(), endpoint.concretePath())
                                .contentType("application/json").content("{}"))
                        .andExpect(status().isNotFound());
            }
        }
    }

    /** Müştərinin sessiyasız açıq endpoint-ləri. */
    private static final Set<String> PUBLIC_CUSTOMER_ENDPOINTS = Set.of(
            "GET /api/v1/auth/session",
            "POST /api/v1/auth/otp/request",
            "POST /api/v1/auth/otp/verify",
            "POST /api/v1/auth/register",
            "POST /api/v1/auth/refresh",
            "POST /api/v1/auth/logout",
            "GET /api/v1/taps/{token}");

    @Test
    void everyCustomerEndpointRejectsAnonymousRequests() throws Exception {
        List<Endpoint> customer = new ArrayList<>(endpoints("/api/v1/auth/"));
        customer.addAll(endpoints("/api/v1/me"));
        customer.addAll(endpoints("/api/v1/taps/"));
        assertThat(customer).hasSizeGreaterThan(10);
        for (Endpoint endpoint : customer) {
            if (!PUBLIC_CUSTOMER_ENDPOINTS.contains(endpoint.key())) {
                AdminClient.staff(mvc, TestTenants.ALPHA_HOST)
                        .perform(MockMvcRequestBuilders.request(endpoint.method(), endpoint.concretePath())
                                .contentType("application/json").content("{}"))
                        .andExpect(status().isUnauthorized());
            }
        }
    }

    @Test
    void everyEndpointIsUnderAKnownPrefix() {
        List<String> unexpected = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            for (String pattern : info.getPatternValues()) {
                boolean known = pattern.startsWith("/api/v1/admin/") || pattern.equals("/api/v1/admin")
                        || pattern.startsWith("/api/v1/auth/") || pattern.equals("/api/v1/me")
                        || pattern.startsWith("/api/v1/me/")
                        || pattern.startsWith("/api/v1/platform/") || pattern.startsWith("/api/v1/public/")
                        || pattern.startsWith("/api/v1/taps/") || pattern.startsWith("/t/")
                        || pattern.equals("/api/v1/site/demo-requests")
                        || pattern.equals("/api/v1/csrf") || pattern.equals("/api/v1/context")
                        || pattern.equals("/manifest.webmanifest") || pattern.startsWith("/internal/")
                        || pattern.startsWith("/api/v1/docs") || pattern.startsWith("/api/v1/openapi.json")
                        || pattern.startsWith("/swagger-ui") || pattern.equals("/error");
                if (!known) {
                    unexpected.add(pattern);
                }
            }
        }
        assertThat(unexpected).as("yeni endpoint-lər üçün SecurityConfig və bu test yenilənməlidir").isEmpty();
    }

    @Test
    void roleMatrixForTeamManagement() throws Exception {
        String host = TestTenants.ALPHA_HOST;
        UUID branch = branchOf(host);
        String ownerEmail = uniqueEmail("owner");
        createStaff(host, ownerEmail, AdminRole.COMPANY_OWNER, null, true);
        String managerEmail = uniqueEmail("manager");
        createStaff(host, managerEmail, AdminRole.BRANCH_MANAGER, branch, false);
        String baristaEmail = uniqueEmail("barista");
        UUID baristaId = createStaff(host, baristaEmail, AdminRole.STAFF, branch, false);
        String otherOwnerEmail = uniqueEmail("owner2");
        UUID otherOwnerId = createStaff(host, otherOwnerEmail, AdminRole.COMPANY_OWNER, null, true);

        AdminClient owner = signedInStaff(host, ownerEmail, true);
        AdminClient manager = signedInStaff(host, managerEmail, false);
        AdminClient barista = signedInStaff(host, baristaEmail, false);

        owner.get("/team").andExpect(status().isOk());
        manager.get("/team").andExpect(status().isOk());
        barista.get("/team").andExpect(status().isForbidden());
        barista.get("/team/invitations").andExpect(status().isForbidden());

        // Menecer yalnız öz filialının baristasını idarə edə bilər, sahibi yox
        manager.patch("/team/members/" + otherOwnerId + "/status", Map.of("status", "DISABLED"))
                .andExpect(status().isNotFound());
        manager.post("/team/members/" + baristaId + "/mfa-reset").andExpect(status().isForbidden());
        manager.patch("/team/members/" + baristaId + "/status", Map.of("status", "DISABLED"))
                .andExpect(status().isNoContent());
        manager.patch("/team/members/" + baristaId + "/status", Map.of("status", "ACTIVE"))
                .andExpect(status().isNoContent());

        // Heç kim özünü bloklaya bilməz
        String ownerId = json(owner.get("/me"), "$.id");
        owner.patch("/team/members/" + ownerId + "/status", Map.of("status", "DISABLED")).andExpect(status().isForbidden());
        owner.patch("/team/members/" + otherOwnerId + "/status", Map.of("status", "INVITED"))
                .andExpect(status().isBadRequest());

        // Silinmə: menecer sahibi silə bilməz, heç kim özünü silə bilməz
        manager.delete("/team/members/" + otherOwnerId).andExpect(status().isNotFound());
        owner.delete("/team/members/" + ownerId).andExpect(status().isForbidden());
    }

    @Test
    void deletedMemberIsSoftDeletedAndCanBeInvitedAgain() throws Exception {
        String host = TestTenants.ALPHA_HOST;
        UUID branch = branchOf(host);
        String ownerEmail = uniqueEmail("owner");
        createStaff(host, ownerEmail, AdminRole.COMPANY_OWNER, null, true);
        String baristaEmail = uniqueEmail("barista");
        UUID baristaId = createStaff(host, baristaEmail, AdminRole.STAFF, branch, false);
        AdminClient owner = signedInStaff(host, ownerEmail, true);
        AdminClient barista = signedInStaff(host, baristaEmail, false);

        owner.delete("/team/members/" + baristaId).andExpect(status().isNoContent());

        // Sessiya dərhal bağlanır, giriş mümkün deyil, siyahıda görünmür, təkrar əməliyyat — 404
        barista.get("/me").andExpect(status().isUnauthorized());
        AdminClient.staff(mvc, host).login(baristaEmail, PASSWORD).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        owner.get("/team").andExpect(jsonPath("$[?(@.id == '" + baristaId + "')]").isEmpty());
        owner.delete("/team/members/" + baristaId).andExpect(status().isNotFound());
        owner.patch("/team/members/" + baristaId + "/status", Map.of("status", "ACTIVE")).andExpect(status().isNotFound());

        // Sətir bazada qalır (soft delete), kim və nə vaxt sildiyi qeyd olunur
        Map<String, Object> row = TenantContext.callAs(tenant(host), () -> tenantJdbc.sql(
                        "SELECT status, deleted_at, deleted_by FROM staff_users WHERE id = :id")
                .param("id", baristaId).query().singleRow());
        assertThat(row.get("status")).isEqualTo("DELETED");
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("deleted_by").toString()).isEqualTo(json(owner.get("/me"), "$.id"));

        // Eyni email yenidən dəvət oluna bilər
        owner.post("/team/invitations", Map.of("email", baristaEmail, "role", "STAFF", "branchId", branch.toString(),
                "language", "az")).andExpect(status().isCreated());
    }

    @Test
    void sessionFromOneCompanyIsUselessInAnother() throws Exception {
        String email = uniqueEmail("alpha-staff");
        createStaff(TestTenants.ALPHA_HOST, email, AdminRole.BRANCH_MANAGER, branchOf(TestTenants.ALPHA_HOST), false);
        AdminClient alpha = signedInStaff(TestTenants.ALPHA_HOST, email, false);
        alpha.get("/me").andExpect(status().isOk());

        AdminClient replayedOnBeta = alpha.withSessionOn(TestTenants.BETA_HOST, AdminRealm.STAFF);
        replayedOnBeta.get("/me").andExpect(status().isUnauthorized());
        replayedOnBeta.get("/team").andExpect(status().isUnauthorized());
        // Eyni email beta-da mövcud deyil
        AdminClient.staff(mvc, TestTenants.BETA_HOST).login(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void pendingMfaSessionCannotReachTheApi() throws Exception {
        String email = uniqueEmail("pending");
        createStaff(TestTenants.ALPHA_HOST, email, AdminRole.COMPANY_OWNER, null, true);
        AdminClient client = AdminClient.staff(mvc, TestTenants.ALPHA_HOST);
        client.login(email, PASSWORD);
        client.get("/me").andExpect(status().isForbidden());
        client.get("/me/sessions").andExpect(status().isForbidden());
        client.post("/auth/step-up", Map.of("code", "123456")).andExpect(status().isForbidden());
        client.post("/auth/mfa/enrollment").andExpect(status().isForbidden());
        // Pending sessiyanın ömrü 10 dəqiqədir
        clock.advance(Duration.ofMinutes(11));
        client.post("/auth/mfa/verify", Map.of("code", freshTotp(TOTP_SECRET))).andExpect(status().isUnauthorized());
    }

    @Test
    void stateChangingRequestsWithoutCsrfTokenAreRejected() throws Exception {
        String email = uniqueEmail("csrf");
        createStaff(TestTenants.ALPHA_HOST, email, AdminRole.BRANCH_MANAGER, branchOf(TestTenants.ALPHA_HOST), false);
        AdminClient client = signedInStaff(TestTenants.ALPHA_HOST, email, false);
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/auth/logout")
                        .with(r -> {
                            r.setServerName(TestTenants.ALPHA_HOST);
                            return r;
                        })
                        .cookie(new Cookie(AdminRealm.STAFF.cookieName(), client.session())))
                .andExpect(status().isForbidden());
        client.get("/me").andExpect(status().isOk());
    }
}
