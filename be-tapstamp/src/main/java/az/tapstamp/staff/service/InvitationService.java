package az.tapstamp.staff.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.notification.mail.MailMessage;
import az.tapstamp.notification.mail.MailSender;
import az.tapstamp.notification.mail.MailTemplates;
import az.tapstamp.security.admin.AdminAccount;
import az.tapstamp.security.admin.AdminAccountStore;
import az.tapstamp.security.admin.AdminAuthProperties;
import az.tapstamp.security.admin.AdminAuthService;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.security.admin.AdminRealms;
import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.security.password.PasswordHasher;
import az.tapstamp.security.ratelimit.RateLimitPolicy;
import az.tapstamp.security.ratelimit.RateLimiter;
import az.tapstamp.security.token.SecureTokens;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.pii.EmailMasker;
import az.tapstamp.shared.web.ClientInfo;
import az.tapstamp.staff.repository.StaffMapper.OpenInvitation;
import az.tapstamp.staff.repository.StaffMapper;
import az.tapstamp.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * İşçi dəvətləri. Dəvət tokeni 256 bitdir, bazada yalnız hash-i saxlanılır, 72 saat etibarlıdır və bir dəfə
 * istifadə olunur. Eyni email-ə yeni dəvət göndəriləndə köhnələri ləğv olunur.
 */
@Service
public class InvitationService {

    public record InvitationView(UUID id, String email, AdminRole role, UUID branchId, Language language,
                                 Instant expiresAt, Instant createdAt) {
    }

    public record InvitationDetails(String email, AdminRole role, String branchName, String companyName,
                                    Language language) {
    }

    private final StaffMapper staff;
    private final TransactionTemplate tx;
    private final AdminRealms realms;
    private final AdminAuthService auth;
    private final PasswordHasher hasher;
    private final RateLimiter rateLimiter;
    private final MailSender mail;
    private final MailTemplates templates;
    private final TaskExecutor mailExecutor;
    private final AuditLogger audit;
    private final AdminAuthProperties properties;
    private final Clock clock;

    public InvitationService(StaffMapper staff, PlatformTransactionManager transactionManager,
                             AdminRealms realms, AdminAuthService auth, PasswordHasher hasher, RateLimiter rateLimiter,
                             MailSender mail, MailTemplates templates,
                             @Qualifier("applicationTaskExecutor") TaskExecutor mailExecutor, AuditLogger audit,
                             AdminAuthProperties properties, Clock clock) {
        this.staff = staff;
        this.tx = new TransactionTemplate(transactionManager);
        this.realms = realms;
        this.auth = auth;
        this.hasher = hasher;
        this.rateLimiter = rateLimiter;
        this.mail = mail;
        this.templates = templates;
        this.mailExecutor = mailExecutor;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /** Şirkət daxilindən dəvət (sahib və ya menecer). */
    public InvitationView invite(AdminPrincipal inviter, String email, AdminRole role, UUID branchId, Language language) {
        TeamPermissions.requireCanGrant(inviter, role, branchId);
        InvitationView view = create(inviter.userId(), email, role, branchId, language);
        audit.tenant("STAFF_INVITATION_CREATED", AuditLogger.ActorType.STAFF, inviter.userId(), "invitation", view.id(),
                Map.of("email", EmailMasker.mask(view.email()), "role", role.name()));
        return view;
    }

    /** Platforma admini tərəfindən şirkət sahibinin dəvəti. Tenant konteksti çağıran tərəfindən qurulur. */
    public InvitationView inviteOwnerByPlatform(UUID platformAdminId, String email, Language language) {
        InvitationView view = create(null, email, AdminRole.COMPANY_OWNER, null, language);
        audit.tenant("STAFF_INVITATION_CREATED", AuditLogger.ActorType.PLATFORM, platformAdminId, "invitation",
                view.id(), Map.of("email", EmailMasker.mask(view.email()), "role", AdminRole.COMPANY_OWNER.name()));
        return view;
    }

    /** Gözləyən dəvətlər: sahib hamısını, menecer yalnız öz filiallarına olanları görür. */
    public List<InvitationView> pending(AdminPrincipal principal) {
        boolean owner = principal.hasRole(AdminRole.COMPANY_OWNER);
        Set<UUID> managed = TeamPermissions.managedBranches(principal);
        return staff.pendingInvitations(clock.instant()).stream()
                .filter(view -> owner || (view.branchId() != null && managed.contains(view.branchId())))
                .toList();
    }

    public void revoke(AdminPrincipal principal, UUID invitationId) {
        InvitationView view = pending(principal).stream()
                .filter(invitation -> invitation.id().equals(invitationId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        TeamPermissions.requireCanGrant(principal, view.role(), view.branchId());
        staff.revokeInvitation(invitationId, clock.instant());
        audit.tenant("STAFF_INVITATION_REVOKED", AuditLogger.ActorType.STAFF, principal.userId(), "invitation",
                invitationId, Map.of());
    }

    /** Dəvət səhifəsi üçün: tokenin etibarlı olub-olmadığı və dəvətin məzmunu. */
    public InvitationDetails inspect(String token, ClientInfo client) {
        rateLimiter.consume(RateLimitPolicy.TOKEN_REDEEM_PER_IP, realms.rateLimitScope(AdminRealm.STAFF), client.ip());
        OpenInvitation invitation = findOpen(token, false).orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        return new InvitationDetails(invitation.email(), invitation.role(),
                invitation.branchId() == null ? null : branchName(invitation.branchId(), invitation.language()),
                companyName(invitation.language()), invitation.language());
    }

    /**
     * Dəvəti qəbul edir və hesab yaradır. Dəvət sətri {@code FOR UPDATE} ilə kilidlənir — eyni link ilə paralel
     * iki sorğu iki hesab yarada bilməz. Şifrə siyasəti pozularsa tranzaksiya geri qaytarılır, dəvət qüvvədə qalır.
     */
    public UUID accept(String token, String displayName, String password, ClientInfo client) {
        rateLimiter.consume(RateLimitPolicy.TOKEN_REDEEM_PER_IP, realms.rateLimitScope(AdminRealm.STAFF), client.ip());
        Instant now = clock.instant();
        AdminAccountStore accounts = realms.accounts(AdminRealm.STAFF);
        UUID userId = tx.execute(status -> {
            OpenInvitation invitation = findOpen(token, true).orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
            auth.checkPolicy(password, invitation.email());
            if (accounts.findByEmail(invitation.email()).isPresent()) {
                throw new ApiException(ErrorCode.ALREADY_EXISTS);
            }
            UUID id = accounts.insert(invitation.email(), hasher.hash(password), displayName.strip(),
                    invitation.language(), AdminAccount.AccountStatus.ACTIVE, now);
            staff.insertRole(Ids.newId(), id, invitation.role(), invitation.branchId(), now);
            staff.markInvitationAccepted(invitation.id(), now);
            return id;
        });
        audit.tenant("STAFF_INVITATION_ACCEPTED", AuditLogger.ActorType.STAFF, userId, "account", userId, Map.of());
        return userId;
    }

    private InvitationView create(UUID invitedBy, String email, AdminRole role, UUID branchId, Language language) {
        if (role == AdminRole.SUPER_ADMIN || role.branchScoped() != (branchId != null)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (branchId != null && !activeBranchExists(branchId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        String normalized = AdminAccountStore.normalizeEmail(email);
        if (realms.accounts(AdminRealm.STAFF).findByEmail(normalized).isPresent()) {
            throw new ApiException(ErrorCode.ALREADY_EXISTS);
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.invitationTtl());
        String token = SecureTokens.generate();
        UUID id = Ids.newId();
        tx.executeWithoutResult(status -> {
            staff.revokeInvitationsForEmail(normalized, now);
            staff.insertInvitation(id, normalized, role, branchId, SecureTokens.hash(token), expiresAt, invitedBy, now,
                    language);
        });
        // Token URL fraqmentindədir (#): server loglarına və Referer başlığına düşmür
        String link = realms.baseUrl(AdminRealm.STAFF) + "/admin/invitation#token=" + token;
        MailMessage message = templates.invitation(normalized, language, companyName(language), link,
                properties.invitationTtl().toHours(), TenantContext.require().id().toString());
        mailExecutor.execute(() -> mail.send(message));
        return new InvitationView(id, normalized, role, branchId, language, expiresAt, now);
    }

    private Optional<OpenInvitation> findOpen(String token, boolean lock) {
        return staff.findOpenInvitation(SecureTokens.hash(token == null ? "" : token), clock.instant(), lock);
    }

    private boolean activeBranchExists(UUID branchId) {
        return staff.activeBranchExists(branchId);
    }

    private String branchName(UUID branchId, Language language) {
        return staff.findBranchName(branchId).map(name -> name.resolve(language)).orElse(null);
    }

    private String companyName(Language language) {
        return staff.findCompanyName().map(name -> name.resolve(language)).orElse(TenantContext.require().slug());
    }
}
