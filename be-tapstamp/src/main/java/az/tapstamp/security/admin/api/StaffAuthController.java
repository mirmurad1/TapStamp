package az.tapstamp.security.admin.api;

import az.tapstamp.security.admin.AdminAuthService;
import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.security.admin.AdminSessionCookies;
import az.tapstamp.shared.web.ClientInfo;
import az.tapstamp.security.admin.api.AdminAuthDtos.LoginResponse;
import az.tapstamp.staff.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** İşçilərin girişi və dəvətin qəbulu — yalnız şirkətin öz domenində işləyir. */
@RestController
@RequestMapping("/api/v1/admin/auth")
@Tag(name = "Admin: autentifikasiya")
public class StaffAuthController extends AdminAuthEndpoints {

    private final AdminAuthService auth;
    private final AdminSessionCookies cookies;
    private final InvitationService invitations;

    public StaffAuthController(AdminAuthService auth, AdminSessionCookies cookies, InvitationService invitations) {
        super(auth, cookies);
        this.auth = auth;
        this.cookies = cookies;
        this.invitations = invitations;
    }

    @Override
    protected AdminRealm realm() {
        return AdminRealm.STAFF;
    }

    public record InvitationTokenRequest(@NotBlank @Size(max = 128) String token) {
        @Override
        public @NonNull String toString() {
            return "InvitationTokenRequest[***]";
        }
    }

    public record AcceptInvitationRequest(
            @NotBlank @Size(max = 128) String token,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Size(max = 128) String password) {
        @Override
        public @NonNull String toString() {
            return "AcceptInvitationRequest[***]";
        }
    }

    @Schema(description = "Dəvətin məzmunu (qəbul səhifəsində göstərilir)")
    public record InvitationDetailsResponse(String email, String role, String branchName, String companyName,
                                            String language) {
    }

    @PostMapping("/invitations/inspect")
    @Operation(summary = "Dəvət linkinin yoxlanılması",
            description = "Token POST body-də göndərilir (URL-də yox). Etibarsız, vaxtı keçmiş və ya istifadə olunmuş token üçün `TOKEN_INVALID`.")
    InvitationDetailsResponse inspect(@Valid @RequestBody InvitationTokenRequest request, HttpServletRequest http) {
        var details = invitations.inspect(request.token(), ClientInfo.of(http));
        return new InvitationDetailsResponse(details.email(), details.role().name(), details.branchName(),
                details.companyName(), details.language().tag());
    }

    @PostMapping("/invitations/accept")
    @Operation(summary = "Dəvətin qəbulu",
            description = "Hesab yaradılır və istifadəçi daxil olur. Sahib üçün növbəti addım TOTP qeydiyyatıdır.")
    LoginResponse accept(@Valid @RequestBody AcceptInvitationRequest request, HttpServletRequest http,
                         HttpServletResponse response) {
        ClientInfo client = ClientInfo.of(http);
        var userId = invitations.accept(request.token(), request.displayName(), request.password(), client);
        var result = auth.signIn(AdminRealm.STAFF, userId, client);
        cookies.write(response, AdminRealm.STAFF, result.session());
        return new LoginResponse(result.status());
    }
}
