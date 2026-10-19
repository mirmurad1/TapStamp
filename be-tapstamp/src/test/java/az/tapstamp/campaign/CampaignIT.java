package az.tapstamp.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;

import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.TapIntegrationTest;
import az.tapstamp.tenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Kampaniya SMS-ləri: önizləmə (latın variantı ilə), yaradılma, imtina edən müştəri, platforma təsdiqi, göndəriş
 * (09:00–21:00) və SMS jurnalı.
 */
class CampaignIT extends TapIntegrationTest {

    @Autowired
    private CampaignSender sender;

    @Autowired
    @Qualifier("platformJdbc")
    private JdbcClient platformJdbc;

    @Test
    void ownerCreatesPlatformApprovesAndSmsAreSentToConsentingCustomers() throws Exception {
        String receiver = uniquePhone();
        signedIn(receiver);
        String optedOut = uniquePhone();
        CustomerClient refuser = signedIn(optedOut);
        refuser.patch("/api/v1/me/marketing", Map.of("enabled", false)).andExpect(status().isOk());
        refuser.get("/api/v1/me/marketing").andExpect(jsonPath("$.enabled").value(false));

        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        // Azərbaycan hərfləri: UCS-2 (70 simvol/SMS); latın variantı ucuzdur
        String text = "Əziz müştəri, bu həftə ikinci qəhvə hədiyyədir! Şənbə və bazar günləri bütün filiallarda keçərlidir.";
        owner.post("/campaigns/preview", Map.of("text", text, "audience", "ALL")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fullText").value("Alpha Kafe: " + text))
                .andExpect(jsonPath("$.encoding").value("UCS_2"))
                .andExpect(jsonPath("$.segments").value(2))
                .andExpect(jsonPath("$.latinText").value(org.hamcrest.Matchers.startsWith("Alpha Kafe: Eziz musteri")))
                .andExpect(jsonPath("$.latinSegments").value(1))
                .andExpect(jsonPath("$.latinCheaper").value(true))
                .andExpect(jsonPath("$.recipients").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        owner.post("/campaigns/preview", Map.of("text", "x", "audience", "INACTIVE")).andExpect(status().isBadRequest());

        String latin = "Bu hefte ikinci qehve hediyyedir!";
        String id = json(owner.post("/campaigns", Map.of("text", latin, "audience", "ALL"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.segmentsPerMessage").value(1)), "$.id");
        int recipients = count("SELECT count(*) FROM campaign_recipients WHERE campaign_id = CAST(:id AS uuid)",
                Map.of("id", id));
        assertThat(recipients).isPositive();
        assertThat(count("""
                SELECT count(*) FROM campaign_recipients r JOIN customers c ON c.id = r.customer_id
                WHERE r.campaign_id = CAST(:id AS uuid) AND c.marketing_opt_out_at IS NOT NULL""", Map.of("id", id)))
                .isZero();

        // Təsdiqdən əvvəl heç nə getmir
        sendAll();
        assertThat(count("SELECT count(*) FROM campaign_recipients WHERE campaign_id = CAST(:id AS uuid) AND status = 'SENT'",
                Map.of("id", id))).isZero();

        AdminClient platform = signedInPlatformAdmin();
        platform.get("/campaigns").andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].totalSms").value(org.hamcrest.Matchers.contains(recipients)));
        platform.post("/campaigns/" + alpha().id() + "/" + id + "/approve").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        platform.post("/campaigns/" + alpha().id() + "/" + id + "/approve").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_STATE_CHANGED"));
        owner.post("/campaigns/" + id + "/cancel").andExpect(status().isConflict());

        sendAll();
        owner.get("/campaigns/" + id).andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sentCount").value(recipients));
        assertThat(sms.to(receiver)).anySatisfy(m -> assertThat(m.text()).isEqualTo("Alpha Kafe: " + latin));
        assertThat(sms.to(optedOut)).noneSatisfy(m -> assertThat(m.text()).contains(latin));
        assertThat(platformJdbc.sql("SELECT count(*) FROM sms_messages WHERE phone = :p AND purpose = 'CAMPAIGN'")
                .param("p", receiver).query(Integer.class).single()).isOne();
    }

    @Test
    void rejectedCampaignIsNeverSent() throws Exception {
        signedIn(uniquePhone());
        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        String id = json(owner.post("/campaigns", Map.of("text", "Rədd testi", "audience", "ALL"))
                .andExpect(status().isCreated()), "$.id");
        AdminClient platform = signedInPlatformAdmin();
        platform.post("/campaigns/" + alpha().id() + "/" + id + "/reject", Map.of("reason", "Ödəniş alınmayıb"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        sendAll();
        owner.get("/campaigns/" + id).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("Ödəniş alınmayıb"));
    }

    /** Saatı şirkətin 09:00–21:00 pəncərəsinə gətirib göndərişi bitənə qədər işlədir. */
    private void sendAll() {
        LocalTime local = clock.instant().atZone(alpha().timezone()).toLocalTime();
        if (local.isBefore(LocalTime.of(10, 0)) || !local.isBefore(LocalTime.of(20, 0))) {
            long minutes = Duration.between(local, LocalTime.of(12, 0)).toMinutes();
            clock.advance(Duration.ofMinutes(minutes < 0 ? minutes + 24 * 60 : minutes));
        }
        for (int i = 0; i < 50; i++) {
            TenantContext.runAs(alpha(), sender::sendForCurrentTenant);
        }
    }
}
