package az.tapstamp.analytics;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.TapIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/** Filialların və işçilərin müqayisəsi: göstəricilər, işçi təsdiqi, menecerin çərçivəsi, yoxlamalar. */
class ComparisonIT extends TapIntegrationTest {

    @Test
    void branchesAndStaffAreCompared() throws Exception {
        UUID branchId = newBranch(new Policy(0, 50, true, 30, false, null));
        TestTag tag = newTag(branchId);
        CustomerClient customer = signedIn(uniquePhone());
        customer.post(claimPath(tapToken(customer, tag, 1))).andExpect(status().isOk());
        AdminClient barista = signedInBarista(branchId);
        String pending = barista.get("/staff-console/pending-stamps?branchId=" + branchId)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String stampId = JsonPath.read(pending, "$.items[0].id");
        barista.post("/staff-console/pending-stamps/" + stampId + "/confirm").andExpect(status().isOk());

        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        owner.get("/analytics/comparison").andExpect(status().isOk())
                .andExpect(jsonPath("$.branches[?(@.current.branchId == '" + branchId + "')].current.visits")
                        .value(Matchers.contains(1)))
                .andExpect(jsonPath("$.branches[?(@.current.branchId == '" + branchId + "')].current.newCustomers")
                        .value(Matchers.contains(1)))
                .andExpect(jsonPath("$.staff[?(@.branchIds[0] == '" + branchId + "')].confirmed")
                        .value(Matchers.hasItem(1)));

        // Menecer yalnız öz filialını görür
        signedInStaff(HOST, AdminRole.BRANCH_MANAGER, branchId).get("/analytics/comparison")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branches.length()").value(1));
    }

    @Test
    void invalidPeriodIsRejected() throws Exception {
        signedInStaff(HOST, AdminRole.COMPANY_OWNER, null).get("/analytics/comparison?from=2026-10-10&to=2026-10-01")
                .andExpect(status().isBadRequest());
    }
}
