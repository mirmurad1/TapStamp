package az.cafeloyalty.security.admin.api;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import az.cafeloyalty.config.AppProperties;
import az.cafeloyalty.security.admin.AdminAuthService;
import az.cafeloyalty.security.admin.AdminPrincipal;
import az.cafeloyalty.security.admin.AdminRealm;
import az.cafeloyalty.security.admin.AdminRealms;
import az.cafeloyalty.security.admin.AdminSessionCookies;
import az.cafeloyalty.security.admin.ClientInfo;
import az.cafeloyalty.security.admin.api.AdminAuthDtos.ChangePasswordRequest;
import az.cafeloyalty.security.admin.api.AdminAuthDtos.MeResponse;
import az.cafeloyalty.security.admin.api.AdminAuthDtos.RecoveryCodesResponse;
import az.cafeloyalty.security.admin.api.AdminAuthDtos.RoleResponse;
import az.cafeloyalty.security.admin.api.AdminAuthDtos.SessionResponse;
import az.cafeloyalty.security.admin.api.AdminAuthDtos.UpdateProfileRequest;
import az.cafeloyalty.shared.i18n.Language;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Admin hesabının özü: profil, dil, şifrə, sessiyalar, bərpa kodları. */
public abstract class AdminAccountEndpoints {

    private final AdminAuthService auth;
    private final AdminRealms realms;
    private final AdminSessionCookies cookies;
    private final AppProperties appProperties;

    protected AdminAccountEndpoints(AdminAuthService auth, AdminRealms realms, AdminSessionCookies cookies,
                                    AppProperties appProperties) {
        this.auth = auth;
        this.realms = realms;
        this.cookies = cookies;
        this.appProperties = appProperties;
    }

    protected abstract AdminRealm realm();

    @GetMapping("/me")
    @Operation(summary = "Daxil olmuş adminin profili", description = "Rollar, filiallar, dil və 2FA vəziyyəti.")
    MeResponse me(@AuthenticationPrincipal AdminPrincipal principal) {
        return toResponse(principal);
    }

    @PatchMapping("/me")
    @Operation(summary = "Profilin yenilənməsi", description = "Ad və interfeys dili; dil dərhal bütün cihazlarda tətbiq olunur.")
    MeResponse update(@Valid @RequestBody UpdateProfileRequest request, @AuthenticationPrincipal AdminPrincipal principal) {
        Language language = request.preferredLanguage() == null ? null
                : Language.fromTag(request.preferredLanguage()).orElse(null);
        auth.updateProfile(principal, request.displayName(), language);
        var account = realms.accounts(realm()).findById(principal.userId()).orElseThrow();
        return toResponse(new AdminPrincipal(principal.realm(), principal.userId(), principal.sessionId(),
                account.email(), account.displayName(), account.preferredLanguage(), principal.state(),
                principal.roles(), principal.totpEnabled(), principal.stepUpAt()));
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Şifrənin dəyişdirilməsi", description = "Digər bütün sessiyalar ləğv olunur, cari sessiyanın tokeni yenilənir.")
    void changePassword(@Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal AdminPrincipal principal,
                        HttpServletRequest http, HttpServletResponse response) {
        var session = auth.changePassword(principal, request.currentPassword(), request.newPassword(), ClientInfo.of(http));
        cookies.write(response, realm(), session);
    }

    @GetMapping("/me/sessions")
    @Operation(summary = "Aktiv sessiyalar (cihazlar)")
    List<SessionResponse> sessions(@AuthenticationPrincipal AdminPrincipal principal) {
        return auth.sessions(principal).stream()
                .map(s -> new SessionResponse(s.id(), s.createdAt(), s.lastActivityAt(), s.ip(), s.userAgent(),
                        s.id().equals(principal.sessionId())))
                .toList();
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sessiyanın ləğvi (cihazdan çıxış)")
    void revokeSession(@PathVariable UUID sessionId, @AuthenticationPrincipal AdminPrincipal principal) {
        auth.revokeSession(principal, sessionId);
    }

    @PostMapping("/me/mfa/recovery-codes")
    @Operation(summary = "Yeni bərpa kodları", description = "Köhnə kodlar etibarsız olur. Step-up (son 10 dəqiqədə TOTP) tələb edir.")
    RecoveryCodesResponse regenerateRecoveryCodes(@AuthenticationPrincipal AdminPrincipal principal) {
        return new RecoveryCodesResponse(auth.regenerateRecoveryCodes(principal));
    }

    private MeResponse toResponse(AdminPrincipal principal) {
        List<RoleResponse> roles = principal.roles().stream()
                .sorted(Comparator.comparing(grant -> grant.role().ordinal()))
                .map(grant -> new RoleResponse(grant.role().name(), grant.branchId()))
                .toList();
        int remaining = principal.totpEnabled()
                ? realms.accounts(realm()).remainingRecoveryCodes(principal.userId()) : 0;
        return new MeResponse(principal.userId(), principal.email(), principal.displayName(),
                principal.language().tag(), realm().name(), roles, principal.totpEnabled(), remaining,
                new MeResponse.Features(appProperties.dev().simulatorEnabled()));
    }
}
