package az.tapstamp.security.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import org.junit.jupiter.api.Test;

/** IDOR karkası: rol + filial matrisi. */
class AccessPolicyTest {

    private static final UUID CENTRE = UUID.randomUUID();
    private static final UUID GANJLIK = UUID.randomUUID();

    private static AdminPrincipal principal(RoleGrant... grants) {
        return new AdminPrincipal(AdminRealm.STAFF, UUID.randomUUID(), UUID.randomUUID(), "user@example.test", "User",
                Language.AZ, AdminSession.State.ACTIVE, Set.of(grants), true, null);
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.code()).isEqualTo(code));
    }

    @Test
    void ownerAccessesEveryBranch() {
        AdminPrincipal owner = principal(new RoleGrant(AdminRole.COMPANY_OWNER, null));
        assertThatCode(() -> AccessPolicy.requireBranch(owner, CENTRE)).doesNotThrowAnyException();
        assertThatCode(() -> AccessPolicy.requireBranch(owner, GANJLIK)).doesNotThrowAnyException();
        assertThatCode(() -> AccessPolicy.requireCompanyWide(owner)).doesNotThrowAnyException();
    }

    @Test
    void managerAndBaristaAreLimitedToTheirBranches() {
        AdminPrincipal manager = principal(new RoleGrant(AdminRole.BRANCH_MANAGER, GANJLIK));
        AdminPrincipal barista = principal(new RoleGrant(AdminRole.STAFF, CENTRE));

        assertThatCode(() -> AccessPolicy.requireBranch(manager, GANJLIK)).doesNotThrowAnyException();
        assertCode(() -> AccessPolicy.requireBranch(manager, CENTRE), ErrorCode.NOT_FOUND);
        assertThatCode(() -> AccessPolicy.requireBranch(barista, CENTRE)).doesNotThrowAnyException();
        assertCode(() -> AccessPolicy.requireBranch(barista, GANJLIK), ErrorCode.NOT_FOUND);
        assertCode(() -> AccessPolicy.requireBranch(barista, null), ErrorCode.NOT_FOUND);
        assertCode(() -> AccessPolicy.requireCompanyWide(manager), ErrorCode.FORBIDDEN);
    }

    @Test
    void multiBranchStaffSeesOnlyAssignedBranches() {
        UUID third = UUID.randomUUID();
        AdminPrincipal staff = principal(new RoleGrant(AdminRole.STAFF, CENTRE), new RoleGrant(AdminRole.STAFF, GANJLIK));
        assertThatCode(() -> AccessPolicy.requireBranch(staff, CENTRE)).doesNotThrowAnyException();
        assertThatCode(() -> AccessPolicy.requireBranch(staff, GANJLIK)).doesNotThrowAnyException();
        assertCode(() -> AccessPolicy.requireBranch(staff, third), ErrorCode.NOT_FOUND);
        assertCode(() -> AccessPolicy.requireAnyRole(staff, AdminRole.BRANCH_MANAGER, AdminRole.COMPANY_OWNER),
                ErrorCode.FORBIDDEN);
    }
}
