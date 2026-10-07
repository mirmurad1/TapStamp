package az.cafeloyalty.tenancy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.cafeloyalty.support.AdminIntegrationTest;
import az.cafeloyalty.support.TestTenants;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/** Daxili TLS domen siyahısı: yalnız düzgün sirr token ilə, istənilən Host-dan (certbot backend-ə birbaşa müraciət edir). */
class InternalTlsIT extends AdminIntegrationTest {

    @Test
    void domainListNeedsInternalToken() throws Exception {
        mvc.perform(get("/internal/tls/domains").with(r -> {
                    r.setServerName("backend");
                    return r;
                }).header("X-Internal-Token", TestTenants.INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(TestTenants.ALPHA_HOST + "\n")))
                .andExpect(content().string(Matchers.containsString(TestTenants.BETA_HOST + "\n")))
                .andExpect(content().string(Matchers.containsString("platform.localhost\n")));

        mvc.perform(get("/internal/tls/domains").header("X-Internal-Token", "wrong")).andExpect(status().isNotFound());
        mvc.perform(get("/internal/tls/domains")).andExpect(status().isNotFound());
        mvc.perform(get("/internal/tls/domains").with(r -> {
            r.setServerName(TestTenants.ALPHA_HOST);
            return r;
        })).andExpect(status().isNotFound());
    }
}
