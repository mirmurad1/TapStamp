package az.tapstamp.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import az.tapstamp.support.AdminClient;
import az.tapstamp.support.AdminIntegrationTest;
import az.tapstamp.support.TestTenants;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Şirkətin domenləri: əlavə, əsas domen (step-up), silmə; marşrutlaşdırma dərhal dəyişir; platforma auditi. */
class PlatformDomainIT extends AdminIntegrationTest {

    private ResultActions publicCompany(String host) throws Exception {
        return mvc.perform(get("/api/v1/public/company").with(r -> {
            r.setServerName(host);
            return r;
        }));
    }

    @Test
    void platformAdminManagesTenantDomains() throws Exception {
        String email = uniqueEmail("super");
        createPlatformAdmin(email, true);
        AdminClient admin = signedInPlatformAdmin(email);
        String tenantId = tenant(TestTenants.ALPHA_HOST).id().toString();
        String base = "/tenants/" + tenantId + "/domains";
        String extra = "alpha-" + UUID.randomUUID().toString().substring(0, 8) + ".test";

        String primaryId = json(admin.get(base)
                .andExpect(jsonPath("$[0].hostname").value(TestTenants.ALPHA_HOST))
                .andExpect(jsonPath("$[0].primary").value(true)), "$[0].id");

        ResultActions added = admin.post(base, Map.of("hostname", " " + extra.toUpperCase(java.util.Locale.ROOT) + " "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hostname").value(extra))
                .andExpect(jsonPath("$.tlsStatus").value("PENDING"));
        String extraId = json(added, "$.id");
        admin.post(base, Map.of("hostname", extra)).andExpect(status().isConflict());
        admin.post(base, Map.of("hostname", TestTenants.BETA_HOST)).andExpect(status().isConflict());
        admin.post(base, Map.of("hostname", "not a host")).andExpect(status().isBadRequest());
        admin.post(base, Map.of("hostname", "platform.localhost")).andExpect(status().isBadRequest());
        admin.post("/tenants/" + UUID.randomUUID() + "/domains", Map.of("hostname", "x-" + extra)).andExpect(status().isNotFound());

        // Yeni domen dərhal həmin şirkətə yönəlir
        publicCompany(extra).andExpect(status().isOk());

        try {
            admin.post(base + "/" + extraId + "/primary").andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
            admin.post("/auth/step-up", Map.of("code", freshTotp(TOTP_SECRET))).andExpect(status().isNoContent());
            admin.post(base + "/" + extraId + "/primary").andExpect(jsonPath("$.primary").value(true));
            admin.get(base).andExpect(jsonPath("$[0].hostname").value(extra));
            admin.delete(base + "/" + extraId).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DOMAIN_IS_PRIMARY"));
        } finally {
            admin.post(base + "/" + primaryId + "/primary").andExpect(jsonPath("$.primary").value(true));
        }
        admin.delete(base + "/" + extraId).andExpect(status().isNoContent());
        publicCompany(extra).andExpect(status().is4xxClientError());
        publicCompany(TestTenants.ALPHA_HOST).andExpect(status().isOk());

        admin.get("/audit-logs?size=50&action=TENANT_DOMAIN_ADDED")
                .andExpect(jsonPath("$.items[?(@.targetId == '" + tenantId + "')].details").isNotEmpty());
        admin.get("/audit-logs?action=bad").andExpect(status().isBadRequest());
    }

    @Test
    void companyStaffCannotManageDomains() throws Exception {
        String ownerEmail = uniqueEmail("owner");
        createStaff(TestTenants.ALPHA_HOST, ownerEmail, az.tapstamp.security.admin.AdminRole.COMPANY_OWNER, null, true);
        AdminClient owner = signedInStaff(TestTenants.ALPHA_HOST, ownerEmail, true);
        String tenantId = tenant(TestTenants.ALPHA_HOST).id().toString();
        owner.perform(get("/api/v1/platform/tenants/" + tenantId + "/domains")).andExpect(status().isNotFound());
    }
}
