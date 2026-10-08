package az.tapstamp.platform.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.tapstamp.platform.service.PlatformTenantService;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.tenancy.TenantStatus;
import az.tapstamp.tenancy.provisioning.ProvisionTenantCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Platforma paneli: şirkətlərin (tenant-ların) idarə olunması. Yalnız platforma domenində, SUPER_ADMIN üçün. */
@RestController
@RequestMapping("/api/v1/platform/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Platforma: şirkətlər")
public class PlatformTenantController {

    private final PlatformTenantService tenants;

    public PlatformTenantController(PlatformTenantService tenants) {
        this.tenants = tenants;
    }

    public record TenantResponse(UUID id, String slug, LocalizedText displayName, String status, String defaultLanguage,
                                 String timezone, String primaryDomain, List<String> domains) {

        static TenantResponse from(PlatformTenantService.TenantSummary summary) {
            return new TenantResponse(summary.id(), summary.slug(), summary.displayName(), summary.status().name(),
                    summary.defaultLanguage().tag(), summary.timezone(), summary.primaryDomain(), summary.domains());
        }
    }

    public record ProvisionRequest(
            @Schema(description = "Texniki ad (baza adında istifadə olunur): kiçik latın hərfləri, rəqəm, alt xətt")
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,30}$") String slug,
            @NotNull @Valid LocalizedText displayName,
            @Schema(description = "Şirkətin öz domeni, məs. bonus.mockcoffee.az")
            @NotBlank @Size(max = 253) @Pattern(regexp = "^[a-z0-9-]+(\\.[a-z0-9-]+)+$") String primaryDomain,
            @NotBlank @Pattern(regexp = "az|en|ru") String defaultLanguage,
            @NotBlank @Size(max = 64) String timezone) {
    }

    public record TenantStatusRequest(@NotNull @Pattern(regexp = "ACTIVE|SUSPENDED") String status) {
    }

    public record OwnerInvitationRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "az|en|ru") String language) {
    }

    public record OwnerInvitationResponse(UUID id, String email, Instant expiresAt) {
    }

    public record OwnerResponse(UUID id, String email, String displayName, String status, boolean totpEnabled,
                                Instant lastLoginAt) {
    }

    public record StatsResponse(int branches, long activeCustomers, int activeStaff) {
    }

    @GetMapping
    @Operation(summary = "Bütün şirkətlər")
    List<TenantResponse> list() {
        return tenants.list().stream().map(TenantResponse::from).toList();
    }

    @GetMapping("/{tenantId}")
    @Operation(summary = "Şirkət haqqında məlumat")
    TenantResponse get(@PathVariable UUID tenantId) {
        return TenantResponse.from(tenants.get(tenantId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Yeni şirkət yaratmaq", description = """
            Ayrıca baza və DB rolları yaradılır, miqrasiyalar işə salınır. Step-up tələb edir. \
            Prod-da standart olaraq söndürülüb (`APP_PROVISIONING_ENABLED`) — onda `PROVISIONING_DISABLED`.""")
    TenantResponse provision(@Valid @RequestBody ProvisionRequest request, @AuthenticationPrincipal AdminPrincipal principal) {
        ProvisionTenantCommand command = new ProvisionTenantCommand(request.slug(), request.displayName(),
                request.primaryDomain(), Language.fromTag(request.defaultLanguage()).orElse(Language.DEFAULT),
                request.timezone());
        return TenantResponse.from(tenants.provision(principal, command));
    }

    @PatchMapping("/{tenantId}/status")
    @Operation(summary = "Şirkəti dayandırmaq və ya bərpa etmək", description = "Step-up tələb edir.")
    TenantResponse updateStatus(@PathVariable UUID tenantId, @Valid @RequestBody TenantStatusRequest request,
                                @AuthenticationPrincipal AdminPrincipal principal) {
        return TenantResponse.from(tenants.updateStatus(principal, tenantId, TenantStatus.valueOf(request.status())));
    }

    @GetMapping("/{tenantId}/stats")
    @Operation(summary = "Şirkətin ümumi göstəriciləri", description = "Yalnız saylar — müştərilərin şəxsi məlumatı göstərilmir.")
    StatsResponse stats(@PathVariable UUID tenantId) {
        var stats = tenants.stats(tenantId);
        return new StatsResponse(stats.branches(), stats.activeCustomers(), stats.activeStaff());
    }

    @GetMapping("/{tenantId}/owners")
    @Operation(summary = "Şirkətin sahibləri")
    List<OwnerResponse> owners(@PathVariable UUID tenantId) {
        return tenants.owners(tenantId).stream()
                .map(owner -> new OwnerResponse(owner.id(), owner.email(), owner.displayName(), owner.status(),
                        owner.totpEnabled(), owner.lastLoginAt()))
                .toList();
    }

    @PostMapping("/{tenantId}/owner-invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Şirkət sahibinə dəvət", description = "Dəvət linki şirkətin öz domenində açılır, 72 saat etibarlıdır.")
    OwnerInvitationResponse inviteOwner(@PathVariable UUID tenantId, @Valid @RequestBody OwnerInvitationRequest request,
                                        @AuthenticationPrincipal AdminPrincipal principal) {
        var view = tenants.inviteOwner(principal, tenantId, request.email(),
                Language.fromTag(request.language()).orElse(Language.DEFAULT));
        return new OwnerInvitationResponse(view.id(), view.email(), view.expiresAt());
    }

    @PostMapping("/{tenantId}/owners/{userId}/mfa-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sahibin TOTP-sinin sıfırlanması",
            description = "Sahib həm telefonunu, həm bərpa kodlarını itirəndə. Step-up tələb edir; audit jurnalına yazılır.")
    void resetOwnerMfa(@PathVariable UUID tenantId, @PathVariable UUID userId,
                       @AuthenticationPrincipal AdminPrincipal principal) {
        tenants.resetOwnerMfa(principal, tenantId, userId);
    }
}
