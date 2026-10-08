package az.cafeloyalty.staff.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import az.cafeloyalty.audit.AuditLogger;
import az.cafeloyalty.notification.mail.MailMessage;
import az.cafeloyalty.notification.mail.MailSender;
import az.cafeloyalty.notification.mail.MailTemplates;
import az.cafeloyalty.security.admin.AdminAccount;
import az.cafeloyalty.security.admin.AdminAccountStore;
import az.cafeloyalty.security.admin.AdminAuthProperties;
import az.cafeloyalty.security.admin.AdminAuthService;
import az.cafeloyalty.security.admin.AdminPrincipal;
import az.cafeloyalty.security.admin.AdminRealm;
import az.cafeloyalty.security.admin.AdminRealms;
import az.cafeloyalty.security.admin.AdminRole;
import az.cafeloyalty.shared.web.ClientInfo;
import az.cafeloyalty.security.password.PasswordHasher;
import az.cafeloyalty.security.ratelimit.RateLimitPolicy;
import az.cafeloyalty.security.ratelimit.RateLimiter;
import az.cafeloyalty.security.token.SecureTokens;
import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.shared.id.Ids;
import az.cafeloyalty.shared.persistence.LocalizedTextConverter;
import az.cafeloyalty.shared.pii.EmailMasker;
import az.cafeloyalty.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.simple.JdbcClient;
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

    private record OpenInvitation(UUID id, String email, AdminRole role, UUID branchId, Language language) {
    }

    private static final LocalizedTextConverter TEXT = new LocalizedTextConverter();

    private final JdbcClient jdbc;
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

    public InvitationService(@Qualifier("tenantJdbc") JdbcClient jdbc, PlatformTransactionManager transactionManager,
                             AdminRealms realms, AdminAuthService auth, PasswordHasher hasher, RateLimiter rateLimiter,
                             MailSender mail, MailTemplates templates,
                             @Qualifier("applicationTaskExecutor") TaskExecutor mailExecutor, AuditLogger audit,
                             AdminAuthProperties properties, Clock clock) {
        this.jdbc = jdbc;
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
        return jdbc.sql("""
                        SELECT id, email, role, branch_id, language, expires_at, created_at FROM staff_invitations
                        WHERE accepted_at IS NULL AND revoked_at IS NULL AND expires_at > :now
                        ORDER BY created_at DESC""")
                .param("now", ts(clock.instant()))
                .query((rs, n) -> new InvitationView(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        AdminRole.valueOf(rs.getString("role")),
                        rs.getObject("branch_id", UUID.class),
                        Language.fromTag(rs.getString("language")).orElse(Language.DEFAULT),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()))
                .list()
                .stream()
                .filter(view -> owner || (view.branchId() != null && managed.contains(view.branchId())))
                .toList();
    }

    public void revoke(AdminPrincipal principal, UUID invitationId) {
        InvitationView view = pending(principal).stream()
                .filter(invitation -> invitation.id().equals(invitationId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        TeamPermissions.requireCanGrant(principal, view.role(), view.branchId());
        jdbc.sql("UPDATE staff_invitations SET revoked_at = :now WHERE id = :id AND revoked_at IS NULL")
                .param("now", ts(clock.instant()))
                .param("id", invitationId)
                .update();
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
            jdbc.sql("""
                            INSERT INTO staff_roles (id, staff_user_id, role, branch_id, created_at)
                            VALUES (:id, :userId, :role, :branchId, :now)""")
                    .param("id", Ids.newId())
                    .param("userId", id)
                    .param("role", invitation.role().name())
                    .param("branchId", invitation.branchId())
                    .param("now", ts(now))
                    .update();
            jdbc.sql("UPDATE staff_invitations SET accepted_at = :now WHERE id = :id")
                    .param("now", ts(now))
                    .param("id", invitation.id())
                    .update();
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
            jdbc.sql("""
                            UPDATE staff_invitations SET revoked_at = :now
                            WHERE email = :email AND accepted_at IS NULL AND revoked_at IS NULL""")
                    .param("now", ts(now))
                    .param("email", normalized)
                    .update();
            jdbc.sql("""
                            INSERT INTO staff_invitations (id, email, role, branch_id, token_hash, expires_at, invited_by,
                                                           created_at, language)
                            VALUES (:id, :email, :role, :branchId, :hash, :expiresAt, :invitedBy, :now, :lang)""")
                    .param("id", id)
                    .param("email", normalized)
                    .param("role", role.name())
                    .param("branchId", branchId)
                    .param("hash", SecureTokens.hash(token))
                    .param("expiresAt", ts(expiresAt))
                    .param("invitedBy", invitedBy)
                    .param("now", ts(now))
                    .param("lang", language.tag())
                    .update();
        });
        // Token URL fraqmentindədir (#): server loglarına və Referer başlığına düşmür
        String link = realms.baseUrl(AdminRealm.STAFF) + "/admin/invitation#token=" + token;
        MailMessage message = templates.invitation(normalized, language, companyName(language), link,
                properties.invitationTtl().toHours(), TenantContext.require().id().toString());
        mailExecutor.execute(() -> mail.send(message));
        return new InvitationView(id, normalized, role, branchId, language, expiresAt, now);
    }

    private Optional<OpenInvitation> findOpen(String token, boolean lock) {
        return jdbc.sql("""
                        SELECT id, email, role, branch_id, language FROM staff_invitations
                        WHERE token_hash = :hash AND accepted_at IS NULL AND revoked_at IS NULL AND expires_at > :now"""
                        + (lock ? " FOR UPDATE" : ""))
                .param("hash", SecureTokens.hash(token == null ? "" : token))
                .param("now", ts(clock.instant()))
                .query((rs, n) -> new OpenInvitation(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        AdminRole.valueOf(rs.getString("role")),
                        rs.getObject("branch_id", UUID.class),
                        Language.fromTag(rs.getString("language")).orElse(Language.DEFAULT)))
                .optional();
    }

    private boolean activeBranchExists(UUID branchId) {
        return jdbc.sql("SELECT count(*) FROM branches WHERE id = :id AND status = 'ACTIVE'")
                .param("id", branchId).query(Integer.class).single() > 0;
    }

    private String branchName(UUID branchId, Language language) {
        return jdbc.sql("SELECT name::text FROM branches WHERE id = :id")
                .param("id", branchId)
                .query(String.class)
                .optional()
                .map(json -> TEXT.convertToEntityAttribute(json).resolve(language))
                .orElse(null);
    }

    private String companyName(Language language) {
        return jdbc.sql("SELECT name::text FROM company_profile WHERE singleton")
                .query(String.class)
                .optional()
                .map(json -> TEXT.convertToEntityAttribute(json).resolve(language))
                .orElse(TenantContext.require().slug());
    }

    private static OffsetDateTime ts(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
