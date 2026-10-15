package az.tapstamp.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import az.tapstamp.notification.sms.SmsLog;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.TapIntegrationTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;

/** SMS jurnalı (hər OTP yazılır, mətndə kod gizlədilir, kod ayrıca şifrəlidir) və platformanın SMS hesabatı. */
class PlatformSmsUsageIT extends TapIntegrationTest {

    @Autowired
    @Qualifier("platformJdbc")
    private JdbcClient platformJdbc;

    private int logged(String phone) {
        return platformJdbc.sql("SELECT count(*) FROM sms_messages WHERE phone = :p")
                .param("p", phone).query(Integer.class).single();
    }

    @Test
    void everyOtpIsLoggedWithoutTheCodeAndReportedPerCompany() throws Exception {
        String phone = uniquePhone();
        String[] challenge = requestCode(client(), phone, "az");
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> logged(phone) == 1);

        var row = platformJdbc.sql("""
                        SELECT text, status, segments, encoding, tenant_id,
                               coalesce(octet_length(code_enc), 0) AS code_bytes,
                               position(convert_to(:code, 'UTF8') in code_enc) AS plain_code
                        FROM sms_messages WHERE phone = :p""")
                .param("p", phone).param("code", challenge[1])
                .query((rs, n) -> new Object[] {rs.getString("text"), rs.getString("status"), rs.getInt("segments"),
                        rs.getString("encoding"), rs.getObject("tenant_id", UUID.class), rs.getInt("code_bytes"),
                        rs.getInt("plain_code")})
                .single();
        // Nömrə açıq, mətndə kod gizlədilib, kodun özü ayrıca şifrəli saxlanılır
        assertThat(row[0]).isEqualTo("Alpha Kafe OTP: ******").asString().doesNotContain(challenge[1]);
        assertThat(row[1]).isEqualTo("SENT");
        assertThat(row[2]).isEqualTo(1);
        assertThat(row[3]).isEqualTo("GSM_7");
        assertThat(row[4]).isEqualTo(alpha().id());
        assertThat((Integer) row[5]).isPositive();
        assertThat(row[6]).isEqualTo(0);

        LocalDate today = LocalDate.ofInstant(clock.instant(), SmsLog.REPORT_ZONE);
        AdminClient admin = signedInPlatformAdmin();
        String path = "/sms-usage?from=" + today.minusDays(6) + "&to=" + today;
        admin.get(path).andExpect(status().isOk())
                .andExpect(jsonPath("$.tenants[?(@.slug == '" + alpha().slug() + "')].messages").isNotEmpty())
                .andExpect(jsonPath("$.messages").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.segments").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // Keçmiş dövrdə heç nə yoxdur; səhv aralıq rədd olunur
        admin.get("/sms-usage?from=2020-01-01&to=2020-01-31").andExpect(status().isOk())
                .andExpect(jsonPath("$.tenants").isEmpty()).andExpect(jsonPath("$.messages").value(0));
        admin.get("/sms-usage?from=" + today + "&to=" + today.minusDays(1)).andExpect(status().isBadRequest());
        admin.get("/sms-usage?from=2020-01-01&to=2025-01-01").andExpect(status().isBadRequest());
    }
}
