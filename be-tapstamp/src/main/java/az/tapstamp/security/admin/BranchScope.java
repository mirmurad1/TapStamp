package az.tapstamp.security.admin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Sorğunun filial çərçivəsi: sahib bütün filialları, menecer yalnız öz filiallarını görür. Konkret filial
 * istənilərsə, ona çıxış {@link AccessPolicy#requireBranch} ilə yoxlanılır (başqa filial — 404).
 *
 * <p>SQL-də istifadə: {@code (:allBranches OR x.branch_id IN (:branchIds))}. {@code branchIds} heç vaxt boş deyil
 * (boş IN siyahısı SQL xətasıdır), ona görə "hamısı" halında yer tutan ID qaytarılır.
 *
 * @param all       bütün filiallar (yalnız sahib, filial seçilmədikdə)
 * @param branchIds icazə verilən filiallar ({@code all} olduqda istifadə olunmur)
 */
public record BranchScope(boolean all, List<UUID> branchIds) {

    private static final UUID NONE = new UUID(0, 0);

    public static BranchScope of(AdminPrincipal principal, UUID requestedBranchId) {
        if (requestedBranchId != null) {
            AccessPolicy.requireBranch(principal, requestedBranchId);
            return new BranchScope(false, List.of(requestedBranchId));
        }
        if (principal.hasRole(AdminRole.COMPANY_OWNER) || principal.hasRole(AdminRole.SUPER_ADMIN)) {
            return new BranchScope(true, List.of(NONE));
        }
        List<UUID> own = principal.roles().stream().map(RoleGrant::branchId)
                .filter(Objects::nonNull).distinct().toList();
        return new BranchScope(false, own.isEmpty() ? List.of(NONE) : own);
    }
}
