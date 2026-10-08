package az.cafeloyalty.security.admin.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.cafeloyalty.security.admin.AdminAuthService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin autentifikasiyası endpoint-lərinin sorğu və cavab modelləri. */
public final class AdminAuthDtos {

    private AdminAuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {
        @Override
        public String toString() {
            return "LoginRequest[email=***]";
        }
    }

    @Schema(description = "Girişin nəticəsi. Sessiya cookie-si HttpOnly-dir; token cavabda qaytarılmır.")
    public record LoginResponse(AdminAuthService.LoginStatus status) {
    }

    @Schema(description = "Cari sessiyanın vəziyyəti: NONE — daxil olmayıb, PENDING_MFA — TOTP gözlənilir, "
            + "MFA_ENROLLMENT — TOTP qurulmalıdır, ACTIVE — tam daxil olub")
    public record SessionStateResponse(String state) {
    }

    public record CodeRequest(
            @Schema(description = "6 rəqəmli TOTP kodu və ya bərpa kodu (xxxxx-xxxxx)")
            @NotBlank @Size(max = 32) String code) {
    }

    @Schema(description = "TOTP qeydiyyatı: sirr autentifikator tətbiqinə əl ilə və ya QR kod ilə əlavə olunur")
    public record EnrollmentStartResponse(String secret, String otpauthUri) {
    }

    @Schema(description = "Bərpa kodları yalnız bir dəfə göstərilir; hər biri bir dəfə istifadə oluna bilər")
    public record RecoveryCodesResponse(List<String> recoveryCodes) {
    }

    public record PasswordForgotRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record PasswordResetRequest(
            @NotBlank @Size(max = 128) String token,
            @NotBlank @Size(max = 128) String newPassword) {
        @Override
        public String toString() {
            return "PasswordResetRequest[***]";
        }
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {
        @Override
        public String toString() {
            return "ChangePasswordRequest[***]";
        }
    }

    @Schema(description = "2FA aktivdirsə code (TOTP), deyilsə password (cari şifrə) göndərilir")
    public record StepUpRequest(@Size(max = 32) String code, @Size(max = 128) String password) {
        @Override
        public String toString() {
            return "StepUpRequest[***]";
        }
    }

    public record PasswordConfirmRequest(@NotBlank @Size(max = 128) String password) {
        @Override
        public String toString() {
            return "PasswordConfirmRequest[***]";
        }
    }

    public record UpdateProfileRequest(
            @Size(min = 1, max = 80) String displayName,
            @Pattern(regexp = "az|en|ru") String preferredLanguage) {
    }

    public record RoleResponse(String role, UUID branchId) {
    }

    @Schema(description = "Daxil olmuş admin haqqında məlumat")
    public record MeResponse(
            UUID id,
            String email,
            String displayName,
            String preferredLanguage,
            @Schema(description = "STAFF — şirkət işçisi, PLATFORM — platforma admini") String realm,
            List<RoleResponse> roles,
            boolean totpEnabled,
            @Schema(description = "2FA müvəqqəti söndürülüb; eyni tətbiqin kodu ilə yenidən aktivləşdirilə bilər") boolean totpPaused,
            int recoveryCodesRemaining,
            Features features) {

        public record Features(@Schema(description = "Dev alətləri (simulyator, outbox) — yalnız dev mühitində") boolean devTools) {
        }
    }

    public record SessionResponse(UUID id, Instant createdAt, Instant lastActivityAt, String ip, String userAgent,
                                  boolean current) {
    }
}
