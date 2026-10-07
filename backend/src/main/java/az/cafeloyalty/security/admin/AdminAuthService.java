package az.cafeloyalty.security.admin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import az.cafeloyalty.notification.mail.MailMessage;
import az.cafeloyalty.notification.mail.MailSender;
import az.cafeloyalty.notification.mail.MailTemplates;
import az.cafeloyalty.security.crypto.KeyPurpose;
import az.cafeloyalty.security.password.PasswordHasher;
import az.cafeloyalty.security.password.PasswordPolicy;
import az.cafeloyalty.security.ratelimit.RateLimitPolicy;
import az.cafeloyalty.security.ratelimit.RateLimiter;
import az.cafeloyalty.security.token.SecureTokens;
import az.cafeloyalty.security.totp.Base32;
import az.cafeloyalty.security.totp.TotpService;
import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.shared.pii.EmailMasker;
import az.cafeloyalty.shared.web.ClientInfo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * İşçi və platforma adminlərinin autentifikasiyası (realm-dən asılı olmayan məntiq).
 *
 * <ul>
 *   <li>Argon2id, artan gözləmə ilə bloklanma (5 uğursuz cəhddən sonra 1 → 2 → 4 … 60 dəq);</li>
 *   <li>mövcud olmayan email, bloklanmış və aktiv olmayan hesab üçün eyni cavab və eyni hesablama vaxtı;</li>
 *   <li>TOTP (OWNER və SUPER_ADMIN üçün məcburi), bərpa kodları, kodun təkrar istifadəsinin qarşısı;</li>
 *   <li>vəziyyət dəyişəndə sessiya tokeninin yenilənməsi (session fixation-ın qarşısı);</li>
 *   <li>şifrə bərpası: həmişə eyni cavab, birdəfəlik və 30 dəqiqəlik link, bərpadan sonra bütün sessiyalar ləğv olunur.</li>
 * </ul>
 */
@Service
public class AdminAuthService {

    public enum LoginStatus {
        AUTHENTICATED,
        MFA_REQUIRED,
        MFA_ENROLLMENT_REQUIRED
    }

    /** Yeni və ya yenilənmiş sessiya: token yalnız cookie-də göndərilir. */
    public record IssuedSession(String token, AdminSession.State state, Duration maxAge) {
        @Override
        public String toString() {
            return "IssuedSession[state=" + state + "]";
        }
    }

    public record LoginResult(LoginStatus status, IssuedSession session) {
    }

    public record EnrollmentStart(String secret, String otpauthUri) {
    }

    public record EnrollmentResult(List<String> recoveryCodes, IssuedSession session) {
    }

    private static final int RECOVERY_CODE_COUNT = 10;

    private final AdminRealms realms;
    private final PasswordHasher hasher;
    private final PasswordPolicy passwordPolicy;
    private final TotpService totp;
    private final RateLimiter rateLimiter;
    private final MailSender mail;
    private final MailTemplates templates;
    private final TaskExecutor mailExecutor;
    private final AdminAuthProperties properties;
    private final Clock clock;

    public AdminAuthService(AdminRealms realms, PasswordHasher hasher, PasswordPolicy passwordPolicy, TotpService totp,
                            RateLimiter rateLimiter, MailSender mail, MailTemplates templates,
                            @Qualifier("applicationTaskExecutor") TaskExecutor mailExecutor,
                            AdminAuthProperties properties, Clock clock) {
        this.realms = realms;
        this.hasher = hasher;
        this.passwordPolicy = passwordPolicy;
        this.totp = totp;
        this.rateLimiter = rateLimiter;
        this.mail = mail;
        this.templates = templates;
        this.mailExecutor = mailExecutor;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ Giriş

    public LoginResult login(AdminRealm realm, String email, String password, ClientInfo client) {
        Instant now = clock.instant();
        String scope = realms.rateLimitScope(realm);
        rateLimiter.consume(RateLimitPolicy.ADMIN_LOGIN_PER_IP, scope, client.ip());
        AdminAccountStore accounts = realms.accounts(realm);

        Optional<AdminAccount> found = accounts.findByEmail(email);
        if (found.isEmpty() || found.get().status() != AdminAccount.AccountStatus.ACTIVE) {
            // Mövcud olmayan və aktiv olmayan hesab: eyni hesablama vaxtı və eyni bloklanma davranışı
            hasher.simulateVerification(password);
            if (!rateLimiter.tryConsume(RateLimitPolicy.ADMIN_LOGIN_FAILURE_PER_EMAIL, scope,
                    AdminAccountStore.normalizeEmail(email))) {
                throw new ApiException(ErrorCode.LOGIN_TEMPORARILY_BLOCKED);
            }
            realms.audit(realm, "ADMIN_LOGIN_FAILED", null, "account", null, Map.of(
                    "email", EmailMasker.mask(email),
                    "reason", found.isEmpty() ? "UNKNOWN_ACCOUNT" : "ACCOUNT_" + found.get().status()));
            throw new ApiException(ErrorCode.LOGIN_FAILED);
        }

        AdminAccount account = found.get();
        if (account.lockedAt(now)) {
            hasher.simulateVerification(password);
            realms.audit(realm, "ADMIN_LOGIN_BLOCKED", account.id(), "account", account.id(), Map.of());
            throw new ApiException(ErrorCode.LOGIN_TEMPORARILY_BLOCKED);
        }
        if (!hasher.matches(password, account.passwordHash())) {
            registerFailure(realm, account, now, "BAD_PASSWORD");
            throw new ApiException(ErrorCode.LOGIN_FAILED);
        }
        if (hasher.needsRehash(account.passwordHash())) {
            accounts.updatePasswordHash(account.id(), hasher.hash(password));
        }
        accounts.recordSuccessfulLogin(account.id(), now);

        Set<RoleGrant> roles = accounts.loadRoles(account.id());
        AdminSession.State state = account.totpEnabled() ? AdminSession.State.PENDING_MFA
                : mfaMandatory(realm, roles) ? AdminSession.State.MFA_ENROLLMENT : AdminSession.State.ACTIVE;
        IssuedSession session = openSession(realm, account.id(), state, client, now);
        realms.audit(realm, state == AdminSession.State.ACTIVE ? "ADMIN_LOGIN_SUCCEEDED" : "ADMIN_PASSWORD_VERIFIED",
                account.id(), "account", account.id(), Map.of("state", state.name()));
        return new LoginResult(statusOf(state), session);
    }

    /** Dəvət qəbul olunandan sonra birbaşa daxil olmaq üçün. */
    public LoginResult signIn(AdminRealm realm, UUID userId, ClientInfo client) {
        Instant now = clock.instant();
        Set<RoleGrant> roles = realms.accounts(realm).loadRoles(userId);
        AdminSession.State state = mfaMandatory(realm, roles) ? AdminSession.State.MFA_ENROLLMENT : AdminSession.State.ACTIVE;
        realms.accounts(realm).recordSuccessfulLogin(userId, now);
        return new LoginResult(statusOf(state), openSession(realm, userId, state, client, now));
    }

    public IssuedSession verifyMfa(AdminPrincipal principal, String code) {
        requireState(principal, AdminSession.State.PENDING_MFA);
        Instant now = clock.instant();
        AdminAccount account = account(principal);
        if (account.lockedAt(now)) {
            throw new ApiException(ErrorCode.LOGIN_TEMPORARILY_BLOCKED);
        }
        if (!checkSecondFactor(principal.realm(), account, code, true, now)) {
            onMfaFailure(principal, account, now);
        }
        // Uğurlu ikinci faktor uğursuz cəhdlərin sayğacını sıfırlayır
        realms.accounts(principal.realm()).recordSuccessfulLogin(account.id(), now);
        IssuedSession session = rotate(principal, AdminSession.State.ACTIVE, now);
        realms.audit(principal.realm(), "ADMIN_LOGIN_SUCCEEDED", account.id(), "account", account.id(),
                Map.of("mfa", true));
        return session;
    }

    // ------------------------------------------------------------------ TOTP qeydiyyatı

    public EnrollmentStart startEnrollment(AdminPrincipal principal) {
        AdminAccount account = account(principal);
        if (account.totpEnabled()) {
            throw new ApiException(ErrorCode.MFA_ALREADY_ENABLED);
        }
        byte[] secret = totp.generateSecret();
        byte[] encrypted = realms.cipher(principal.realm())
                .encrypt(KeyPurpose.TOTP, secret, realms.totpAad(principal.realm(), account.id()));
        realms.accounts(principal.realm()).storePendingTotp(account.id(), encrypted, clock.instant());
        return new EnrollmentStart(Base32.encode(secret),
                totp.otpauthUri(realms.issuer(principal.realm()), account.email(), secret));
    }

    public EnrollmentResult confirmEnrollment(AdminPrincipal principal, String code) {
        Instant now = clock.instant();
        AdminAccount account = account(principal);
        if (account.totpEnabled()) {
            throw new ApiException(ErrorCode.MFA_ALREADY_ENABLED);
        }
        if (account.totpSecretEnc() == null) {
            throw new ApiException(ErrorCode.MFA_INVALID);
        }
        OptionalLong step = totp.verify(decryptSecret(principal.realm(), account), code, null);
        if (step.isEmpty()) {
            onMfaFailure(principal, account, now);
        }
        List<String> codes = recoveryCodes();
        AdminAccountStore accounts = realms.accounts(principal.realm());
        IssuedSession session = realms.tx(principal.realm()).execute(status -> {
            accounts.enableTotp(account.id(), step.getAsLong(), now);
            accounts.replaceRecoveryCodes(account.id(), codes.stream().map(SecureTokens::hash).toList(), now);
            return rotate(principal, AdminSession.State.ACTIVE, now);
        });
        realms.audit(principal.realm(), "ADMIN_MFA_ENABLED", account.id(), "account", account.id(), Map.of());
        return new EnrollmentResult(codes, session);
    }

    public List<String> regenerateRecoveryCodes(AdminPrincipal principal) {
        requireStepUp(principal);
        List<String> codes = recoveryCodes();
        realms.accounts(principal.realm()).replaceRecoveryCodes(principal.userId(),
                codes.stream().map(SecureTokens::hash).toList(), clock.instant());
        realms.audit(principal.realm(), "ADMIN_RECOVERY_CODES_REGENERATED", principal.userId(), "account",
                principal.userId(), Map.of());
        return codes;
    }

    // ------------------------------------------------------------------ Step-up

    /** Həssas əməliyyatdan əvvəl kimliyin TOTP ilə yenidən təsdiqi. */
    public void stepUp(AdminPrincipal principal, String code) {
        requireState(principal, AdminSession.State.ACTIVE);
        Instant now = clock.instant();
        AdminAccount account = account(principal);
        if (!account.totpEnabled()) {
            throw new ApiException(ErrorCode.STEP_UP_REQUIRED);
        }
        if (!checkSecondFactor(principal.realm(), account, code, false, now)) {
            onMfaFailure(principal, account, now);
        }
        realms.sessions(principal.realm()).markStepUp(principal.sessionId(), now);
        realms.audit(principal.realm(), "ADMIN_STEP_UP", account.id(), "session", principal.sessionId(), Map.of());
    }

    public void requireStepUp(AdminPrincipal principal) {
        Instant stepUpAt = principal.stepUpAt();
        if (stepUpAt == null || stepUpAt.plus(properties.stepUpValidity()).isBefore(clock.instant())) {
            throw new ApiException(ErrorCode.STEP_UP_REQUIRED);
        }
    }

    // ------------------------------------------------------------------ Çıxış və sessiyalar

    public void logout(AdminPrincipal principal) {
        realms.sessions(principal.realm()).revoke(principal.sessionId(), "LOGOUT", clock.instant());
        realms.audit(principal.realm(), "ADMIN_LOGOUT", principal.userId(), "session", principal.sessionId(), Map.of());
    }

    public List<AdminSession> sessions(AdminPrincipal principal) {
        return realms.sessions(principal.realm()).listActive(principal.userId(), clock.instant());
    }

    public void revokeSession(AdminPrincipal principal, UUID sessionId) {
        boolean own = sessions(principal).stream().anyMatch(session -> session.id().equals(sessionId));
        if (!own) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        realms.sessions(principal.realm()).revoke(sessionId, "REVOKED_BY_USER", clock.instant());
        realms.audit(principal.realm(), "ADMIN_SESSION_REVOKED", principal.userId(), "session", sessionId, Map.of());
    }

    public void updateProfile(AdminPrincipal principal, String displayName, Language language) {
        realms.accounts(principal.realm()).updateProfile(principal.userId(), displayName, language, clock.instant());
    }

    // ------------------------------------------------------------------ Şifrə

    public IssuedSession changePassword(AdminPrincipal principal, String currentPassword, String newPassword,
                                        ClientInfo client) {
        requireState(principal, AdminSession.State.ACTIVE);
        Instant now = clock.instant();
        AdminAccount account = account(principal);
        if (!hasher.matches(currentPassword, account.passwordHash())) {
            registerFailure(principal.realm(), account, now, "BAD_CURRENT_PASSWORD");
            throw new ApiException(ErrorCode.CURRENT_PASSWORD_INVALID);
        }
        checkPolicy(newPassword, account.email());
        AdminAccountStore accounts = realms.accounts(principal.realm());
        IssuedSession session = realms.tx(principal.realm()).execute(status -> {
            accounts.updatePassword(account.id(), hasher.hash(newPassword), now);
            realms.sessions(principal.realm()).revokeAll(account.id(), "PASSWORD_CHANGED", now, principal.sessionId());
            return rotate(principal, AdminSession.State.ACTIVE, now);
        });
        sendMail(templates.passwordChanged(account.email(), account.preferredLanguage(),
                realms.tenantIdOrNull(principal.realm())));
        realms.audit(principal.realm(), "ADMIN_PASSWORD_CHANGED", account.id(), "account", account.id(), Map.of());
        return session;
    }

    /** Həmişə eyni cavab: hesabın mövcud olub-olmaması açıqlanmır, email arxa planda göndərilir. */
    public void requestPasswordReset(AdminRealm realm, String email, ClientInfo client) {
        Instant now = clock.instant();
        String scope = realms.rateLimitScope(realm);
        rateLimiter.consume(RateLimitPolicy.PASSWORD_RESET_PER_IP, scope, client.ip());
        if (!rateLimiter.tryConsume(RateLimitPolicy.PASSWORD_RESET_PER_EMAIL, scope, AdminAccountStore.normalizeEmail(email))) {
            return;
        }
        Optional<AdminAccount> account = realms.accounts(realm).findByEmail(email)
                .filter(a -> a.status() == AdminAccount.AccountStatus.ACTIVE);
        if (account.isEmpty()) {
            return;
        }
        String token = SecureTokens.generate();
        realms.accounts(realm).createResetToken(account.get().id(), SecureTokens.hash(token),
                now.plus(properties.passwordResetTtl()), client.ip(), now);
        // Token URL fraqmentindədir (#): server loglarına və Referer başlığına düşmür
        String link = realms.baseUrl(realm) + "/admin/reset-password#token=" + token;
        sendMail(templates.passwordReset(account.get().email(), account.get().preferredLanguage(), link,
                properties.passwordResetTtl().toMinutes(), realms.tenantIdOrNull(realm)));
        realms.audit(realm, "ADMIN_PASSWORD_RESET_REQUESTED", account.get().id(), "account", account.get().id(), Map.of());
    }

    public void resetPassword(AdminRealm realm, String token, String newPassword, ClientInfo client) {
        Instant now = clock.instant();
        rateLimiter.consume(RateLimitPolicy.TOKEN_REDEEM_PER_IP, realms.rateLimitScope(realm), client.ip());
        AdminAccountStore accounts = realms.accounts(realm);
        UUID userId = realms.tx(realm).execute(status -> {
            UUID id = accounts.consumeResetToken(SecureTokens.hash(token == null ? "" : token), now)
                    .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
            AdminAccount account = accounts.findById(id).orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
            // Siyasət pozulubsa tranzaksiya geri qaytarılır və token istifadə olunmamış qalır
            checkPolicy(newPassword, account.email());
            accounts.updatePassword(id, hasher.hash(newPassword), now);
            realms.sessions(realm).revokeAll(id, "PASSWORD_RESET", now, null);
            return id;
        });
        AdminAccount account = accounts.findById(userId).orElseThrow();
        sendMail(templates.passwordChanged(account.email(), account.preferredLanguage(), realms.tenantIdOrNull(realm)));
        realms.audit(realm, "ADMIN_PASSWORD_RESET_COMPLETED", userId, "account", userId, Map.of());
    }

    public void checkPolicy(String password, String email) {
        List<PasswordPolicy.Violation> violations = passwordPolicy.check(password, email);
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY_VIOLATION, new Object[0],
                    Map.of("violations", violations.stream().map(Enum::name).toList()));
        }
    }

    // ------------------------------------------------------------------ Daxili köməkçilər

    private boolean mfaMandatory(AdminRealm realm, Set<RoleGrant> roles) {
        return realm == AdminRealm.PLATFORM || roles.stream().anyMatch(r -> r.role() == AdminRole.COMPANY_OWNER);
    }

    private IssuedSession openSession(AdminRealm realm, UUID userId, AdminSession.State state, ClientInfo client,
                                      Instant now) {
        String token = SecureTokens.generate();
        Duration ttl = state == AdminSession.State.ACTIVE ? properties.sessionAbsolute() : properties.pendingMfaTtl();
        realms.sessions(realm).create(userId, SecureTokens.hash(token), state, now, now.plus(ttl), null,
                client.ip(), client.userAgent());
        return new IssuedSession(token, state, ttl);
    }

    private IssuedSession rotate(AdminPrincipal principal, AdminSession.State state, Instant now) {
        String token = SecureTokens.generate();
        Duration ttl = state == AdminSession.State.ACTIVE ? properties.sessionAbsolute() : properties.pendingMfaTtl();
        Instant mfaVerifiedAt = state == AdminSession.State.ACTIVE && principal.state() != AdminSession.State.ACTIVE
                ? now : null;
        realms.sessions(principal.realm()).rotate(principal.sessionId(), SecureTokens.hash(token), state,
                now.plus(ttl), mfaVerifiedAt, now);
        return new IssuedSession(token, state, ttl);
    }

    private boolean checkSecondFactor(AdminRealm realm, AdminAccount account, String code, boolean allowRecoveryCode,
                                      Instant now) {
        if (code == null) {
            return false;
        }
        String trimmed = code.trim();
        AdminAccountStore accounts = realms.accounts(realm);
        if (trimmed.matches("^\\d{6}$")) {
            OptionalLong step = totp.verify(decryptSecret(realm, account), trimmed, account.totpLastUsedStep());
            return step.isPresent() && accounts.advanceTotpStep(account.id(), step.getAsLong());
        }
        if (allowRecoveryCode && accounts.useRecoveryCode(account.id(),
                SecureTokens.hash(SecureTokens.normalizeRecoveryCode(trimmed)), now)) {
            realms.audit(realm, "ADMIN_RECOVERY_CODE_USED", account.id(), "account", account.id(),
                    Map.of("remaining", accounts.remainingRecoveryCodes(account.id())));
            return true;
        }
        return false;
    }

    private void onMfaFailure(AdminPrincipal principal, AdminAccount account, Instant now) {
        int failures = realms.sessions(principal.realm()).recordMfaFailure(principal.sessionId());
        registerFailure(principal.realm(), account, now, "BAD_MFA_CODE");
        if (failures >= properties.maxMfaFailures()) {
            realms.sessions(principal.realm()).revoke(principal.sessionId(), "MFA_FAILURES", now);
        }
        throw new ApiException(ErrorCode.MFA_INVALID);
    }

    private void registerFailure(AdminRealm realm, AdminAccount account, Instant now, String reason) {
        int count = realms.accounts(realm).incrementFailedLogins(account.id(), now);
        int threshold = properties.lockoutThreshold();
        if (count >= threshold) {
            long minutes = Math.min(1L << Math.min(count - threshold, 10), properties.maxLockout().toMinutes());
            realms.accounts(realm).lockUntil(account.id(), now.plus(Duration.ofMinutes(minutes)));
            realms.audit(realm, "ADMIN_ACCOUNT_LOCKED", account.id(), "account", account.id(),
                    Map.of("minutes", minutes, "failures", count));
        }
        realms.audit(realm, "ADMIN_LOGIN_FAILED", account.id(), "account", account.id(), Map.of("reason", reason));
    }

    private byte[] decryptSecret(AdminRealm realm, AdminAccount account) {
        return realms.cipher(realm).decrypt(account.totpSecretEnc(), realms.totpAad(realm, account.id()));
    }

    private AdminAccount account(AdminPrincipal principal) {
        return realms.accounts(principal.realm()).findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    private static void requireState(AdminPrincipal principal, AdminSession.State state) {
        if (principal.state() != state) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }

    private static LoginStatus statusOf(AdminSession.State state) {
        return switch (state) {
            case ACTIVE -> LoginStatus.AUTHENTICATED;
            case PENDING_MFA -> LoginStatus.MFA_REQUIRED;
            case MFA_ENROLLMENT -> LoginStatus.MFA_ENROLLMENT_REQUIRED;
        };
    }

    private static List<String> recoveryCodes() {
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            codes.add(SecureTokens.recoveryCode());
        }
        return codes;
    }

    /** Email arxa planda göndərilir: cavab vaxtı hesabın mövcudluğundan və SMTP-nin sürətindən asılı olmasın. */
    private void sendMail(MailMessage message) {
        mailExecutor.execute(() -> mail.send(message));
    }
}
