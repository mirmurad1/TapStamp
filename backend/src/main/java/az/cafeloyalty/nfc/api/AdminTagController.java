package az.cafeloyalty.nfc.api;

import java.util.List;
import java.util.UUID;

import az.cafeloyalty.nfc.admin.TagAdminService;
import az.cafeloyalty.nfc.admin.TagAdminService.ProvisioningPackage;
import az.cafeloyalty.nfc.admin.TagAdminService.TagView;
import az.cafeloyalty.nfc.admin.TagAdminService.TapEvent;
import az.cafeloyalty.security.admin.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** NFC çipləri: sahib idarə edir, filial meneceri öz filialının çiplərinə baxır. */
@RestController
@RequestMapping("/api/v1/admin/tags")
@PreAuthorize("hasAnyRole('COMPANY_OWNER', 'BRANCH_MANAGER')")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Admin: NFC çipləri")
public class AdminTagController {

    private final TagAdminService tags;

    public AdminTagController(TagAdminService tags) {
        this.tags = tags;
    }

    @Schema(description = "Yeni çip: UID 7 bayt (14 hex simvol, iki nöqtə və boşluq ola bilər)")
    public record TagRegisterRequest(@NotBlank @Size(max = 32) String uid, @NotNull UUID branchId,
                                  @NotBlank @Size(max = 60) String label) {
    }

    public record TagUpdateRequest(@Size(min = 1, max = 60) String label, UUID branchId) {
    }

    @GetMapping
    @Operation(summary = "Çiplərin siyahısı", description = "Açar vəziyyəti (ACTIVE/PENDING versiyası) və son 7 gündə toxunuş sayı ilə.")
    List<TagView> list(@AuthenticationPrincipal AdminPrincipal principal, @RequestParam(required = false) UUID branchId) {
        return tags.list(principal, branchId);
    }

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Çipin qeydiyyatı", description = "Server 3 təsadüfi açar yaradır (PENDING); ilk toxunuş onları aktivləşdirir.")
    TagView register(@AuthenticationPrincipal AdminPrincipal principal, @Valid @RequestBody TagRegisterRequest request) {
        return tags.register(principal, request.uid(), request.branchId(), request.label());
    }

    @PatchMapping("/{tagId}")
    @PreAuthorize("hasRole('COMPANY_OWNER')")
    @Operation(summary = "Etiket və ya filialın dəyişdirilməsi")
    TagView update(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tagId,
               @Valid @RequestBody TagUpdateRequest request) {
        return tags.update(principal, tagId, request.label(), request.branchId());
    }

    @PostMapping("/{tagId}/disable")
    @PreAuthorize("hasRole('COMPANY_OWNER')")
    @Operation(summary = "Deaktivasiya", description = "Dərhal qüvvəyə minir: bütün toxunuşlar TAG_DISABLED alır.")
    TagView disable(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tagId) {
        return tags.setEnabled(principal, tagId, false);
    }

    @PostMapping("/{tagId}/enable")
    @PreAuthorize("hasRole('COMPANY_OWNER')")
    @Operation(summary = "Aktivləşdirmə")
    TagView enable(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tagId) {
        return tags.setEnabled(principal, tagId, true);
    }

    @PostMapping("/{tagId}/key-rotations")
    @PreAuthorize("hasRole('COMPANY_OWNER')")
    @Operation(summary = "Açarın yenilənməsi", description = """
            Yeni PENDING açar dəsti (step-up). Çip yeni açarlarla proqramlaşdırılandan sonra ilk toxunuşda keçid avtomatik \
            olur; o vaxta qədər köhnə açar işləyir.""")
    TagView rotate(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tagId) {
        return tags.rotateKeys(principal, tagId);
    }

    @PostMapping("/{tagId}/provisioning-package")
    @PreAuthorize("hasRole('COMPANY_OWNER')")
    @Operation(summary = "Provisioning paketi", description = """
            Çipin proqramlaşdırılması üçün URL şablonu, SDM ofsetləri və açarlar. Step-up tələb edir; PENDING açarlar \
            yalnız bir dəfə göstərilir (təkrar — PROVISIONING_ALREADY_EXPORTED), auditə yazılır.""")
    ResponseEntity<ProvisioningPackage> provisioningPackage(@AuthenticationPrincipal AdminPrincipal principal,
                                                            @PathVariable UUID tagId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tags.provisioningPackage(principal, tagId));
    }

    @GetMapping("/{tagId}/events")
    @Operation(summary = "Çipin toxunuş jurnalı", description = "Son 100 cəhd — qəbul və rədd edilənlər, səbəbləri ilə.")
    List<TapEvent> events(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tagId) {
        return tags.events(principal, tagId);
    }
}
