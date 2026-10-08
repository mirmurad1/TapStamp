package az.tapstamp.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.TapIntegrationTest;
import az.tapstamp.tenancy.TenantContext;
import org.junit.jupiter.api.Test;

/** Admin paneli: müştərilərin siyahısı, detallar, əl ilə düzəliş, tam nömrə, bloklama, fırıldaq işarələri, CSV. */
class CustomerAdminIT extends TapIntegrationTest {

    private record Fixture(UUID branchId, String phone, CustomerClient customer, UUID customerId) {
    }

    /** Yeni filialda bir möhürü olan yeni müştəri. */
    private Fixture customerWithStamp() throws Exception {
        UUID branchId = newBranch(Policy.relaxed());
        String phone = uniquePhone();
        CustomerClient customer = signedIn(phone);
        TestTag tag = newTag(branchId);
        customer.post(claimPath(tapToken(customer, tag, 1))).andExpect(status().isOk());
        UUID customerId = UUID.fromString(json(customer.get("/api/v1/me"), "$.id"));
        return new Fixture(branchId, phone, customer, customerId);
    }

    /** +994551234567 → 0551234567 (URL-də kodlaşdırma tələb etmir). */
    private static String localDigits(String e164) {
        return localFormat(e164).replace(" ", "");
    }

    @Test
    void listSearchesByPhoneAndNameAndRespectsBranchScope() throws Exception {
        Fixture f = customerWithStamp();
        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);

        owner.get("/customers?q=" + localDigits(f.phone()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(f.customerId().toString()))
                .andExpect(jsonPath("$.items[0].phoneMasked").value(org.hamcrest.Matchers.containsString("***")))
                .andExpect(jsonPath("$.items[0].currentStamps").value(1));
        // Ölkə kodu və ya əvvəldəki 0 olmadan da tapılır; mövcud olmayan nömrə — boş nəticə
        owner.get("/customers?q=" + localDigits(f.phone()).substring(1)).andExpect(jsonPath("$.total").value(1));
        owner.get("/customers?q=0001234567").andExpect(jsonPath("$.total").value(0));
        owner.get("/customers?branchId=" + f.branchId()).andExpect(jsonPath("$.total").value(1));
        owner.get("/customers?q=test&branchId=" + f.branchId() + "&sort=NAME&direction=asc")
                .andExpect(jsonPath("$.total").value(1));
        owner.get("/customers?size=101").andExpect(status().isBadRequest());
        owner.get("/customers?sort=PHONE").andExpect(status().isBadRequest());

        owner.get("/customers/" + f.customerId())
                .andExpect(jsonPath("$.name").value("Test Müştəri"))
                .andExpect(jsonPath("$.totalStamps").value(1));
        owner.get("/customers/" + f.customerId() + "/timeline")
                .andExpect(jsonPath("$[0].kind").value("STAMP"))
                .andExpect(jsonPath("$[0].source").value("NFC_TAP"));

        // Menecer: öz filialının müştərisini görür, başqa filialın müştərisi onun üçün yoxdur
        AdminClient ownManager = signedInStaff(HOST, AdminRole.BRANCH_MANAGER, f.branchId());
        ownManager.get("/customers/" + f.customerId()).andExpect(status().isOk());
        AdminClient otherManager = signedInStaff(HOST, AdminRole.BRANCH_MANAGER, newBranch(Policy.relaxed()));
        otherManager.get("/customers/" + f.customerId()).andExpect(status().isNotFound());
        otherManager.get("/customers?q=" + localDigits(f.phone())).andExpect(jsonPath("$.total").value(0));
        otherManager.get("/customers?branchId=" + f.branchId()).andExpect(status().isNotFound());
        signedInBarista(f.branchId()).get("/customers").andExpect(status().isForbidden());
    }

    @Test
    void manualAdjustmentsAreIdempotentAndRevokeUnusedGiftCards() throws Exception {
        Fixture f = customerWithStamp();
        AdminClient manager = signedInStaff(HOST, AdminRole.BRANCH_MANAGER, f.branchId());
        String path = "/customers/" + f.customerId() + "/stamp-adjustments";
        Map<String, Object> plusThree = Map.of("delta", 3, "reason", "Kassa sistemi işləmirdi", "branchId", f.branchId());

        manager.post(path, plusThree).andExpect(status().isBadRequest());
        manager.post(path, plusThree, "Idempotency-Key", "adjust-0001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStamps").value(4))
                .andExpect(jsonPath("$.totalStamps").value(4));
        // Təkrar göndəriş — eyni nəticə, ikinci dəfə əlavə olunmur
        manager.post(path, plusThree, "Idempotency-Key", "adjust-0001").andExpect(jsonPath("$.currentStamps").value(4));
        manager.post(path, Map.of("delta", 2, "reason", "Kassa sistemi işləmirdi", "branchId", f.branchId()),
                        "Idempotency-Key", "adjust-0001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        // Hədd (9) dolur — hədiyyə kartı verilir
        manager.post(path, Map.of("delta", 5, "reason", "Ad günü hədiyyəsi", "branchId", f.branchId()),
                        "Idempotency-Key", "adjust-0002")
                .andExpect(jsonPath("$.giftCardsIssued").value(1))
                .andExpect(jsonPath("$.currentStamps").value(0));
        f.customer().get("/api/v1/me/gift-cards").andExpect(jsonPath("$[0].status").value("AVAILABLE"));

        // Kartdakı saydan çox silmə: istifadə olunmamış hədiyyə kartı ləğv edilir, kart geri qayıdır
        manager.post(path, Map.of("delta", -2, "reason", "Səhv vurulmuş möhürlər"), "Idempotency-Key", "adjust-0003")
                .andExpect(jsonPath("$.giftCardsRevoked").value(1))
                .andExpect(jsonPath("$.currentStamps").value(7))
                .andExpect(jsonPath("$.totalStamps").value(7));
        f.customer().get("/api/v1/me/gift-cards").andExpect(jsonPath("$[0].status").value("REVOKED"));
        manager.post(path, Map.of("delta", -50, "reason", "Səhv vurulmuş möhürlər"), "Idempotency-Key", "adjust-0004")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAMP_ADJUSTMENT_TOO_LARGE"));
        manager.post(path, Map.of("delta", 1, "reason", "abc", "branchId", f.branchId()), "Idempotency-Key", "adjust-0005")
                .andExpect(status().isBadRequest());

        manager.get("/customers/" + f.customerId() + "/timeline")
                .andExpect(jsonPath("$[?(@.kind == 'STAMP' && @.status == 'REVOKED')].reason").value(
                        org.hamcrest.Matchers.hasItem("Səhv vurulmuş möhürlər")))
                .andExpect(jsonPath("$[?(@.kind == 'REWARD')].status").value(org.hamcrest.Matchers.hasItem("REVOKED")));
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'STAMPS_ADJUSTED' AND target_id = :id",
                Map.of("id", f.customerId().toString()))).isEqualTo(3);
    }

    @Test
    void fullPhoneNeedsOwnerAndStepUp() throws Exception {
        Fixture f = customerWithStamp();
        String path = "/customers/" + f.customerId() + "/phone-reveal";
        signedInStaff(HOST, AdminRole.BRANCH_MANAGER, f.branchId()).post(path).andExpect(status().isForbidden());

        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        owner.post(path).andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        owner.post("/auth/step-up", Map.of("password", STAFF_PASSWORD)).andExpect(status().isNoContent());
        owner.post(path).andExpect(status().isOk()).andExpect(jsonPath("$.phone").value(f.phone()));
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'CUSTOMER_PHONE_REVEALED' AND target_id = :id",
                Map.of("id", f.customerId().toString()))).isEqualTo(1);
    }

    @Test
    void ownerBlocksCustomerAndSessionsEnd() throws Exception {
        Fixture f = customerWithStamp();
        String path = "/customers/" + f.customerId() + "/status";
        signedInStaff(HOST, AdminRole.BRANCH_MANAGER, f.branchId()).patch(path, Map.of("status", "BLOCKED"))
                .andExpect(status().isForbidden());
        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        owner.patch(path, Map.of("status", "BLOCKED")).andExpect(status().isNoContent());
        f.customer().get("/api/v1/me").andExpect(status().isUnauthorized());
        owner.get("/customers/" + f.customerId()).andExpect(jsonPath("$.status").value("BLOCKED"));
        owner.get("/customers?status=BLOCKED&branchId=" + f.branchId()).andExpect(jsonPath("$.total").value(1));
        owner.patch(path, Map.of("status", "ACTIVE")).andExpect(status().isNoContent());
        owner.patch(path, Map.of("status", "DELETED")).andExpect(status().isBadRequest());
    }

    @Test
    void fraudFlagsAreListedAndResolvedWithNote() throws Exception {
        Fixture f = customerWithStamp();
        UUID flagId = Ids.newId();
        TenantContext.runAs(alpha(), () -> tenantJdbc.sql("""
                        INSERT INTO fraud_flags (id, customer_id, type, severity, details, created_at)
                        VALUES (:id, :customer, 'RAPID_MULTI_ACCOUNT', 'MEDIUM', '{"accounts": 3}', now())""")
                .param("id", flagId).param("customer", f.customerId()).update());
        AdminClient manager = signedInStaff(HOST, AdminRole.BRANCH_MANAGER, f.branchId());

        manager.get("/customers/" + f.customerId()).andExpect(jsonPath("$.openFraudFlags").value(1));
        manager.get("/customers/" + f.customerId() + "/fraud-flags")
                .andExpect(jsonPath("$[0].type").value("RAPID_MULTI_ACCOUNT"))
                .andExpect(jsonPath("$[0].resolvedAt").doesNotExist());
        signedInStaff(HOST, AdminRole.BRANCH_MANAGER, newBranch(Policy.relaxed()))
                .post("/customers/fraud-flags/" + flagId + "/resolve", Map.of("note", "Ailə üzvləri"))
                .andExpect(status().isNotFound());
        manager.post("/customers/fraud-flags/" + flagId + "/resolve", Map.of("note", "Ailə üzvləri, eyni telefon"))
                .andExpect(status().isNoContent());
        manager.get("/customers/" + f.customerId() + "/fraud-flags")
                .andExpect(jsonPath("$[0].resolvedAt").exists())
                .andExpect(jsonPath("$[0].resolutionNote").value("Ailə üzvləri, eyni telefon"));
        manager.get("/customers/" + f.customerId()).andExpect(jsonPath("$.openFraudFlags").value(0));
    }

    @Test
    void csvExportIsLocalizedAndSafeAgainstFormulaInjection() throws Exception {
        Fixture f = customerWithStamp();
        TenantContext.runAs(alpha(), () -> tenantJdbc.sql("UPDATE customers SET name = :name WHERE id = :id")
                .param("name", "=HYPERLINK(\"http://evil\",\"x\")").param("id", f.customerId()).update());
        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        String path = "/customers/export?branchId=" + f.branchId();
        owner.post(path).andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        signedInStaff(HOST, AdminRole.BRANCH_MANAGER, f.branchId()).post(path).andExpect(status().isForbidden());

        owner.post("/auth/step-up", Map.of("password", STAFF_PASSWORD)).andExpect(status().isNoContent());
        String csv = owner.post(path)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("customers.csv")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).startsWith("﻿ID,Ad,Telefon (maskalanmış),Dil,Status,Qeydiyyat");
        assertThat(lines[1]).contains(f.customerId().toString())
                .contains("\"'=HYPERLINK(\"\"http://evil\"\",\"\"x\"\")\"")
                .contains(",Aktiv,")
                .doesNotContain(f.phone());
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'CUSTOMERS_EXPORTED' AND details ->> 'rows' = '1'",
                Map.of())).isGreaterThanOrEqualTo(1);
    }
}
