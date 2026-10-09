package az.tapstamp.staff.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import az.tapstamp.security.admin.AdminAccount;
import az.tapstamp.security.admin.AdminAuthService;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.security.admin.AdminRealms;
import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.security.admin.RoleGrant;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.staff.repository.StaffMapper;
import org.springframework.stereotype.Service;

/** Komanda: işçilərin siyahısı, bloklanma/aktivləşdirmə, yumşaq silinmə, itirilmiş TOTP-nin sıfırlanması. */
@Service
public class TeamService {

    public record MemberView(UUID id, String email, String displayName, AdminAccount.AccountStatus status,
                             List<RoleGrant> roles, boolean totpEnabled, Instant lastLoginAt) {
    }

    private final StaffMapper staff;
    private final AdminRealms realms;
    private final AdminAuthService auth;
    private final Clock clock;

    public TeamService(StaffMapper staff, AdminRealms realms, AdminAuthService auth, Clock clock) {
        this.staff = staff;
        this.realms = realms;
        this.auth = auth;
        this.clock = clock;
    }

    public List<MemberView> members(AdminPrincipal principal) {
        Map<UUID, Set<RoleGrant>> roles = new HashMap<>();
        for (StaffMapper.RoleRow role : staff.roles()) {
            roles.computeIfAbsent(role.staffUserId(), id -> new HashSet<>())
                    .add(new RoleGrant(role.role(), role.branchId()));
        }
        List<MemberView> members = new ArrayList<>();
        for (StaffMapper.MemberRow member : staff.members()) {
            Set<RoleGrant> grants = roles.getOrDefault(member.id(), Set.of());
            if (TeamPermissions.canView(principal, grants) || member.id().equals(principal.userId())) {
                members.add(new MemberView(member.id(), member.email(), member.displayName(), member.status(),
                        grants.stream().sorted((a, b) -> a.role().compareTo(b.role())).toList(),
                        member.totpEnabledAt() != null, member.lastLoginAt()));
            }
        }
        return members;
    }

    /** Hesabın bloklanması: bütün sessiyalar dərhal ləğv olunur. Sonuncu aktiv sahib bloklana bilməz. */
    public void updateStatus(AdminPrincipal principal, UUID userId, AdminAccount.AccountStatus status) {
        if (status == AdminAccount.AccountStatus.INVITED) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        AdminAccount target = manageable(principal, userId);
        if (target.status() == status) {
            return;
        }
        Instant now = clock.instant();
        Set<RoleGrant> grants = realms.accounts(AdminRealm.STAFF).loadRoles(userId);
        boolean owner = grants.stream().anyMatch(grant -> grant.role() == AdminRole.COMPANY_OWNER);
        realms.tx(AdminRealm.STAFF).executeWithoutResult(tx -> {
            if (status == AdminAccount.AccountStatus.DISABLED && owner && activeOwners() <= 1) {
                throw new ApiException(ErrorCode.FORBIDDEN);
            }
            realms.accounts(AdminRealm.STAFF).updateStatus(userId, status, now);
            if (status == AdminAccount.AccountStatus.DISABLED) {
                realms.sessions(AdminRealm.STAFF).revokeAll(userId, "ACCOUNT_DISABLED", now, null);
            }
        });
        realms.audit(AdminRealm.STAFF, status == AdminAccount.AccountStatus.DISABLED ? "STAFF_DISABLED" : "STAFF_ENABLED",
                principal.userId(), "account", userId, Map.of());
    }

    /**
     * İşçinin silinməsi — yumşaq silinmə (soft delete): sətir tarixçə üçün bazada qalır (status DELETED), sessiyaları
     * ləğv olunur, siyahıda və girişdə görünmür, email yenidən dəvət üçün boşalır. Özünü və sonuncu aktiv sahibi
     * silmək olmaz.
     */
    public void delete(AdminPrincipal principal, UUID userId) {
        if (userId.equals(principal.userId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        manageable(principal, userId);
        Instant now = clock.instant();
        Set<RoleGrant> grants = realms.accounts(AdminRealm.STAFF).loadRoles(userId);
        boolean owner = grants.stream().anyMatch(grant -> grant.role() == AdminRole.COMPANY_OWNER);
        realms.tx(AdminRealm.STAFF).executeWithoutResult(tx -> {
            if (owner && activeOwners(userId) < 1) {
                throw new ApiException(ErrorCode.FORBIDDEN);
            }
            realms.accounts(AdminRealm.STAFF).softDelete(userId, principal.userId(), now);
            realms.sessions(AdminRealm.STAFF).revokeAll(userId, "ACCOUNT_DELETED", now, null);
        });
        realms.audit(AdminRealm.STAFF, "STAFF_DELETED", principal.userId(), "account", userId, Map.of());
    }

    /**
     * İtirilmiş telefona görə TOTP-nin sıfırlanması (yalnız sahib, step-up ilə). İşçinin sessiyaları ləğv olunur;
     * növbəti girişdə TOTP-ni yenidən qurur.
     */
    public void resetMfa(AdminPrincipal principal, UUID userId) {
        if (!principal.hasRole(AdminRole.COMPANY_OWNER)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        auth.requireStepUp(principal);
        manageable(principal, userId);
        Instant now = clock.instant();
        realms.tx(AdminRealm.STAFF).executeWithoutResult(tx -> {
            realms.accounts(AdminRealm.STAFF).resetTotp(userId, now);
            realms.sessions(AdminRealm.STAFF).revokeAll(userId, "MFA_RESET", now, null);
        });
        realms.audit(AdminRealm.STAFF, "STAFF_MFA_RESET", principal.userId(), "account", userId, Map.of());
    }

    private AdminAccount manageable(AdminPrincipal principal, UUID userId) {
        AdminAccount target = realms.accounts(AdminRealm.STAFF).findById(userId)
                .filter(account -> account.status() != AdminAccount.AccountStatus.DELETED)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Set<RoleGrant> grants = realms.accounts(AdminRealm.STAFF).loadRoles(userId);
        if (!TeamPermissions.canManage(principal, userId, grants)) {
            // Menecer görmədiyi işçinin mövcudluğunu öyrənməsin
            throw new ApiException(TeamPermissions.canView(principal, grants) ? ErrorCode.FORBIDDEN : ErrorCode.NOT_FOUND);
        }
        return target;
    }

    private int activeOwners() {
        return activeOwners(null);
    }

    /** Aktiv sahiblərin sayı ({@code excluded} istisna olmaqla). */
    private int activeOwners(UUID excluded) {
        return staff.countActiveOwners(excluded);
    }
}
