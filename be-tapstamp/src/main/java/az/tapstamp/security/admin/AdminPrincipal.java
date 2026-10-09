package az.tapstamp.security.admin;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import az.tapstamp.shared.i18n.Language;
import org.jspecify.annotations.NonNull;

/** Daxil olmuş admin (işçi və ya platforma admini) və onun cari sessiyası. */
public record AdminPrincipal(
        AdminRealm realm,
        UUID userId,
        UUID sessionId,
        String email,
        String displayName,
        Language language,
        AdminSession.State state,
        Set<RoleGrant> roles,
        boolean totpEnabled,
        Instant stepUpAt) {

    public boolean hasRole(AdminRole role) {
        return roles.stream().anyMatch(grant -> grant.role() == role);
    }

    /** MANAGER/STAFF yalnız öz filiallarına, OWNER və SUPER_ADMIN bütün filiallara çıxışa malikdir. */
    public boolean canAccessBranch(UUID branchId) {
        return hasRole(AdminRole.COMPANY_OWNER) || hasRole(AdminRole.SUPER_ADMIN)
                || roles.stream().anyMatch(grant -> branchId.equals(grant.branchId()));
    }

    @Override
    public @NonNull String toString() {
        return "AdminPrincipal[realm=" + realm + ", userId=" + userId + ", state=" + state + "]";
    }
}
