package az.tapstamp.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.customer.auth.CustomerCookies;
import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.CustomerIntegrationTest;
import az.tapstamp.support.TestSmsInbox;
import az.tapstamp.support.TestTenants;
import az.tapstamp.tenancy.TenantContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Müştəri sessiyaları: cookie atributları, refresh rotasiyası, iki tabın yarışı, oğurlanmış tokenin təkrar istifadəsi,
 * cihazlar, CSRF, şirkətlər arası izolyasiya, razılıqların yeni versiyası, hesabın silinməsi.
 */
class CustomerSessionIT extends CustomerIntegrationTest {

    private static final String AT = CustomerCookies.ACCESS;
    private static final String RT = CustomerCookies.REFRESH;

    private void sql(String sql, Map<String, Object> params) {
        TenantContext.runAs(directory.findByHost(HOST).orElseThrow(), () -> {
            var spec = tenantJdbc.sql(sql);
            for (var entry : params.entrySet()) {
                spec = spec.param(entry.getKey(), entry.getValue());
            }
            spec.update();
        });
    }

    private <T> T query(String sql, Class<T> type, Map<String, Object> params) {
        return TenantContext.callAs(directory.findByHost(HOST).orElseThrow(), () -> {
            var spec = tenantJdbc.sql(sql);
            for (var entry : params.entrySet()) {
                spec = spec.param(entry.getKey(), entry.getValue());
            }
            return spec.query(type).single();
        });
    }

    @Test
    void sessionCookiesAreHttpOnlySecureAndScoped() throws Exception {
        String phone = uniquePhone();
        CustomerClient client = client();
        String[] challenge = requestCode(client, phone, "az");
        ResultActions verify = client.post("/api/v1/auth/otp/verify", Map.of("challengeId", challenge[0], "code", challenge[1]));
        String token = json(verify, "$.registrationToken");
        List<String> headers = client.post("/api/v1/auth/register", Map.of("registrationToken", token,
                        "acceptedDocuments", currentDocuments(client), "language", "az"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);

        String access = headers.stream().filter(h -> h.startsWith(AT + "=")).findFirst().orElseThrow();
        String refresh = headers.stream().filter(h -> h.startsWith(RT + "=")).findFirst().orElseThrow();
        assertThat(access).contains("Path=/;", "Max-Age=900", "Secure", "HttpOnly", "SameSite=Lax");
        assertThat(refresh).contains("Path=/api/v1/auth", "Max-Age=" + Duration.ofDays(180).toSeconds(), "Secure",
                "HttpOnly", "SameSite=Lax");
        // Tokenlər bazada yalnız hash şəklindədir
        String accessToken = client.cookie(AT);
        assertThat(query("SELECT count(*) FROM customer_sessions WHERE access_token_hash = convert_to(:t, 'UTF8')",
                Integer.class, Map.of("t", accessToken))).isZero();
        client.get("/api/v1/auth/session").andExpect(jsonPath("$.state").value("ACTIVE"));
        client().get("/api/v1/auth/session").andExpect(jsonPath("$.state").value("NONE"));
    }

    @Test
    void refreshRotatesBothTokensAndToleratesTheTwoTabRace() throws Exception {
        CustomerClient tab1 = signedIn(uniquePhone());
        String oldAccess = tab1.cookie(AT);
        String oldRefresh = tab1.cookie(RT);

        tab1.post("/api/v1/auth/refresh").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        assertThat(tab1.cookie(AT)).isNotEqualTo(oldAccess);
        assertThat(tab1.cookie(RT)).isNotEqualTo(oldRefresh);
        tab1.get("/api/v1/me").andExpect(status().isOk());

        // İkinci tab eyni anda köhnə refresh token ilə gəlir: sessiya ləğv olunmur
        CustomerClient tab2 = client();
        tab2.setCookie(RT, oldRefresh, "/api/v1/auth");
        tab2.post("/api/v1/auth/refresh").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFRESH_RACE"));
        tab1.get("/api/v1/me").andExpect(status().isOk());

        // Köhnə access token artıq etibarsızdır
        CustomerClient stale = client();
        stale.setCookie(AT, oldAccess, "/");
        stale.get("/api/v1/me").andExpect(status().isUnauthorized());
        stale.get("/api/v1/auth/session").andExpect(jsonPath("$.state").value("NONE"));
    }

    @Test
    void reuseOfAnOldRefreshTokenRevokesTheWholeSession() throws Exception {
        CustomerClient victim = signedIn(uniquePhone());
        String stolenRefresh = victim.cookie(RT);
        victim.post("/api/v1/auth/refresh").andExpect(status().isOk());

        clock.advance(Duration.ofSeconds(11));
        CustomerClient attacker = client();
        attacker.setCookie(RT, stolenRefresh, "/api/v1/auth");
        attacker.post("/api/v1/auth/refresh").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));

        // Qurbanın yeni tokenləri də ləğv olunub — hücumçunun ələ keçirdiyi sessiya tam bağlanır
        victim.get("/api/v1/me").andExpect(status().isUnauthorized());
        victim.post("/api/v1/auth/refresh").andExpect(status().isUnauthorized());
        assertThat(query("SELECT count(*) FROM audit_logs WHERE action = 'CUSTOMER_REFRESH_REUSE_DETECTED'",
                Integer.class, Map.of())).isPositive();
    }

    @Test
    void expiredAccessTokenIsRenewedWithTheRefreshToken() throws Exception {
        CustomerClient client = signedIn(uniquePhone());
        clock.advance(Duration.ofMinutes(16));
        client.get("/api/v1/me").andExpect(status().isUnauthorized());
        client.get("/api/v1/auth/session").andExpect(jsonPath("$.state").value("REFRESH_REQUIRED"));
        client.post("/api/v1/auth/refresh").andExpect(status().isOk());
        client.get("/api/v1/me").andExpect(status().isOk());
    }

    @Test
    void logoutRevokesTheSessionAndClearsCookies() throws Exception {
        CustomerClient client = signedIn(uniquePhone());
        String refresh = client.cookie(RT);
        client.post("/api/v1/auth/logout").andExpect(status().isNoContent());
        assertThat(client.cookie(AT)).isNull();
        assertThat(client.cookie(RT)).isNull();

        CustomerClient replay = client();
        replay.setCookie(RT, refresh, "/api/v1/auth");
        replay.post("/api/v1/auth/refresh").andExpect(status().isUnauthorized());
        client().post("/api/v1/auth/logout").andExpect(status().isNoContent());
    }

    @Test
    void stateChangingRequestsRequireCsrf() throws Exception {
        CustomerClient client = signedIn(uniquePhone());
        client.withoutCsrf().post("/api/v1/me/sessions/revoke-others").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        client.withoutCsrf().post("/api/v1/auth/refresh").andExpect(status().isForbidden());
    }

    @Test
    void customerSeesAndRevokesOwnDevicesOnly() throws Exception {
        String phone = uniquePhone();
        CustomerClient phoneApp = signedIn(phone);
        CustomerClient laptop = signedIn(phone);
        CustomerClient tablet = signedIn(phone);

        String body = laptop.get("/api/v1/me/sessions").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> devices = JsonPath.read(body, "$");
        assertThat(devices).filteredOn(d -> Boolean.TRUE.equals(d.get("current"))).hasSize(1);
        String phoneSession = query("""
                        SELECT id::text FROM customer_sessions WHERE access_token_hash = sha256(convert_to(:t, 'UTF8'))""",
                String.class, Map.of("t", phoneApp.cookie(AT)));

        // Başqa müştəri bu sessiyanı ləğv edə bilməz (mövcudluğu da açıqlanmır)
        signedIn(uniquePhone()).delete("/api/v1/me/sessions/" + phoneSession).andExpect(status().isNotFound());
        laptop.delete("/api/v1/me/sessions/" + phoneSession).andExpect(status().isNoContent());
        phoneApp.get("/api/v1/me").andExpect(status().isUnauthorized());

        laptop.post("/api/v1/me/sessions/revoke-others").andExpect(jsonPath("$.revoked").value(1));
        tablet.get("/api/v1/me").andExpect(status().isUnauthorized());
        laptop.get("/api/v1/me").andExpect(status().isOk());
        laptop.get("/api/v1/me/sessions").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void cookiesFromOneCompanyAreUselessInAnother() throws Exception {
        CustomerClient alpha = signedIn(uniquePhone());
        CustomerClient onBeta = alpha.onHost(TestTenants.BETA_HOST);
        onBeta.get("/api/v1/me").andExpect(status().isUnauthorized());
        onBeta.post("/api/v1/auth/refresh").andExpect(status().isUnauthorized());
        alpha.get("/api/v1/me").andExpect(status().isOk());
    }

    @Test
    void staffAndCustomerSessionsDoNotMix() throws Exception {
        CustomerClient customer = signedIn(uniquePhone());
        // Müştəri tokeni işçi cookie-si kimi
        CustomerClient forged = client();
        forged.setCookie(AdminRealm.STAFF.cookieName(), customer.cookie(AT), "/");
        forged.get("/api/v1/admin/me").andExpect(status().isUnauthorized());
        // Müştəri cookie-si admin endpoint-ində heç nə açmır
        customer.get("/api/v1/admin/me").andExpect(status().isUnauthorized());
    }

    @Test
    void languageChangeAppliesToErrorsAndSms() throws Exception {
        String phone = uniquePhone();
        CustomerClient client = signedIn(phone);
        client.patch("/api/v1/me", Map.of("preferredLanguage", "ru")).andExpect(jsonPath("$.preferredLanguage").value("ru"));
        client.delete("/api/v1/me").andExpect(jsonPath("$.title").value("Требуется подтверждение"));
        client.post("/api/v1/auth/step-up/otp/request").andExpect(status().isAccepted());
        // SMS mətni dildən asılı deyil: "<şirkət> OTP: <kod>"
        assertThat(sms.await(phone, 2).text()).matches(".+ OTP: \\d{6}");
        client.patch("/api/v1/me", Map.of("name", "")).andExpect(jsonPath("$.name").isEmpty());
    }

    @Test
    void newDocumentVersionRequiresConsentAgain() throws Exception {
        CustomerClient client = signedIn(uniquePhone());
        client.get("/api/v1/me").andExpect(jsonPath("$.pendingConsents.length()").value(0));

        sql("UPDATE legal_documents SET is_current = false WHERE type = 'TERMS' AND is_current", Map.of());
        int version = query("SELECT max(doc_version) + 1 FROM legal_documents WHERE type = 'TERMS'", Integer.class, Map.of());
        sql("""
                INSERT INTO legal_documents (id, type, doc_version, content, published_at, is_current)
                VALUES (:id, 'TERMS', :v, CAST('{"az": "yeni şərtlər"}' AS jsonb), now(), true)""",
                Map.of("id", Ids.newId(), "v", version));
        client.get("/api/v1/me")
                .andExpect(jsonPath("$.pendingConsents.length()").value(1))
                .andExpect(jsonPath("$.pendingConsents[0].type").value("TERMS"))
                .andExpect(jsonPath("$.pendingConsents[0].version").value(version));
        client.post("/api/v1/auth/consents",
                        Map.of("acceptedDocuments", List.of(Map.of("type", "TERMS", "version", version - 1))))
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        client.post("/api/v1/auth/consents",
                        Map.of("acceptedDocuments", List.of(Map.of("type", "TERMS", "version", version))))
                .andExpect(status().isNoContent());
        client.get("/api/v1/me").andExpect(jsonPath("$.pendingConsents.length()").value(0));
    }

    @Test
    void accountDeletionRequiresSmsStepUpAndAnonymizes() throws Exception {
        String phone = uniquePhone();
        CustomerClient client = signedIn(phone);
        String customerId = json(client.get("/api/v1/me"), "$.id");

        client.delete("/api/v1/me").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        String challengeId = json(client.post("/api/v1/auth/step-up/otp/request").andExpect(status().isAccepted()),
                "$.challengeId");
        String code = TestSmsInbox.code(sms.await(phone, 2));
        // Giriş kodu step-up üçün keçərli deyil və əksinə
        client.post("/api/v1/auth/otp/verify", Map.of("challengeId", challengeId, "code", code))
                .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
        client.post("/api/v1/auth/step-up/otp/verify", Map.of("challengeId", challengeId, "code", code))
                .andExpect(status().isNoContent());
        client.delete("/api/v1/me").andExpect(status().isNoContent());
        assertThat(client.cookie(AT)).isNull();

        client.get("/api/v1/me").andExpect(status().isUnauthorized());
        Map<String, Object> params = Map.of("id", UUID.fromString(customerId));
        assertThat(query("""
                        SELECT count(*) FROM customers WHERE id = :id AND status = 'DELETED' AND phone_enc IS NULL
                          AND phone_hash IS NULL AND phone_masked IS NULL AND name IS NULL AND deleted_at IS NOT NULL""",
                Integer.class, params)).isOne();
        assertThat(query("SELECT count(*) FROM customer_sessions WHERE customer_id = :id AND revoked_at IS NULL",
                Integer.class, params)).isZero();
        assertThat(query("SELECT count(*) FROM consents WHERE customer_id = :id AND withdrawn_at IS NULL",
                Integer.class, params)).isZero();
        assertThat(query("SELECT count(*) FROM otp_challenges WHERE customer_id = :id", Integer.class, params)).isZero();
        assertThat(query("SELECT count(*) FROM loyalty_accounts WHERE customer_id = :id", Integer.class, params)).isOne();
        assertThat(query("SELECT count(*) FROM audit_logs WHERE action = 'CUSTOMER_DELETED' AND target_id = :id",
                Integer.class, Map.of("id", customerId))).isOne();

        // Eyni nömrə yeni, boş hesab ala bilər
        CustomerClient fresh = client();
        String[] challenge = requestCode(fresh, phone, "az");
        fresh.post("/api/v1/auth/otp/verify", Map.of("challengeId", challenge[0], "code", challenge[1]))
                .andExpect(jsonPath("$.status").value("REGISTRATION_REQUIRED"));
    }
}
