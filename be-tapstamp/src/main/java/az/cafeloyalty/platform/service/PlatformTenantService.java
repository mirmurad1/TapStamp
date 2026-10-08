package az.cafeloyalty.platform.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.cafeloyalty.audit.AuditLogger;
import az.cafeloyalty.security.admin.AdminAuthService;
import az.cafeloyalty.security.admin.AdminPrincipal;
import az.cafeloyalty.security.admin.AdminRealm;
import az.cafeloyalty.security.admin.AdminRealms;
import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.shared.i18n.LocalizedText;
import az.cafeloyalty.shared.pii.EmailMasker;
import az.cafeloyalty.staff.service.InvitationService;
import az.cafeloyalty.tenancy.TenancyProperties;
import az.cafeloyalty.tenancy.TenantContext;
import az.cafeloyalty.tenancy.TenantStatus;
import az.cafeloyalty.tenancy.provisioning.ProvisionTenantCommand;
import az.cafeloyalty.tenancy.provisioning.TenantProvisioner;
import az.cafeloyalty.tenancy.registry.TenantDirectory;
import az.cafeloyalty.tenancy.registry.TenantRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Platforma adminlərinin şirkətlər üzərində əməliyyatları. Şirkətin bazasına yalnız konkret əməliyyat üçün
 * ({@link TenantContext#callAs}) qoşulur; müştərilərin şəxsi məlumatları platformaya göstərilmir — yalnız saylar.
 */
@Service
public class PlatformTenantService {

    public record TenantSummary(UUID id, String slug, LocalizedText displayName, TenantStatus status,
                                Language defaultLanguage, String timezone, String primaryDomain, List<String> domains) {
    }

    public record TenantStats(int branches, long activeCustomers, int activeStaff) {
    }

    public record OwnerView(UUID id, String email, String displayName, String status, boolean totpEnabled,
                            Instant lastLoginAt) {
    }

    private final TenantDirectory directory;
    private final TenantProvisioner provisioner;
    private final TenancyProperties tenancy;
    private final InvitationService invitations;
    private final AdminRealms realms;
    private final AdminAuthService auth;
    private final AuditLogger audit;
    private final JdbcClient tenantJdbc;
    private final Clock clock;

    public PlatformTenantService(TenantDirectory directory, TenantProvisioner provisioner, TenancyProperties tenancy,
                                 InvitationService invitations, AdminRealms realms, AdminAuthService auth, AuditLogger audit,
                                 @Qualifier("tenantJdbc") JdbcClient tenantJdbc, Clock clock) {
        this.directory = directory;
        this.provisioner = provisioner;
        this.tenancy = tenancy;
        this.invitations = invitations;
        this.realms = realms;
        this.auth = auth;
        this.audit = audit;
        this.tenantJdbc = tenantJdbc;
        this.clock = clock;
    }

    public List<TenantSummary> list() {
        return directory.findAll().stream().map(this::summary).toList();
    }

    public TenantSummary get(UUID tenantId) {
        return summary(tenant(tenantId));
    }

    /** Yeni şirkət: ayrıca baza, DB rolları, miqrasiyalar. Step-up tələb edir. */
    public TenantSummary provision(AdminPrincipal principal, ProvisionTenantCommand command) {
        auth.requireStepUp(principal);
        if (!tenancy.provisioner().enabled()) {
            throw new ApiException(ErrorCode.PROVISIONING_DISABLED);
        }
        if (directory.findBySlug(command.slug()).isPresent() || directory.findByHost(command.primaryDomain()).isPresent()) {
            throw new ApiException(ErrorCode.ALREADY_EXISTS);
        }
        TenantRecord tenant = provisioner.provision(command);
        realms.audit(AdminRealm.PLATFORM, "TENANT_PROVISIONED", principal.userId(), "tenant", tenant.id(),
                Map.of("slug", tenant.slug(), "domain", command.primaryDomain()));
        return summary(tenant);
    }

    /** Şirkətin dayandırılması və ya bərpası: dayandırılmış şirkətin domeni dərhal 503 qaytarır. */
    public TenantSummary updateStatus(AdminPrincipal principal, UUID tenantId, TenantStatus status) {
        auth.requireStepUp(principal);
        if (status != TenantStatus.ACTIVE && status != TenantStatus.SUSPENDED) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        TenantRecord tenant = tenant(tenantId);
        if (tenant.status() != TenantStatus.ACTIVE && tenant.status() != TenantStatus.SUSPENDED) {
            throw new ApiException(ErrorCode.TENANT_UNAVAILABLE);
        }
        directory.updateStatus(tenantId, status, null);
        realms.audit(AdminRealm.PLATFORM, status == TenantStatus.SUSPENDED ? "TENANT_SUSPENDED" : "TENANT_ACTIVATED",
                principal.userId(), "tenant", tenantId, Map.of("slug", tenant.slug()));
        return get(tenantId);
    }

    public TenantStats stats(UUID tenantId) {
        TenantRecord tenant = activeTenant(tenantId);
        return TenantContext.callAs(tenant.toInfo(), () -> new TenantStats(
                count("SELECT count(*) FROM branches WHERE status = 'ACTIVE'"),
                tenantJdbc.sql("SELECT count(*) FROM customers WHERE status = 'ACTIVE'").query(Long.class).single(),
                count("SELECT count(*) FROM staff_users WHERE status = 'ACTIVE'")));
    }

    public List<OwnerView> owners(UUID tenantId) {
        TenantRecord tenant = activeTenant(tenantId);
        return TenantContext.callAs(tenant.toInfo(), () -> tenantJdbc.sql("""
                        SELECT DISTINCT u.id, u.email, u.display_name, u.status, u.totp_enabled_at, u.last_login_at,
                               u.created_at
                        FROM staff_users u JOIN staff_roles r ON r.staff_user_id = u.id
                        WHERE r.role = 'COMPANY_OWNER' ORDER BY u.created_at""")
                .query((rs, n) -> {
                    var lastLogin = rs.getTimestamp("last_login_at");
                    return new OwnerView(rs.getObject("id", UUID.class), rs.getString("email"),
                            rs.getString("display_name"), rs.getString("status"),
                            rs.getTimestamp("totp_enabled_at") != null,
                            lastLogin == null ? null : lastLogin.toInstant());
                })
                .list());
    }

    /** Şirkətin sahibinə dəvət (ilk sahib və ya əlavə sahib). Link şirkətin öz domenində açılır. */
    public InvitationService.InvitationView inviteOwner(AdminPrincipal principal, UUID tenantId, String email,
                                                        Language language) {
        TenantRecord tenant = activeTenant(tenantId);
        var view = TenantContext.callAs(tenant.toInfo(),
                () -> invitations.inviteOwnerByPlatform(principal.userId(), email, language));
        realms.audit(AdminRealm.PLATFORM, "TENANT_OWNER_INVITED", principal.userId(), "tenant", tenantId,
                Map.of("email", EmailMasker.mask(view.email())));
        return view;
    }

    /** Sahib həm telefonunu, həm bərpa kodlarını itirəndə: TOTP sıfırlanır, sessiyaları ləğv olunur. Step-up tələb edir. */
    public void resetOwnerMfa(AdminPrincipal principal, UUID tenantId, UUID userId) {
        auth.requireStepUp(principal);
        TenantRecord tenant = activeTenant(tenantId);
        boolean found = owners(tenantId).stream().anyMatch(owner -> owner.id().equals(userId));
        if (!found) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        Instant now = clock.instant();
        TenantContext.runAs(tenant.toInfo(), () -> {
            realms.tx(AdminRealm.STAFF).executeWithoutResult(tx -> {
                realms.accounts(AdminRealm.STAFF).resetTotp(userId, now);
                realms.sessions(AdminRealm.STAFF).revokeAll(userId, "MFA_RESET", now, null);
            });
            audit.tenant("STAFF_MFA_RESET", AuditLogger.ActorType.PLATFORM, principal.userId(), "account", userId,
                    Map.of());
        });
        realms.audit(AdminRealm.PLATFORM, "TENANT_OWNER_MFA_RESET", principal.userId(), "tenant", tenantId,
                Map.of("userId", userId.toString()));
    }

    private int count(String sql) {
        return tenantJdbc.sql(sql).query(Integer.class).single();
    }

    private TenantRecord tenant(UUID tenantId) {
        return directory.findById(tenantId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private TenantRecord activeTenant(UUID tenantId) {
        TenantRecord tenant = tenant(tenantId);
        if (!tenant.status().servesRequests()) {
            throw new ApiException(ErrorCode.TENANT_UNAVAILABLE);
        }
        return tenant;
    }

    private TenantSummary summary(TenantRecord tenant) {
        return new TenantSummary(tenant.id(), tenant.slug(), tenant.displayName(), tenant.status(),
                tenant.defaultLanguage(), tenant.timezone().getId(), directory.primaryHost(tenant.id()).orElse(null),
                directory.hostnames(tenant.id()));
    }
}
