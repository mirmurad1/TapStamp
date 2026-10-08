package az.tapstamp.nfc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.TapIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * NFC çiplərinin idarəsi platforma panelindən: qeydiyyat, birdəfəlik provisioning paketi, aktivləşdirmə, açar
 * rotasiyası, deaktivasiya, pulsuz/pullu say. Şirkət panelində çiplər yalnız baxış üçündür.
 */
class TagAdminIT extends TapIntegrationTest {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    private static String randomUid() {
        byte[] uid = new byte[7];
        ThreadLocalRandom.current().nextBytes(uid);
        uid[0] = 0x04;
        return HEX.formatHex(uid);
    }

    /** Provisioning paketindəki açarlarla "proqramlaşdırılmış" çip. */
    private TestTag programmed(ResultActions pkg, UUID tagId, UUID branchId) throws Exception {
        return new TestTag(tagId, branchId, HEX.parseHex(json(pkg, "$.uid")), HEX.parseHex(json(pkg, "$.sdmMetaReadKey")),
                HEX.parseHex(json(pkg, "$.sdmFileReadKey")));
    }

    private String base() {
        return "/tenants/" + alpha().id() + "/tags";
    }

    @Test
    void platformAdminRegistersProvisionsActivatesAndRotatesTag() throws Exception {
        UUID branchId = newBranch(Policy.relaxed());
        AdminClient admin = signedInPlatformAdmin();
        String base = base();
        String uid = randomUid();
        String formatted = String.join(":", uid.split("(?<=\\G..)")).toLowerCase(java.util.Locale.ROOT);

        ResultActions created = admin.post(base, Map.of("uid", formatted, "branchId", branchId, "label", "Piştaxta 1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uid").value(uid))
                .andExpect(jsonPath("$.pendingKeyVersion").value(1))
                .andExpect(jsonPath("$.activeKeyVersion").doesNotExist());
        UUID tagId = UUID.fromString(json(created, "$.id"));
        admin.post(base, Map.of("uid", uid, "branchId", branchId, "label", "Təkrar")).andExpect(status().isConflict());
        admin.post(base, Map.of("uid", "XYZ", "branchId", branchId, "label", "Səhv")).andExpect(status().isBadRequest());
        admin.post("/tenants/" + UUID.randomUUID() + "/tags", Map.of("uid", randomUid(), "branchId", branchId, "label", "X"))
                .andExpect(status().isNotFound());

        // Say: ilk çip pulsuzdur, qalanları pullu; cəmi = pulsuz + pullu
        ResultActions overview = admin.get(base).andExpect(status().isOk())
                .andExpect(jsonPath("$.usage.freeLimit").value(1))
                .andExpect(jsonPath("$.usage.free").value(1))
                .andExpect(jsonPath("$.branches[?(@.id == '" + branchId + "')]").isNotEmpty())
                .andExpect(jsonPath("$.tags[?(@.id == '" + tagId + "')]").isNotEmpty());
        int total = Integer.parseInt(json(overview, "$.usage.total"));
        int paid = Integer.parseInt(json(overview, "$.usage.paid"));
        assertThat(paid).isEqualTo(total - 1);
        overview.andExpect(jsonPath("$.tags[?(@.free == true && @.type != 'SIMULATED')]").value(org.hamcrest.Matchers.hasSize(1)));

        // Paket: step-up ilə, yalnız bir dəfə
        String pkgPath = base + "/" + tagId + "/provisioning-package";
        admin.post(pkgPath).andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        admin.post("/auth/step-up", Map.of("password", STAFF_PASSWORD)).andExpect(status().isNoContent());
        ResultActions pkg = admin.post(pkgPath).andExpect(status().isOk())
                .andExpect(jsonPath("$.keyVersion").value(1))
                .andExpect(jsonPath("$.appMasterKey").value(org.hamcrest.Matchers.matchesPattern("^[0-9A-F]{32}$")));
        String url = json(pkg, "$.url");
        assertThat(url).contains(HOST).contains("/t/" + uid + "?picc=");
        String withoutScheme = url.substring("https://".length());
        int piccOffset = Integer.parseInt(json(pkg, "$.piccDataOffset"));
        int macOffset = Integer.parseInt(json(pkg, "$.sdmMacOffset"));
        assertThat(withoutScheme.substring(piccOffset - 7, piccOffset - 7 + 32)).isEqualTo("0".repeat(32));
        assertThat(withoutScheme.substring(macOffset - 7)).isEqualTo("0".repeat(16));
        admin.post(pkgPath).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROVISIONING_ALREADY_EXPORTED"));

        // İlk toxunuş PENDING açarı aktivləşdirir; sahib bunu öz panelində görür
        TestTag tag = programmed(pkg, tagId, branchId);
        CustomerClient customer = signedIn(uniquePhone());
        customer.post(claimPath(tapToken(customer, tag, 1))).andExpect(status().isOk());
        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        owner.get("/tags?branchId=" + branchId)
                .andExpect(jsonPath("$[0].activeKeyVersion").value(1))
                .andExpect(jsonPath("$[0].pendingKeyVersion").doesNotExist())
                .andExpect(jsonPath("$[0].tapsLast7Days").value(1));

        // Rotasiya: ixrac olunmamış PENDING dəst əvəzlənir; ixracdan sonra yeni rotasiya bloklanır
        String rotations = base + "/" + tagId + "/key-rotations";
        admin.post(rotations).andExpect(jsonPath("$.pendingKeyVersion").value(2));
        admin.post(rotations).andExpect(jsonPath("$.pendingKeyVersion").value(2));
        TestTag rotated = programmed(admin.post(pkgPath).andExpect(status().isOk()), tagId, branchId);
        admin.post(rotations).andExpect(jsonPath("$.code").value("TAG_ROTATION_IN_PROGRESS"));
        // Keçidə qədər köhnə açar işləyir, yeni açarla ilk toxunuşda keçid baş verir
        assertThat(tap(customer, tapPath(tag, 2))).startsWith("/tap/").doesNotStartWith("/tap/error");
        assertThat(tap(customer, tapPath(rotated, 3))).startsWith("/tap/").doesNotStartWith("/tap/error");
        owner.get("/tags?branchId=" + branchId).andExpect(jsonPath("$[0].activeKeyVersion").value(2));
        assertThat(tap(customer, tapPath(tag, 4))).isEqualTo("/tap/error?code=TAP_SIGNATURE_INVALID");

        // Deaktivasiya dərhal qüvvəyə minir
        admin.post(base + "/" + tagId + "/disable").andExpect(jsonPath("$.status").value("DISABLED"));
        assertThat(tap(customer, tapPath(rotated, 5))).isEqualTo("/tap/error?code=TAG_DISABLED");
        admin.post(base + "/" + tagId + "/enable").andExpect(jsonPath("$.status").value("ACTIVE"));
        admin.patch(base + "/" + tagId, Map.of("label", "Kassa yanı")).andExpect(jsonPath("$.label").value("Kassa yanı"));

        admin.get(base + "/" + tagId + "/events")
                .andExpect(jsonPath("$[?(@.outcome == 'REJECTED')].reason").value(org.hamcrest.Matchers.hasItem("TAG_DISABLED")))
                .andExpect(jsonPath("$[?(@.outcome == 'ACCEPTED')]").isNotEmpty());

        // Audit: şirkətin jurnalında (aktor — platforma) və platforma jurnalında
        owner.get("/audit-logs?action=TAG_PROVISIONING_EXPORTED")
                .andExpect(jsonPath("$[?(@.targetId == '" + tagId + "')]").isNotEmpty());
        owner.get("/audit-logs/actions").andExpect(jsonPath("$").value(org.hamcrest.Matchers.hasItem("TAG_REGISTERED")));
        owner.get("/audit-logs?action=bad-action").andExpect(status().isBadRequest());
        admin.get("/audit-logs?action=TENANT_TAG_REGISTERED")
                .andExpect(jsonPath("$[?(@.targetId == '" + alpha().id() + "')]").isNotEmpty());
    }

    @Test
    void companyStaffSeeTagsReadOnly() throws Exception {
        UUID branchId = newBranch(Policy.relaxed());
        TestTag tag = newTag(branchId);
        String tagPath = "/tags/" + tag.id();

        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        owner.get("/tags").andExpect(jsonPath("$[?(@.id == '" + tag.id() + "')]").isNotEmpty());
        owner.get(tagPath + "/events").andExpect(status().isOk());
        // Sahib çip əlavə edə və ya dəyişə bilməz: bu endpoint-lər artıq yoxdur
        owner.post("/tags", Map.of("uid", randomUid(), "branchId", branchId, "label", "Yeni"))
                .andExpect(status().is4xxClientError());
        owner.post(tagPath + "/disable").andExpect(status().is4xxClientError());
        owner.post(tagPath + "/provisioning-package").andExpect(status().is4xxClientError());
        owner.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/platform/tenants/" + alpha().id() + "/tags"))
                .andExpect(status().isNotFound());

        AdminClient manager = signedInStaff(HOST, AdminRole.BRANCH_MANAGER, branchId);
        manager.get("/tags").andExpect(jsonPath("$[?(@.id == '" + tag.id() + "')]").isNotEmpty());
        manager.get(tagPath + "/events").andExpect(status().isOk());
        manager.get("/audit-logs").andExpect(status().isForbidden());

        AdminClient other = signedInStaff(HOST, AdminRole.BRANCH_MANAGER, newBranch(Policy.relaxed()));
        other.get("/tags").andExpect(jsonPath("$[?(@.id == '" + tag.id() + "')]").isEmpty());
        other.get(tagPath + "/events").andExpect(status().isNotFound());
        signedInBarista(branchId).get("/tags").andExpect(status().isForbidden());
    }
}
