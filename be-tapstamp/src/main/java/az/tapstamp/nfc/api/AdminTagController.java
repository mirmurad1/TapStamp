package az.tapstamp.nfc.api;

import java.util.List;
import java.util.UUID;

import az.tapstamp.nfc.admin.TagAdminService;
import az.tapstamp.nfc.admin.TagAdminService.TagView;
import az.tapstamp.nfc.admin.TagAdminService.TapEvent;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * NFC çipləri şirkət panelində yalnız baxış üçündür: sahib bütün, filial meneceri öz filialının çiplərini görür.
 * Çipləri platforma admini əlavə edir və idarə edir ({@code /api/v1/platform/tenants/{id}/tags}).
 */
@RestController
@RequestMapping("/api/v1/admin/tags")
@PreAuthorize("hasAnyRole('COMPANY_OWNER', 'BRANCH_MANAGER')")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Admin: NFC çipləri")
public class AdminTagController {

    private final TagAdminService tags;

    public AdminTagController(TagAdminService tags) {
        this.tags = tags;
    }

    @GetMapping
    @Operation(summary = "Çiplərin siyahısı", description = "Açar vəziyyəti (ACTIVE/PENDING versiyası) və son 7 gündə toxunuş sayı ilə; səhifə `page` (0-dan), `size` (5, 10, 20, 50).")
    PageResult<TagView> list(@AuthenticationPrincipal AdminPrincipal principal, @RequestParam(required = false) UUID branchId,
                             @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        return tags.list(principal, branchId, PageQuery.of(page, size));
    }

    @GetMapping("/{tagId}/events")
    @Operation(summary = "Çipin toxunuş jurnalı", description = "Cəhdlər (qəbul və rədd edilənlər, səbəbləri ilə), yenidən köhnəyə, səhifə ilə.")
    PageResult<TapEvent> events(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tagId,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        return tags.events(principal, tagId, PageQuery.of(page, size));
    }
}
