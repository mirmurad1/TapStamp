package az.tapstamp.dev.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.security.crypto.Aad;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.security.crypto.PhoneHasher;
import az.tapstamp.shared.pii.PhoneMasker;
import az.tapstamp.support.IntegrationTest;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.TenantStatus;
import az.tapstamp.tenancy.registry.TenantDirectory;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Dev seed: real provisioning ilə yaradılan şirkətlər və qaydalara uyğun, ardıcıl tarixçə. */
@ActiveProfiles("dev")
@TestPropertySource(properties = "app.dev.seed-enabled=true")
class DevSeedIT extends IntegrationTest {

    @Autowired
    DevDataSeeder seeder;

    @Autowired
    TenantDirectory directory;

    @Autowired
    @Qualifier("tenantJdbc")
    JdbcClient jdbc;

    @Autowired
    @Qualifier("tenantCipher")
    EnvelopeCipher cipher;

    @Autowired
    PhoneHasher phoneHasher;

    @Autowired
    MockMvc mvc;

    private TenantRecord mockCoffee() {
        return directory.findBySlug("mockcoffee").orElseThrow();
    }

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }

    @Test
    void mockCoffeeIsProvisionedAndSeeded() {
        TenantRecord tenant = mockCoffee();
        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(directory.hostnames(tenant.id())).containsExactly(SeedCatalog.MOCK_COFFEE_DOMAIN);
        TenantContext.runAs(tenant.toInfo(), () -> {
            assertThat(count("SELECT count(*) FROM customers")).isEqualTo(150);
            assertThat(count("SELECT count(*) FROM branches")).isEqualTo(2);
            assertThat(count("SELECT count(*) FROM nfc_tags")).isEqualTo(3);
            assertThat(count("SELECT count(*) FROM nfc_tag_keys WHERE status = 'ACTIVE'")).isEqualTo(1);
            assertThat(count("SELECT stamps_required FROM reward_rules WHERE active")).isEqualTo(9);
            assertThat(count("SELECT count(*) FROM legal_documents WHERE is_current")).isEqualTo(2);
            assertThat(count("SELECT count(*) FROM consents")).isEqualTo(300);
            assertThat(count("SELECT count(*) FROM stamps")).isGreaterThan(1000);
            assertThat(count("SELECT count(DISTINCT preferred_language) FROM customers")).isEqualTo(3);
        });
    }

    @Test
    void historyRespectsAntiAbuseRules() {
        TenantContext.runAs(mockCoffee().toInfo(), () -> {
            assertThat(count("""
                    SELECT count(*) FROM (
                        SELECT created_at - lag(created_at) OVER (PARTITION BY customer_id ORDER BY created_at) AS gap
                        FROM stamps) s
                    WHERE gap < interval '20 minutes'""")).isZero();
            assertThat(count("""
                    SELECT coalesce(max(per_day), 0) FROM (
                        SELECT count(*) AS per_day FROM stamps GROUP BY customer_id, local_date) d""")).isLessThanOrEqualTo(3);
            assertThat(count("""
                    SELECT count(*) FROM stamps
                    WHERE created_at < now() - interval '91 days' OR created_at > now()""")).isZero();
        });
    }

    @Test
    void loyaltyTotalsAreConsistent() {
        TenantContext.runAs(mockCoffee().toInfo(), () -> {
            assertThat(count("SELECT sum(total_stamps) FROM loyalty_accounts")).isEqualTo(count("SELECT count(*) FROM stamps"));
            assertThat(count("SELECT count(*) FROM loyalty_accounts WHERE current_stamps <> total_stamps % 9")).isZero();
            assertThat(count("SELECT count(*) FROM loyalty_accounts WHERE rewards_earned <> total_stamps / 9")).isZero();
            int redeemed = count("SELECT count(*) FROM rewards WHERE status = 'REDEEMED'");
            assertThat(redeemed).isPositive();
            assertThat(count("SELECT count(*) FROM redemptions WHERE status = 'COMPLETED'")).isEqualTo(redeemed);
            assertThat(count("SELECT sum(rewards_redeemed) FROM loyalty_accounts")).isEqualTo(redeemed);
            assertThat(count("SELECT count(*) FROM rewards WHERE status = 'AVAILABLE'")).isPositive();
        });
    }

    @Test
    void tagCountersAndFraudSignalsAreConsistent() {
        TenantContext.runAs(mockCoffee().toInfo(), () -> {
            assertThat(count("""
                    SELECT count(*) FROM nfc_tags t
                    WHERE t.last_counter <> (SELECT max(counter) FROM tap_events e
                                             WHERE e.tag_id = t.id AND e.stage = 'VERIFY' AND e.outcome = 'ACCEPTED')""")).isZero();
            assertThat(count("SELECT count(*) FROM tap_events WHERE reason = 'COUNTER_REPLAY'")).isPositive();
            assertThat(count("SELECT count(*) FROM tap_events WHERE stage = 'CLAIM' AND outcome = 'ACCEPTED'"))
                    .isEqualTo(count("SELECT count(*) FROM stamps"));
            assertThat(count("SELECT count(*) FROM fraud_flags WHERE type = 'REPLAY_ATTEMPT'")).isEqualTo(2);
        });
    }

    @Test
    void phonesAreEncryptedHashedAndMaskedConsistently() {
        TenantRecord tenant = mockCoffee();
        TenantContext.runAs(tenant.toInfo(), () -> {
            List<Map<String, Object>> rows = jdbc.sql(
                    "SELECT id, phone_enc, phone_hash, phone_masked FROM customers ORDER BY registered_at LIMIT 5")
                    .query().listOfRows();
            for (Map<String, Object> row : rows) {
                UUID id = (UUID) row.get("id");
                String phone = cipher.decryptString((byte[]) row.get("phone_enc"),
                        Aad.tenantField(tenant.id(), "customers", "phone_enc", id));
                assertThat(phone).matches("^\\+994\\d{9}$");
                assertThat(row.get("phone_masked")).isEqualTo(PhoneMasker.mask(phone));
                assertThat(Arrays.equals((byte[]) row.get("phone_hash"), phoneHasher.hash(tenant.id(), phone))).isTrue();
            }
        });
    }

    @Test
    void seedingAgainDoesNotDuplicateData() {
        seeder.seed(SeedCatalog.mockCoffee());
        TenantContext.runAs(mockCoffee().toInfo(),
                () -> assertThat(count("SELECT count(*) FROM customers")).isEqualTo(150));
    }

    @Test
    void publicEndpointServesSeededCompanyInEveryLanguage() throws Exception {
        mvc.perform(get("/api/v1/public/company").with(request -> {
                    request.setServerName(SeedCatalog.MOCK_COFFEE_DOMAIN);
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name.az").value("Mock Coffee House"))
                .andExpect(jsonPath("$.branches.length()").value(2))
                .andExpect(jsonPath("$.branches[0].name.ru").value("Центральный филиал"))
                .andExpect(jsonPath("$.rewardRule.stampsRequired").value(9))
                .andExpect(jsonPath("$.rewardRule.rewardName.az").value("Hədiyyə qəhvə"));
    }

    @Test
    void secondCompanyIsCompletelySeparate() {
        TenantRecord demo = directory.findBySlug("democay").orElseThrow();
        TenantContext.runAs(demo.toInfo(), () -> {
            assertThat(count("SELECT count(*) FROM customers")).isEqualTo(12);
            assertThat(count("SELECT stamps_required FROM reward_rules WHERE active")).isEqualTo(8);
        });
        assertThat(demo.dbName()).isNotEqualTo(mockCoffee().dbName());
    }
}
