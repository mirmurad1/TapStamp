package az.cafeloyalty.company;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.cafeloyalty.security.admin.AdminRole;
import az.cafeloyalty.support.AdminClient;
import az.cafeloyalty.support.CustomerClient;
import az.cafeloyalty.support.TapIntegrationTest;
import az.cafeloyalty.support.TestTenants;
import az.cafeloyalty.tenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Şirkətin məzmunu (3 dil), filiallar, iş saatları və filial üzrə qaydanın override-ı. */
class CompanySettingsIT extends TapIntegrationTest {

    @Test
    void ownerEditsCompanyContentAndCustomersSeeItImmediately() throws Exception {
        // Beta şirkəti: digər testlərin alpha məlumatına təsir etməmək üçün; sonda bərpa olunur
        String host = TestTenants.BETA_HOST;
        AdminClient owner = signedInStaff(host, AdminRole.COMPANY_OWNER, null);
        ResultActions before = owner.get("/settings/company").andExpect(status().isOk());
        Map<String, Object> original = new HashMap<>();
        original.put("name", Map.of("az", json(before, "$.name.az")));
        original.put("timezone", json(before, "$.timezone"));
        original.put("defaultLanguage", json(before, "$.defaultLanguage"));
        original.put("brandColor", json(before, "$.brandColor"));
        try {
            owner.put("/settings/company", Map.of(
                            "name", Map.of("az", " Moyka Plus ", "en", "Moyka Plus", "ru", "Мойка Плюс"),
                            "description", Map.of("az", "Avtoyuma"),
                            "timezone", "Asia/Baku", "defaultLanguage", "ru", "brandColor", "#0ea5e9",
                            "smsSenderName", "MoykaPlus"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name.az").value("Moyka Plus"))
                    .andExpect(jsonPath("$.name.ru").value("Мойка Плюс"))
                    .andExpect(jsonPath("$.brandColor").value("#0EA5E9"));
            mvc.perform(get("/api/v1/public/company").with(r -> {
                        r.setServerName(host);
                        return r;
                    }))
                    .andExpect(jsonPath("$.name.en").value("Moyka Plus"))
                    .andExpect(jsonPath("$.brandColor").value("#0EA5E9"));

            owner.put("/settings/company", Map.of("name", Map.of("az", "X"), "timezone", "Mars/Olympus",
                    "defaultLanguage", "az", "brandColor", "#000000")).andExpect(status().isBadRequest());
            owner.put("/settings/company", Map.of("name", Map.of("az", "X"), "timezone", "Asia/Baku",
                    "defaultLanguage", "az", "brandColor", "red")).andExpect(status().isBadRequest());
            owner.put("/settings/company", Map.of("name", Map.of("az", "X"), "timezone", "Asia/Baku",
                    "defaultLanguage", "az", "brandColor", "#000000", "smsSenderName", "Ad-Soyad")).andExpect(status().isBadRequest());
        } finally {
            owner.put("/settings/company", original).andExpect(status().isOk());
        }
        signedInStaff(host, AdminRole.BRANCH_MANAGER, firstBranch(host)).get("/settings/company").andExpect(status().isForbidden());
    }

    private UUID firstBranch(String host) {
        return TenantContext.callAs(directory.findByHost(host).orElseThrow(), () -> tenantJdbc
                .sql("SELECT id FROM branches ORDER BY created_at LIMIT 1").query(UUID.class).single());
    }

    @Test
    void ownerManagesBranchesAndBusinessHours() throws Exception {
        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        Map<String, Object> hours = Map.of("mon", List.of(List.of("08:00", "22:00")), "sat", List.of(List.of("22:00", "02:00")));
        ResultActions created = owner.post("/settings/branches", Map.of(
                        "name", Map.of("az", "Nərimanov filialı", "en", "Narimanov branch"),
                        "address", Map.of("az", "Təbriz küç. 1"),
                        "businessHours", hours))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.businessHours.sat[0][1]").value("02:00"))
                .andExpect(jsonPath("$.policyOverridden").value(false));
        String branchId = json(created, "$.id");

        owner.post("/settings/branches", Map.of("name", Map.of("az", "Səhv"), "businessHours",
                Map.of("monday", List.of(List.of("08:00", "22:00"))))).andExpect(status().isBadRequest());
        owner.post("/settings/branches", Map.of("name", Map.of("az", "Səhv"), "businessHours",
                Map.of("mon", List.of(List.of("25:00", "22:00"))))).andExpect(status().isBadRequest());

        owner.patch("/settings/branches/" + branchId, Map.of("clearBusinessHours", true))
                .andExpect(jsonPath("$.businessHours").doesNotExist())
                .andExpect(jsonPath("$.name.en").value("Narimanov branch"));
        owner.patch("/settings/branches/" + branchId, Map.of("status", "CLOSED")).andExpect(jsonPath("$.status").value("CLOSED"));
        owner.get("/settings/branches").andExpect(jsonPath("$[?(@.id == '" + branchId + "')].status").value("CLOSED"));
        owner.patch("/settings/branches/" + UUID.randomUUID(), Map.of("status", "CLOSED")).andExpect(status().isNotFound());
    }

    @Test
    void branchPolicyOverrideAppliesToClaimsAndCanBeReset() throws Exception {
        UUID branchId = newBranch(Policy.relaxed());
        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        String path = "/settings/branches/" + branchId + "/stamp-policy";

        owner.get(path).andExpect(jsonPath("$.overridden").value(true)).andExpect(jsonPath("$.maxStampsPerDay").value(50));
        owner.delete(path).andExpect(jsonPath("$.overridden").value(false));
        owner.put(path, Map.of("minIntervalMinutes", 0, "maxStampsPerDay", 50, "requireStaffApproval", true,
                        "pendingTimeoutMinutes", 30, "enforceBusinessHours", false))
                .andExpect(jsonPath("$.overridden").value(true))
                .andExpect(jsonPath("$.requireStaffApproval").value(true));
        owner.put(path, Map.of("minIntervalMinutes", -1, "maxStampsPerDay", 50, "requireStaffApproval", true,
                "pendingTimeoutMinutes", 30, "enforceBusinessHours", false)).andExpect(status().isBadRequest());

        // Override dərhal qüvvədədir: bu filialda möhür işçi təsdiqini gözləyir
        TestTag tag = newTag(branchId);
        CustomerClient customer = signedIn(uniquePhone());
        customer.post(claimPath(tapToken(customer, tag, 1))).andExpect(jsonPath("$.status").value("PENDING"));
    }
}
