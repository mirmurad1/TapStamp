package az.tapstamp.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.tapstamp.support.AdminIntegrationTest;
import az.tapstamp.support.TestTenants;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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
                .andExpect(content().string(Matchers.containsString("platform.localhost\n")))
                .andExpect(content().string(Matchers.containsString(TestTenants.SITE_HOST + "\n")))
                .andExpect(content().string(Matchers.containsString("www." + TestTenants.SITE_HOST + "\n")));

        mvc.perform(get("/internal/tls/domains").header("X-Internal-Token", "wrong")).andExpect(status().isNotFound());
        mvc.perform(get("/internal/tls/domains")).andExpect(status().isNotFound());
        mvc.perform(get("/internal/tls/domains").with(r -> {
            r.setServerName(TestTenants.ALPHA_HOST);
            return r;
        })).andExpect(status().isNotFound());
    }

    @Test
    void certbotReportsCertificateStatus() throws Exception {
        String report = TestTenants.BETA_HOST + " ISSUED\nunknown.example FAILED\nbroken line\n";
        mvc.perform(post("/internal/tls/status").contentType(MediaType.TEXT_PLAIN).content(report))
                .andExpect(status().isNotFound());
        mvc.perform(post("/internal/tls/status").with(r -> {
                    r.setServerName("backend");
                    return r;
                }).header("X-Internal-Token", TestTenants.INTERNAL_TOKEN)
                .contentType(MediaType.TEXT_PLAIN).content(report))
                .andExpect(status().isNoContent());
        assertThat(platformJdbc.sql("SELECT tls_status FROM tenant_domains WHERE hostname = :host")
                .param("host", TestTenants.BETA_HOST).query(String.class).single()).isEqualTo("ISSUED");

        mvc.perform(post("/internal/tls/status").header("X-Internal-Token", TestTenants.INTERNAL_TOKEN)
                        .contentType(MediaType.TEXT_PLAIN).content(TestTenants.BETA_HOST + " PENDING\n"))
                .andExpect(status().isNoContent());
        assertThat(platformJdbc.sql("SELECT tls_status FROM tenant_domains WHERE hostname = :host")
                .param("host", TestTenants.BETA_HOST).query(String.class).single()).isEqualTo("ISSUED");
    }
}
