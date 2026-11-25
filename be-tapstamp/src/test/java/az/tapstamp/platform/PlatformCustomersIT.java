package az.tapstamp.platform;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.TapIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Platformada şirkətin müştəri siyahısı: şirkət panelindəki sətirlər, telefonlar maskalanmış. */
class PlatformCustomersIT extends TapIntegrationTest {

    @Test
    void platformAdminSeesMaskedCustomerListOfTenant() throws Exception {
        signedIn(uniquePhone());
        AdminClient admin = signedInPlatformAdmin();
        String path = "/tenants/" + alpha().id() + "/customers";

        admin.get(path + "?status=ACTIVE&size=5").andExpect(status().isOk())
                .andExpect(jsonPath("$.customers.total").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.customers.items[0].phone").value(Matchers.containsString("***")))
                .andExpect(jsonPath("$.stampsRequired").isNumber());
        admin.get(path + "?size=7").andExpect(status().isBadRequest());

        // Şirkətin işçisi platforma siyahısına girə bilməz
        signedInStaff(HOST, AdminRole.COMPANY_OWNER, null)
                .perform(MockMvcRequestBuilders.get("/api/v1/platform" + path))
                .andExpect(status().isNotFound());
    }
}
