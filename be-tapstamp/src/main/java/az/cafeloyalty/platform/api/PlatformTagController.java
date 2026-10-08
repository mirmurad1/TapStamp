package az.cafeloyalty.platform.api;

import java.util.List;
import java.util.UUID;

import az.cafeloyalty.nfc.admin.TagAdminService.ProvisioningPackage;
import az.cafeloyalty.nfc.admin.TagAdminService.TagView;
import az.cafeloyalty.nfc.admin.TagAdminService.TapEvent;
import az.cafeloyalty.platform.service.PlatformTagService;
import az.cafeloyalty.platform.service.PlatformTagService.TagOverview;
import az.cafeloyalty.security.admin.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Şirkətin NFC çipləri — yalnız platforma admini əlavə edir və idarə edir (qərar 44). */
@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}/tags")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Platforma: NFC çipləri")
public class PlatformTagController {

    private final PlatformTagService tags;

    public PlatformTagController(PlatformTagService tags) {
        this.tags = tags;
    }

    @Schema(description = "Yeni çip: UID 7 bayt (14 hex simvol, iki nöqtə və boşluq ola bilər)")
    public record PlatformTagRegisterRequest(@NotBlank @Size(max = 32) String uid, @NotNull UUID branchId,
                                             @NotBlank @Size(max = 60) String label) {
    }

    public record PlatformTagUpdateRequest(@Size(min = 1, max = 60) String label, UUID branchId) {
    }

    @GetMapping
    @Operation(summary = "Şirkətin çipləri", description = """
            Çiplər, şirkətin aktiv filialları və say: ilk N çip pulsuzdur (app.nfc.free-tags-per-tenant), qalanları \
            pulludur.""")
    TagOverview overview(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId) {
        return tags.overview(principal, tenantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Çipin qeydiyyatı", description = "Server 3 təsadüfi açar yaradır (PENDING); ilk toxunuş onları aktivləşdirir.")
    TagView register(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId,
                     @Valid @RequestBody PlatformTagRegisterRequest request) {
        return tags.register(principal, tenantId, request.uid(), request.branchId(), request.label());
    }

    @PatchMapping("/{tagId}")
    @Operation(summary = "Etiket və ya filialın dəyişdirilməsi")
    TagView update(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId,
                   @PathVariable UUID tagId, @Valid @RequestBody PlatformTagUpdateRequest request) {
        return tags.update(principal, tenantId, tagId, request.label(), request.branchId());
    }

    @PostMapping("/{tagId}/disable")
    @Operation(summary = "Deaktivasiya", description = "Dərhal qüvvəyə minir: bütün toxunuşlar TAG_DISABLED alır.")
    TagView disable(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId,
                    @PathVariable UUID tagId) {
        return tags.setEnabled(principal, tenantId, tagId, false);
    }

    @PostMapping("/{tagId}/enable")
    @Operation(summary = "Aktivləşdirmə")
    TagView enable(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId,
                   @PathVariable UUID tagId) {
        return tags.setEnabled(principal, tenantId, tagId, true);
    }

    @PostMapping("/{tagId}/key-rotations")
    @Operation(summary = "Açarın yenilənməsi", description = """
            Yeni PENDING açar dəsti (step-up). Çip yeni açarlarla proqramlaşdırılandan sonra ilk toxunuşda keçid avtomatik \
            olur; o vaxta qədər köhnə açar işləyir.""")
    TagView rotate(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId,
                   @PathVariable UUID tagId) {
        return tags.rotateKeys(principal, tenantId, tagId);
    }

    @PostMapping("/{tagId}/provisioning-package")
    @Operation(summary = "Provisioning paketi", description = """
            Çipin proqramlaşdırılması üçün URL şablonu, SDM ofsetləri və açarlar. Step-up tələb edir; PENDING açarlar \
            yalnız bir dəfə göstərilir (təkrar — PROVISIONING_ALREADY_EXPORTED), auditə yazılır.""")
    ResponseEntity<ProvisioningPackage> provisioningPackage(@AuthenticationPrincipal AdminPrincipal principal,
                                                            @PathVariable UUID tenantId, @PathVariable UUID tagId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(tags.provisioningPackage(principal, tenantId, tagId));
    }

    @GetMapping("/{tagId}/events")
    @Operation(summary = "Çipin toxunuş jurnalı", description = "Son 100 cəhd — qəbul və rədd edilənlər, səbəbləri ilə.")
    List<TapEvent> events(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId,
                          @PathVariable UUID tagId) {
        return tags.events(principal, tenantId, tagId);
    }
}
