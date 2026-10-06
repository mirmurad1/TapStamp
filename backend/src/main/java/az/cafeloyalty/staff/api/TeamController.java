package az.cafeloyalty.staff.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.cafeloyalty.security.admin.AdminAccount;
import az.cafeloyalty.security.admin.AdminPrincipal;
import az.cafeloyalty.security.admin.AdminRole;
import az.cafeloyalty.security.admin.api.AdminAuthDtos.RoleResponse;
import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.staff.service.InvitationService;
import az.cafeloyalty.staff.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Komandanın idarə olunması — sahib və filial menecerləri üçün. */
@RestController
@RequestMapping("/api/v1/admin/team")
@PreAuthorize("hasAnyRole('COMPANY_OWNER', 'BRANCH_MANAGER')")
@Tag(name = "Admin: komanda")
public class TeamController {

    private final TeamService team;
    private final InvitationService invitations;

    public TeamController(TeamService team, InvitationService invitations) {
        this.team = team;
        this.invitations = invitations;
    }

    public record MemberResponse(UUID id, String email, String displayName, String status, List<RoleResponse> roles,
                                 boolean totpEnabled, Instant lastLoginAt) {
    }

    public record InvitationResponse(UUID id, String email, String role, UUID branchId, String language,
                                     Instant expiresAt, Instant createdAt) {

        static InvitationResponse from(InvitationService.InvitationView view) {
            return new InvitationResponse(view.id(), view.email(), view.role().name(), view.branchId(),
                    view.language().tag(), view.expiresAt(), view.createdAt());
        }
    }

    public record InviteRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull AdminRole role,
            UUID branchId,
            @NotBlank @Pattern(regexp = "az|en|ru") String language) {
    }

    public record MemberStatusRequest(@NotNull @Pattern(regexp = "ACTIVE|DISABLED") String status) {
    }

    @GetMapping
    @Operation(summary = "Komanda üzvləri", description = "Sahib hamını, menecer yalnız öz filiallarının işçilərini görür.")
    List<MemberResponse> members(@AuthenticationPrincipal AdminPrincipal principal) {
        return team.members(principal).stream()
                .map(member -> new MemberResponse(member.id(), member.email(), member.displayName(),
                        member.status().name(),
                        member.roles().stream().map(grant -> new RoleResponse(grant.role().name(), grant.branchId())).toList(),
                        member.totpEnabled(), member.lastLoginAt()))
                .toList();
    }

    @GetMapping("/invitations")
    @Operation(summary = "Gözləyən dəvətlər")
    List<InvitationResponse> invitations(@AuthenticationPrincipal AdminPrincipal principal) {
        return invitations.pending(principal).stream().map(InvitationResponse::from).toList();
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "İşçi dəvəti", description = """
            Dəvət emaili 72 saat etibarlı link ilə göndərilir. Sahib istənilən rolu, menecer yalnız öz filialına \
            barista (STAFF) dəvət edə bilər. MANAGER və STAFF üçün filial məcburidir, sahib üçün göstərilmir.""")
    InvitationResponse invite(@Valid @RequestBody InviteRequest request, @AuthenticationPrincipal AdminPrincipal principal) {
        Language language = Language.fromTag(request.language()).orElse(Language.DEFAULT);
        return InvitationResponse.from(
                invitations.invite(principal, request.email(), request.role(), request.branchId(), language));
    }

    @DeleteMapping("/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Dəvətin ləğvi")
    void revokeInvitation(@PathVariable UUID invitationId, @AuthenticationPrincipal AdminPrincipal principal) {
        invitations.revoke(principal, invitationId);
    }

    @PatchMapping("/members/{userId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "İşçinin bloklanması və ya aktivləşdirilməsi",
            description = "Bloklananda bütün sessiyaları dərhal ləğv olunur. Sonuncu aktiv sahib bloklana bilməz.")
    void updateStatus(@PathVariable UUID userId, @Valid @RequestBody MemberStatusRequest request,
                      @AuthenticationPrincipal AdminPrincipal principal) {
        team.updateStatus(principal, userId, AdminAccount.AccountStatus.valueOf(request.status()));
    }

    @PostMapping("/members/{userId}/mfa-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COMPANY_OWNER')")
    @Operation(summary = "İşçinin TOTP-sinin sıfırlanması",
            description = "Telefonunu itirmiş işçi üçün. Yalnız sahib; step-up (son 10 dəqiqədə TOTP) tələb edir.")
    void resetMfa(@PathVariable UUID userId, @AuthenticationPrincipal AdminPrincipal principal) {
        team.resetMfa(principal, userId);
    }
}
