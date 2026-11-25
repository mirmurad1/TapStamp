package az.tapstamp.platform;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.tapstamp.support.AdminClient;
import az.tapstamp.support.TapIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/** Platformanın ana səhifəsi: ümumi göstəricilər və şirkətlər siyahısında axtarış. */
class PlatformTenantOverviewIT extends TapIntegrationTest {

    @Test
    void overviewCountsTenantsAndCustomers() throws Exception {
        signedIn(uniquePhone());
        AdminClient admin = signedInPlatformAdmin();

        admin.get("/tenants/overview").andExpect(status().isOk())
                .andExpect(jsonPath("$.tenants").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.active").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.totalCustomers").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.customersByTenant['" + alpha().id() + "']").value(Matchers.greaterThan(0)));
    }

    @Test
    void listFiltersBySearchAndStatus() throws Exception {
        AdminClient admin = signedInPlatformAdmin();
        String slug = alpha().slug();

        admin.get("/tenants?q=" + slug.toUpperCase(java.util.Locale.ROOT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].slug").value(Matchers.hasItem(slug)));
        admin.get("/tenants?q=no-such-tenant-xyz").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        admin.get("/tenants?status=ACTIVE").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].status").value(Matchers.everyItem(Matchers.is("ACTIVE"))));
    }
}
