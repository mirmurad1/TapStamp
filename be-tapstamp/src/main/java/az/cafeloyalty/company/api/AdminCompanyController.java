package az.cafeloyalty.company.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.cafeloyalty.company.service.CompanyAdminService;
import az.cafeloyalty.company.service.CompanyAdminService.BranchPolicy;
import az.cafeloyalty.company.service.CompanyAdminService.BranchView;
import az.cafeloyalty.company.service.CompanyAdminService.Company;
import az.cafeloyalty.security.admin.AdminPrincipal;
import az.cafeloyalty.shared.i18n.LocalizedText;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Şirkətin məzmunu və filiallar — yalnız sahib. */
@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('COMPANY_OWNER')")
@Tag(name = "Admin: parametrlər")
public class AdminCompanyController {

    private final CompanyAdminService company;

    public AdminCompanyController(CompanyAdminService company) {
        this.company = company;
    }

    @Schema(description = "Şirkətin məzmunu: ad və təsvir 3 dildə (az məcburi)")
    public record CompanyRequest(
            @NotNull @Valid LocalizedText name,
            @Valid LocalizedText description,
            @NotBlank @Size(max = 64) String timezone,
            @NotNull @Pattern(regexp = "az|en|ru") String defaultLanguage,
            @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String brandColor,
            @Schema(description = "SMS göndərən adı (latın hərfləri, rəqəm, boşluq; 11 simvola qədər)") @Size(max = 11)
            String smsSenderName) {
    }

    @Schema(description = "İş saatları: {\"mon\": [[\"08:00\",\"22:00\"]], …}; günlər mon…sun")
    public record BranchRequest(
            @Valid LocalizedText name,
            @Valid LocalizedText address,
            Map<String, List<List<String>>> businessHours,
            @Schema(description = "true — iş saatları silinir (hər zaman açıq)") Boolean clearBusinessHours,
            @Pattern(regexp = "ACTIVE|CLOSED") String status) {
    }

    public record BranchPolicyRequest(
            @NotNull @Min(0) @Max(1440) Integer minIntervalMinutes,
            @NotNull @Min(1) @Max(50) Integer maxStampsPerDay,
            @NotNull Boolean requireStaffApproval,
            @NotNull @Min(1) @Max(1440) Integer pendingTimeoutMinutes,
            @NotNull Boolean enforceBusinessHours) {
    }

    @GetMapping("/company")
    @Operation(summary = "Şirkətin məzmunu")
    Company company(@AuthenticationPrincipal AdminPrincipal principal) {
        return company.company(principal);
    }

    @PutMapping("/company")
    @Operation(summary = "Şirkətin məzmununun dəyişdirilməsi", description = "Ad və təsvir 3 dildə, saat qurşağı, standart dil, brend rəngi.")
    Company updateCompany(@AuthenticationPrincipal AdminPrincipal principal, @Valid @RequestBody CompanyRequest request) {
        return company.updateCompany(principal, new Company(request.name(), request.description(), request.timezone(),
                request.defaultLanguage(), request.brandColor(), request.smsSenderName()));
    }

    @GetMapping("/branches")
    @Operation(summary = "Filiallar", description = "Bağlanmış (CLOSED) filiallar da daxil.")
    List<BranchView> branches(@AuthenticationPrincipal AdminPrincipal principal) {
        return company.branches(principal);
    }

    @PostMapping("/branches")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Yeni filial")
    BranchView createBranch(@AuthenticationPrincipal AdminPrincipal principal, @Valid @RequestBody BranchRequest request) {
        return company.createBranch(principal, request.name(), request.address(), request.businessHours());
    }

    @PatchMapping("/branches/{branchId}")
    @Operation(summary = "Filialın dəyişdirilməsi", description = "Boş sahələr dəyişmir. Sonuncu aktiv filialı bağlamaq olmaz.")
    BranchView updateBranch(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID branchId,
                            @Valid @RequestBody BranchRequest request) {
        return company.updateBranch(principal, branchId, request.name(), request.address(), request.businessHours(),
                Boolean.TRUE.equals(request.clearBusinessHours()), request.status());
    }

    @GetMapping("/branches/{branchId}/stamp-policy")
    @Operation(summary = "Filialın möhür qaydası", description = "overridden=false — şirkətin standart qaydası tətbiq olunur.")
    BranchPolicy branchPolicy(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID branchId) {
        return company.branchPolicy(principal, branchId);
    }

    @PutMapping("/branches/{branchId}/stamp-policy")
    @Operation(summary = "Filial üçün ayrıca möhür qaydası", description = """
            Şirkətin standart qaydasını bu filial üçün əvəz edir. Möhürlər arası vaxt yenə də müştərinin bütün \
            filiallardakı son möhüründən hesablanır.""")
    BranchPolicy overrideBranchPolicy(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID branchId,
                                      @Valid @RequestBody BranchPolicyRequest request) {
        return company.overrideBranchPolicy(principal, branchId, new BranchPolicy(true, request.minIntervalMinutes(),
                request.maxStampsPerDay(), request.requireStaffApproval(), request.pendingTimeoutMinutes(),
                request.enforceBusinessHours()));
    }

    @DeleteMapping("/branches/{branchId}/stamp-policy")
    @Operation(summary = "Filialın ayrıca qaydasını silmək", description = "Filial yenidən şirkətin standart qaydası ilə işləyir.")
    BranchPolicy resetBranchPolicy(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID branchId) {
        return company.resetBranchPolicy(principal, branchId);
    }
}
