package az.tapstamp.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.support.IntegrationTest;
import az.tapstamp.support.TestTenants;
import az.tapstamp.tenancy.provisioning.ProvisionTenantCommand;
import az.tapstamp.tenancy.provisioning.TenantProvisioner;
import az.tapstamp.tenancy.registry.TenantDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Şirkətlər arası izolyasiya veb qatında: domen → tenant → öz bazası. */
class TenantIsolationIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TenantProvisioner provisioner;

    @Autowired
    TenantDirectory directory;

    private static RequestPostProcessor host(String host) {
        return request -> {
            request.setServerName(host);
            return request;
        };
    }

    @Test
    void eachDomainSeesOnlyItsOwnCompanyData() throws Exception {
        mvc.perform(get("/api/v1/public/company").with(host(TestTenants.ALPHA_HOST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name.az").value("Alpha Kafe"))
                .andExpect(jsonPath("$.branches[0].name.az").value("Alpha Kafe — Mərkəz"));
        mvc.perform(get("/api/v1/public/company").with(host(TestTenants.BETA_HOST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name.az").value("Beta Kafe"))
                .andExpect(jsonPath("$.branches[0].name.az").value("Beta Kafe — Mərkəz"));
    }

    @Test
    void hostMatchingIsCaseInsensitiveAndIgnoresTrailingDot() throws Exception {
        mvc.perform(get("/api/v1/public/company").with(host("BETA.TEST.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name.az").value("Beta Kafe"));
    }

    @Test
    void unknownDomainIsRejectedBeforeAnyProcessing() throws Exception {
        mvc.perform(get("/api/v1/public/company").with(host("unknown.test")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/public/company").with(host("alpha.test/../beta.test")))
                .andExpect(status().isNotFound());
    }

    @Test
    void platformDomainHasNoTenantData() throws Exception {
        mvc.perform(get("/api/v1/context").with(host("platform.localhost")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("PLATFORM"))
                .andExpect(jsonPath("$.tenant").doesNotExist());
        mvc.perform(get("/api/v1/public/company").with(host("platform.localhost")))
                .andExpect(status().isNotFound());
    }

    @Test
    void contextDescribesTheTenantWithoutSecrets() throws Exception {
        mvc.perform(get("/api/v1/context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("TENANT"))
                .andExpect(jsonPath("$.tenant.slug").value("alpha"))
                .andExpect(jsonPath("$.tenant.defaultLanguage").value("az"))
                .andExpect(jsonPath("$.tenant.timezone").value("Asia/Baku"))
                .andExpect(jsonPath("$.tenant.dbName").doesNotExist());
    }

    @Test
    void suspendedTenantIsUnavailable() throws Exception {
        var tenant = provisioner.provision(new ProvisionTenantCommand("delta",
                LocalizedText.of("Delta", null, null), "delta.test", Language.EN, "Asia/Baku"));
        mvc.perform(get("/api/v1/public/company").with(host("delta.test"))).andExpect(status().isOk());

        directory.updateStatus(tenant.id(), TenantStatus.SUSPENDED, null);
        try {
            mvc.perform(get("/api/v1/public/company").with(host("delta.test")).header("Accept-Language", "en"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("TENANT_UNAVAILABLE"))
                    .andExpect(jsonPath("$.title").value("Service temporarily unavailable"));
        } finally {
            directory.updateStatus(tenant.id(), TenantStatus.ACTIVE, null);
        }
    }

    @Test
    void tenantDefaultLanguageIsUsedWhenUserHasNoPreference() throws Exception {
        provisioner.provision(new ProvisionTenantCommand("epsilon",
                LocalizedText.of("Epsilon", null, null), "epsilon.test", Language.RU, "Asia/Baku"));
        mvc.perform(get("/api/v1/public/does-not-exist").with(host("epsilon.test")))
                .andExpect(jsonPath("$.title").value("Не найдено"));
    }

    @Test
    void tenantContextIsClearedAfterEachRequest() throws Exception {
        mvc.perform(get("/api/v1/public/company")).andExpect(status().isOk());
        assertThat(TenantContext.current()).isEmpty();
    }
}
