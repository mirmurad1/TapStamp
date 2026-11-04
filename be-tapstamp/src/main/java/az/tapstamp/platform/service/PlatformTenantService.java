package az.tapstamp.platform.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.plan.Plan;
import az.tapstamp.plan.PlanService;
import az.tapstamp.plan.Subscription;
import az.tapstamp.platform.repository.TenantOverviewMapper;
import az.tapstamp.security.admin.AdminAuthService;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.security.admin.AdminRealms;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.pii.EmailMasker;
import az.tapstamp.staff.service.InvitationService;
import az.tapstamp.tenancy.TenancyProperties;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.TenantStatus;
import az.tapstamp.tenancy.provisioning.ProvisionTenantCommand;
import az.tapstamp.tenancy.provisioning.TenantProvisioner;
import az.tapstamp.tenancy.registry.TenantDirectory;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.springframework.stereotype.Service;

/**
 * Platforma adminlərinin şirkətlər üzərində əməliyyatları. Şirkətin bazasına yalnız konkret əməliyyat üçün
 * ({@link TenantContext#callAs}) qoşulur; müştərilərin şəxsi məlumatları platformaya göstərilmir — yalnız saylar.
 */
@Service
public class PlatformTenantService {

    public record TenantSummary(UUID id, String slug, LocalizedText displayName, TenantStatus status,
                                Language defaultLanguage, String timezone, String primaryDomain, List<String> domains,
                                Subscription subscription, boolean planExpired) {
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
    private final TenantOverviewMapper overview;
    private final Clock clock;
    private final PlanService plans;

    public PlatformTenantService(TenantDirectory directory, TenantProvisioner provisioner, TenancyProperties tenancy,
                                 InvitationService invitations, AdminRealms realms, AdminAuthService auth, AuditLogger audit,
                                 TenantOverviewMapper overview, Clock clock, PlanService plans) {
        this.directory = directory;
        this.provisioner = provisioner;
        this.tenancy = tenancy;
        this.invitations = invitations;
        this.realms = realms;
        this.auth = auth;
        this.audit = audit;
        this.overview = overview;
        this.clock = clock;
        this.plans = plans;
    }

    public List<TenantSummary> list() {
        return directory.findAll().stream().map(this::summary).toList();
    }

    public TenantSummary get(UUID tenantId) {
        return summary(tenant(tenantId));
    }

    /** Yeni şirkət: ayrıca baza, DB rolları, miqrasiyalar; seçilmiş paketin 30 günlük sınağı. Step-up tələb edir. */
    public TenantSummary provision(AdminPrincipal principal, ProvisionTenantCommand command, Plan plan) {
        auth.requireStepUp(principal);
        if (!tenancy.provisioner().enabled()) {
            throw new ApiException(ErrorCode.PROVISIONING_DISABLED);
        }
        if (directory.findBySlug(command.slug()).isPresent() || directory.findByHost(command.primaryDomain()).isPresent()) {
            throw new ApiException(ErrorCode.ALREADY_EXISTS);
        }
        TenantRecord tenant = provisioner.provision(command);
        plans.startTrial(tenant.id(), plan, principal.userId());
        realms.audit(AdminRealm.PLATFORM, "TENANT_PROVISIONED", principal.userId(), "tenant", tenant.id(),
                Map.of("slug", tenant.slug(), "domain", command.primaryDomain(), "plan", plan.name()));
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
        return TenantContext.callAs(tenant.toInfo(), overview::stats);
    }

    public List<OwnerView> owners(UUID tenantId) {
        TenantRecord tenant = activeTenant(tenantId);
        return TenantContext.callAs(tenant.toInfo(), overview::owners);
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


    private TenantRecord tenant(UUID tenantId) {
        return directory.findById(tenantId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private TenantRecord activeTenant(UUID tenantId) {
        TenantRecord tenant = tenant(tenantId);
        if (tenant.status().outOfService()) {
            throw new ApiException(ErrorCode.TENANT_UNAVAILABLE);
        }
        return tenant;
    }

    private TenantSummary summary(TenantRecord tenant) {
        Subscription subscription = plans.subscription(tenant.id());
        return new TenantSummary(tenant.id(), tenant.slug(), tenant.displayName(), tenant.status(),
                tenant.defaultLanguage(), tenant.timezone().getId(), directory.primaryHost(tenant.id()).orElse(null),
                directory.hostnames(tenant.id()), subscription, subscription.expired(clock.instant()));
    }
}
