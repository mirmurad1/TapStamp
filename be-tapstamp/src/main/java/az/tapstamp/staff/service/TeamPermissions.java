package az.tapstamp.staff.service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.security.admin.RoleGrant;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;

/**
 * Komanda idarəetməsinin icazə qaydaları:
 * <ul>
 *   <li>sahib (COMPANY_OWNER) istənilən rolu verə və istənilən işçini idarə edə bilər (özündən başqa);</li>
 *   <li>filial meneceri yalnız idarə etdiyi filiallara barista (STAFF) dəvət edə və onları idarə edə bilər;</li>
 *   <li>barista komandanı idarə edə bilməz.</li>
 * </ul>
 */
final class TeamPermissions {

    private TeamPermissions() {
    }

    static Set<UUID> managedBranches(AdminPrincipal principal) {
        return principal.roles().stream()
                .filter(grant -> grant.role() == AdminRole.BRANCH_MANAGER)
                .map(RoleGrant::branchId)
                .collect(Collectors.toUnmodifiableSet());
    }

    static void requireCanGrant(AdminPrincipal principal, AdminRole role, UUID branchId) {
        if (principal.hasRole(AdminRole.COMPANY_OWNER) && role != AdminRole.SUPER_ADMIN) {
            return;
        }
        if (role == AdminRole.STAFF && branchId != null && managedBranches(principal).contains(branchId)) {
            return;
        }
        throw new ApiException(ErrorCode.FORBIDDEN);
    }

    static boolean canManage(AdminPrincipal principal, UUID targetId, Set<RoleGrant> targetRoles) {
        if (principal.userId().equals(targetId)) {
            return false;
        }
        if (principal.hasRole(AdminRole.COMPANY_OWNER)) {
            return true;
        }
        Set<UUID> managed = managedBranches(principal);
        return !managed.isEmpty() && !targetRoles.isEmpty() && targetRoles.stream()
                .allMatch(grant -> grant.role() == AdminRole.STAFF && managed.contains(grant.branchId()));
    }

    /** Sahib hamını, menecer yalnız öz filiallarındakı işçiləri görür. */
    static boolean canView(AdminPrincipal principal, Set<RoleGrant> targetRoles) {
        if (principal.hasRole(AdminRole.COMPANY_OWNER)) {
            return true;
        }
        Set<UUID> managed = managedBranches(principal);
        return targetRoles.stream().anyMatch(grant -> grant.branchId() != null && managed.contains(grant.branchId()));
    }
}
