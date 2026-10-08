package az.tapstamp.security.admin;

import java.util.UUID;

/** Rol təyinatı; MANAGER və STAFF üçün filial göstərilir. */
public record RoleGrant(AdminRole role, UUID branchId) {
}
