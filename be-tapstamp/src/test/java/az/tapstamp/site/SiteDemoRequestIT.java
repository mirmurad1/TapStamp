package az.tapstamp.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import az.tapstamp.notification.mail.MailMessage;
import az.tapstamp.support.IntegrationTest;
import az.tapstamp.support.TestMailbox;
import az.tapstamp.support.TestTenants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Saytın demo müraciəti: yalnız sayt domenində, poçt qutusuna email kimi; bot tələsi və IP limiti. */
class SiteDemoRequestIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestMailbox mailbox;

    @Test
    void validRequestIsEmailedToInbox() throws Exception {
        int before = mailbox.to(TestTenants.SITE_INBOX).size();
        String company = "Qəhvə Evi " + UUID.randomUUID();

        mvc.perform(demo(TestTenants.SITE_HOST, uniqueIp(), body(company + "\r\nBcc: x@evil.test", "")))
                .andExpect(status().isNoContent());

        MailMessage mail = mailbox.awaitMessage(TestTenants.SITE_INBOX, before + 1);
        assertThat(mail.subject()).contains(company).doesNotContain("\r").doesNotContain("\n");
        assertThat(mail.textBody()).contains("+994 50 123 45 67", "Kafe", "2-5", "EN", "İki filial");
    }

    @Test
    void wwwHostWorksToo() throws Exception {
        mvc.perform(demo("www." + TestTenants.SITE_HOST, uniqueIp(), body("Www " + UUID.randomUUID(), "")))
                .andExpect(status().isNoContent());
    }

    @Test
    void honeypotIsSilentlyDropped() throws Exception {
        String company = "Bot " + UUID.randomUUID();
        mvc.perform(demo(TestTenants.SITE_HOST, uniqueIp(), body(company, "https://spam.example")))
                .andExpect(status().isNoContent());
        assertThat(mailbox.to(TestTenants.SITE_INBOX)).noneMatch(m -> m.subject().contains(company));
    }

    @Test
    void invalidFieldsAreRejected() throws Exception {
        String invalid = """
                {"name":"A","phone":"abc","company":"C","businessType":"bank","branches":"1"}""";
        mvc.perform(demo(TestTenants.SITE_HOST, uniqueIp(), invalid)).andExpect(status().isBadRequest());
    }

    @Test
    void endpointExistsOnlyOnSiteDomain() throws Exception {
        mvc.perform(demo(TestTenants.ALPHA_HOST, uniqueIp(), body("Alpha", ""))).andExpect(status().isNotFound());
        mvc.perform(demo(TestTenants.PLATFORM_HOST, uniqueIp(), body("Platform", ""))).andExpect(status().isNotFound());
        // Sayt domenində şirkət və platforma API-ləri yoxdur
        mvc.perform(get("/api/v1/public/company").with(host(TestTenants.SITE_HOST))).andExpect(status().isNotFound());
    }

    @Test
    void crossOriginRequestIsRejected() throws Exception {
        mvc.perform(demo(TestTenants.SITE_HOST, uniqueIp(), body("Cross", "")).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tooManyRequestsFromOneIpAreLimited() throws Exception {
        String ip = uniqueIp();
        for (int i = 0; i < 5; i++) {
            mvc.perform(demo(TestTenants.SITE_HOST, ip, body("Limit " + i, ""))).andExpect(status().isNoContent());
        }
        mvc.perform(demo(TestTenants.SITE_HOST, ip, body("Limit 5", ""))).andExpect(status().isTooManyRequests());
    }

    private MockHttpServletRequestBuilder demo(String hostname, String ip, String json) {
        return post("/api/v1/site/demo-requests")
                .with(host(hostname))
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor host(String hostname) {
        return r -> {
            r.setServerName(hostname);
            return r;
        };
    }

    private static String body(String company, String honeypot) {
        return """
                {"name":"Aysel","phone":"+994 50 123 45 67","company":%s,"businessType":"cafe","branches":"2-5",
                 "note":"İki filial","language":"en","website":%s}""".formatted(json(company), json(honeypot));
    }

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private static String uniqueIp() {
        int n = (int) (Math.random() * 250) + 1;
        int m = (int) (Math.random() * 250) + 1;
        return "10.77." + n + "." + m;
    }
}
