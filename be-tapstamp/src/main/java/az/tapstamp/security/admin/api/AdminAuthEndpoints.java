package az.tapstamp.security.admin.api;

import az.tapstamp.security.admin.AdminAuthService;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.security.admin.AdminSessionCookies;
import az.tapstamp.shared.web.ClientInfo;
import az.tapstamp.security.admin.api.AdminAuthDtos.CodeRequest;
import az.tapstamp.security.admin.api.AdminAuthDtos.EnrollmentStartResponse;
import az.tapstamp.security.admin.api.AdminAuthDtos.LoginRequest;
import az.tapstamp.security.admin.api.AdminAuthDtos.LoginResponse;
import az.tapstamp.security.admin.api.AdminAuthDtos.PasswordForgotRequest;
import az.tapstamp.security.admin.api.AdminAuthDtos.PasswordResetRequest;
import az.tapstamp.security.admin.api.AdminAuthDtos.RecoveryCodesResponse;
import az.tapstamp.security.admin.api.AdminAuthDtos.SessionStateResponse;
import az.tapstamp.security.admin.api.AdminAuthDtos.StepUpRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Giriş, MFA, step-up və şifrə bərpası — işçilər ({@code /api/v1/admin/auth}) və platforma adminləri
 * ({@code /api/v1/platform/auth}) üçün eyni endpoint-lər; alt siniflər yalnız realm-i və yolu təyin edir.
 */
public abstract class AdminAuthEndpoints {

    private final AdminAuthService auth;
    private final AdminSessionCookies cookies;

    protected AdminAuthEndpoints(AdminAuthService auth, AdminSessionCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    protected abstract AdminRealm realm();

    @GetMapping("/session")
    @Operation(summary = "Sessiyanın vəziyyəti", description = """
            Frontend səhifə yenilənəndə hansı addımı göstərəcəyini bununla bilir (giriş, TOTP, TOTP qeydiyyatı və ya panel). \
            Sessiya yoxdursa və ya bitibsə NONE qaytarır.""")
    SessionStateResponse session(@AuthenticationPrincipal AdminPrincipal principal) {
        return new SessionStateResponse(principal == null ? "NONE" : principal.state().name());
    }

    @PostMapping("/login")
    @Operation(summary = "Email və şifrə ilə giriş", description = """
            Uğurlu olarsa HttpOnly sessiya cookie-si qoyulur. Status `MFA_REQUIRED` olarsa TOTP kodu tələb olunur; \
            2FA (iki addımlı yoxlama) hamı üçün könüllüdür. \
            Yanlış email və yanlış şifrə eyni cavabı alır (`LOGIN_FAILED`); 5 uğursuz cəhddən sonra müvəqqəti bloklanma.""")
    LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        var result = auth.login(realm(), request.email(), request.password(), ClientInfo.of(http));
        cookies.write(response, realm(), result.session());
        return new LoginResponse(result.status());
    }

    @PostMapping("/mfa/verify")
    @Operation(summary = "TOTP və ya bərpa kodu ilə girişin tamamlanması",
            description = "Uğurlu olarsa sessiya tokeni yenilənir. 5 yanlış koddan sonra sessiya ləğv olunur.")
    LoginResponse verifyMfa(@Valid @RequestBody CodeRequest request, @AuthenticationPrincipal AdminPrincipal principal,
                            HttpServletResponse response) {
        cookies.write(response, realm(), auth.verifyMfa(principal, request.code()));
        return new LoginResponse(AdminAuthService.LoginStatus.AUTHENTICATED);
    }

    @PostMapping("/mfa/enrollment")
    @Operation(summary = "TOTP qeydiyyatına başlamaq", description = "Yeni sirr yaradır; QR kod frontend-də göstərilir.")
    EnrollmentStartResponse startEnrollment(@AuthenticationPrincipal AdminPrincipal principal) {
        var start = auth.startEnrollment(principal);
        return new EnrollmentStartResponse(start.secret(), start.otpauthUri());
    }

    @PostMapping("/mfa/enrollment/confirm")
    @Operation(summary = "TOTP qeydiyyatının təsdiqi", description = "Tətbiqdəki kodla təsdiqlənir; 10 bərpa kodu bir dəfə qaytarılır.")
    RecoveryCodesResponse confirmEnrollment(@Valid @RequestBody CodeRequest request,
                                            @AuthenticationPrincipal AdminPrincipal principal,
                                            HttpServletResponse response) {
        var result = auth.confirmEnrollment(principal, request.code());
        cookies.write(response, realm(), result.session());
        return new RecoveryCodesResponse(result.recoveryCodes());
    }

    @PostMapping("/step-up")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Həssas əməliyyat üçün təkrar təsdiq",
            description = """
                    2FA aktivdirsə TOTP kodu, deyilsə cari şifrə ilə; 10 dəqiqə ərzində həssas əməliyyatlara \
                    (işçinin 2FA-sının sıfırlanması, bərpa kodları, şirkətin dayandırılması) icazə verir.""")
    void stepUp(@Valid @RequestBody StepUpRequest request, @AuthenticationPrincipal AdminPrincipal principal) {
        auth.stepUp(principal, request.code(), request.password());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Çıxış", description = "Cari sessiyanı ləğv edir və cookie-ni silir. Sessiya artıq bitibsə də 204 qaytarır.")
    void logout(@AuthenticationPrincipal AdminPrincipal principal, HttpServletResponse response) {
        if (principal != null) {
            auth.logout(principal);
        }
        cookies.clear(response, realm());
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Şifrə bərpası linki istəmək",
            description = "Həmişə 202 qaytarır — hesabın mövcud olub-olmadığı açıqlanmır. Link 30 dəqiqə etibarlıdır.")
    void forgotPassword(@Valid @RequestBody PasswordForgotRequest request, HttpServletRequest http) {
        auth.requestPasswordReset(realm(), request.email(), ClientInfo.of(http));
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Yeni şifrənin təyin edilməsi",
            description = "Birdəfəlik token ilə. Uğurlu olarsa istifadəçinin bütün sessiyaları ləğv olunur.")
    void resetPassword(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest http) {
        auth.resetPassword(realm(), request.token(), request.newPassword(), ClientInfo.of(http));
    }
}
