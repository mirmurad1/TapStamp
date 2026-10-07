package az.cafeloyalty.nfc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import az.cafeloyalty.security.admin.AdminRole;
import az.cafeloyalty.support.AdminClient;
import az.cafeloyalty.support.CustomerClient;
import az.cafeloyalty.support.TapIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** NFC çiplərinin idarəsi: qeydiyyat, birdəfəlik provisioning paketi, aktivləşdirmə, açar rotasiyası, deaktivasiya. */
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

    @Test
    void ownerRegistersProvisionsActivatesAndRotatesTag() throws Exception {
        UUID branchId = newBranch(Policy.relaxed());
        AdminClient owner = signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
        String uid = randomUid();
        String formatted = String.join(":", uid.split("(?<=\\G..)")).toLowerCase(java.util.Locale.ROOT);

        ResultActions created = owner.post("/tags", Map.of("uid", formatted, "branchId", branchId, "label", "Piştaxta 1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uid").value(uid))
                .andExpect(jsonPath("$.pendingKeyVersion").value(1))
                .andExpect(jsonPath("$.activeKeyVersion").doesNotExist());
        UUID tagId = UUID.fromString(json(created, "$.id"));
        owner.post("/tags", Map.of("uid", uid, "branchId", branchId, "label", "Təkrar")).andExpect(status().isConflict());
        owner.post("/tags", Map.of("uid", "XYZ", "branchId", branchId, "label", "Səhv")).andExpect(status().isBadRequest());

        // Paket: step-up ilə, yalnız bir dəfə
        String pkgPath = "/tags/" + tagId + "/provisioning-package";
        owner.post(pkgPath).andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        owner.post("/auth/step-up", Map.of("password", STAFF_PASSWORD)).andExpect(status().isNoContent());
        ResultActions pkg = owner.post(pkgPath).andExpect(status().isOk())
                .andExpect(jsonPath("$.keyVersion").value(1))
                .andExpect(jsonPath("$.appMasterKey").value(org.hamcrest.Matchers.matchesPattern("^[0-9A-F]{32}$")));
        String url = json(pkg, "$.url");
        assertThat(url).contains("/t/" + uid + "?picc=");
        String withoutScheme = url.substring("https://".length());
        int piccOffset = Integer.parseInt(json(pkg, "$.piccDataOffset"));
        int macOffset = Integer.parseInt(json(pkg, "$.sdmMacOffset"));
        assertThat(withoutScheme.substring(piccOffset - 7, piccOffset - 7 + 32)).isEqualTo("0".repeat(32));
        assertThat(withoutScheme.substring(macOffset - 7)).isEqualTo("0".repeat(16));
        owner.post(pkgPath).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROVISIONING_ALREADY_EXPORTED"));

        // İlk toxunuş PENDING açarı aktivləşdirir
        TestTag tag = programmed(pkg, tagId, branchId);
        CustomerClient customer = signedIn(uniquePhone());
        customer.post(claimPath(tapToken(customer, tag, 1))).andExpect(status().isOk());
        owner.get("/tags?branchId=" + branchId)
                .andExpect(jsonPath("$[0].activeKeyVersion").value(1))
                .andExpect(jsonPath("$[0].pendingKeyVersion").doesNotExist())
                .andExpect(jsonPath("$[0].tapsLast7Days").value(1));

        // Rotasiya: ixrac olunmamış PENDING dəst əvəzlənir; ixracdan sonra yeni rotasiya bloklanır
        owner.post("/tags/" + tagId + "/key-rotations").andExpect(jsonPath("$.pendingKeyVersion").value(2));
        owner.post("/tags/" + tagId + "/key-rotations").andExpect(jsonPath("$.pendingKeyVersion").value(2));
        TestTag rotated = programmed(owner.post(pkgPath).andExpect(status().isOk()), tagId, branchId);
        owner.post("/tags/" + tagId + "/key-rotations").andExpect(jsonPath("$.code").value("TAG_ROTATION_IN_PROGRESS"));
        // Keçidə qədər köhnə açar işləyir, yeni açarla ilk toxunuşda keçid baş verir
        assertThat(tap(customer, tapPath(tag, 2))).startsWith("/tap/").doesNotStartWith("/tap/error");
        assertThat(tap(customer, tapPath(rotated, 3))).startsWith("/tap/").doesNotStartWith("/tap/error");
        owner.get("/tags?branchId=" + branchId).andExpect(jsonPath("$[0].activeKeyVersion").value(2));
        assertThat(tap(customer, tapPath(tag, 4))).isEqualTo("/tap/error?code=TAP_SIGNATURE_INVALID");

        // Deaktivasiya dərhal qüvvəyə minir
        owner.post("/tags/" + tagId + "/disable").andExpect(jsonPath("$.status").value("DISABLED"));
        assertThat(tap(customer, tapPath(rotated, 5))).isEqualTo("/tap/error?code=TAG_DISABLED");
        owner.post("/tags/" + tagId + "/enable").andExpect(jsonPath("$.status").value("ACTIVE"));
        owner.patch("/tags/" + tagId, Map.of("label", "Kassa yanı")).andExpect(jsonPath("$.label").value("Kassa yanı"));

        owner.get("/tags/" + tagId + "/events")
                .andExpect(jsonPath("$[?(@.outcome == 'REJECTED')].reason").value(org.hamcrest.Matchers.hasItem("TAG_DISABLED")))
                .andExpect(jsonPath("$[?(@.outcome == 'ACCEPTED')]").isNotEmpty());

        // Audit jurnalı: sahib görür, əməliyyat filtri işləyir
        owner.get("/audit-logs?action=TAG_PROVISIONING_EXPORTED")
                .andExpect(jsonPath("$[?(@.targetId == '" + tagId + "')]").isNotEmpty())
                .andExpect(jsonPath("$[0].actorName").exists());
        owner.get("/audit-logs/actions").andExpect(jsonPath("$").value(org.hamcrest.Matchers.hasItem("TAG_REGISTERED")));
        owner.get("/audit-logs?action=bad-action").andExpect(status().isBadRequest());
    }

    @Test
    void managerSeesOwnBranchTagsReadOnly() throws Exception {
        UUID branchId = newBranch(Policy.relaxed());
        TestTag tag = newTag(branchId);
        AdminClient manager = signedInStaff(HOST, AdminRole.BRANCH_MANAGER, branchId);
        manager.get("/tags").andExpect(jsonPath("$[?(@.id == '" + tag.id() + "')]").isNotEmpty());
        manager.get("/tags/" + tag.id() + "/events").andExpect(status().isOk());
        manager.post("/tags", Map.of("uid", randomUid(), "branchId", branchId, "label", "Yeni")).andExpect(status().isForbidden());
        manager.post("/tags/" + tag.id() + "/disable").andExpect(status().isForbidden());
        manager.get("/audit-logs").andExpect(status().isForbidden());

        AdminClient other = signedInStaff(HOST, AdminRole.BRANCH_MANAGER, newBranch(Policy.relaxed()));
        other.get("/tags").andExpect(jsonPath("$[?(@.id == '" + tag.id() + "')]").isEmpty());
        other.get("/tags/" + tag.id() + "/events").andExpect(status().isNotFound());
        signedInBarista(branchId).get("/tags").andExpect(status().isForbidden());
    }
}
