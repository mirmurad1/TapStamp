package az.tapstamp.catalog.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import az.tapstamp.catalog.CatalogService;
import az.tapstamp.catalog.CatalogService.CatalogView;
import az.tapstamp.catalog.CatalogService.ItemInput;
import az.tapstamp.plan.Addon;
import az.tapstamp.plan.RequiresAddon;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Menyu və ya xidmətlər kataloqu — şirkət sahibi üçün (əlavə {@code CATALOG}). Hər dəyişiklik bütün kataloqu
 * qaytarır (admin ekranı bir sorğu ilə yenilənir) və audit jurnalına yazılır.
 */
@RestController
@RequiresAddon(Addon.CATALOG)
@RequestMapping("/api/v1/admin/catalog")
@PreAuthorize("hasRole('COMPANY_OWNER')")
@Tag(name = "Admin: menyu və xidmətlər")
public class AdminCatalogController {

    private final CatalogService catalog;

    public AdminCatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    public record SettingsRequest(@NotNull Boolean enabled,
                                  @Schema(description = "MENU, SERVICES və ya PRICES")
                                  @NotNull @Pattern(regexp = "MENU|SERVICES|PRICES") String title) {
    }

    public record CategoryRequest(@NotNull @Valid LocalizedText name, @Nullable Boolean active) {
    }

    public record OrderRequest(@NotNull @Size(max = 300) List<@NotNull UUID> ids) {
    }

    public record ItemRequest(@NotNull UUID categoryId,
                              @NotNull @Valid LocalizedText name,
                              @Nullable @Valid LocalizedText description,
                              @Schema(description = "AZN; null — qiymət göstərilmir")
                              @Nullable @DecimalMin("0") @DecimalMax("100000") @Digits(integer = 6, fraction = 2)
                              BigDecimal price,
                              @Schema(description = "Xidmətin müddəti, dəqiqə (istəyə bağlı)")
                              @Nullable @Min(1) @Max(1440) Integer durationMinutes,
                              @Schema(description = "false — \"hazırda yoxdur\" (müştəriyə solğun göstərilir)")
                              @Nullable Boolean available,
                              @Schema(description = "Yalnız bu filialda; null — bütün filiallarda")
                              @Nullable UUID branchId) {

        ItemInput toInput() {
            return new ItemInput(categoryId, name, description, price, durationMinutes,
                    !Boolean.FALSE.equals(available), branchId);
        }
    }

    @GetMapping
    @Operation(summary = "Kataloq", description = """
            Parametrlər (açar, bölmənin adı), kateqoriyalar sıra ilə və hər kateqoriyanın məhsulları (gizlədilənlər \
            və "hazırda yoxdur" olanlar da daxil).""")
    CatalogView get(@AuthenticationPrincipal AdminPrincipal principal) {
        return catalog.adminCatalog(principal);
    }

    @PutMapping("/settings")
    @Operation(summary = "Parametrlər", description = """
            `enabled = false` — müştəriyə göstərilmir (məlumat qalır). `title` — müştəriyə görünən ad: MENU (menyu), \
            SERVICES (xidmətlər), PRICES (qiymətlər).""")
    CatalogView settings(@AuthenticationPrincipal AdminPrincipal principal,
                         @Valid @RequestBody SettingsRequest request) {
        return catalog.updateSettings(principal, request.enabled(), request.title());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Kateqoriya yaratmaq", description = "Siyahının sonuna əlavə olunur. Ən çox 30 kateqoriya.")
    CatalogView createCategory(@AuthenticationPrincipal AdminPrincipal principal,
                               @Valid @RequestBody CategoryRequest request) {
        return catalog.createCategory(principal, request.name(), !Boolean.FALSE.equals(request.active()));
    }

    @PutMapping("/categories/order")
    @Operation(summary = "Kateqoriyaların sırası", description = "Bütün kateqoriyaların id-ləri yeni sıra ilə, hər biri bir dəfə.")
    CatalogView reorderCategories(@AuthenticationPrincipal AdminPrincipal principal,
                                  @Valid @RequestBody OrderRequest request) {
        return catalog.reorderCategories(principal, request.ids());
    }

    @PutMapping("/categories/{categoryId}")
    @Operation(summary = "Kateqoriyanı dəyişmək", description = "`active = false` — müştəriyə göstərilmir (silmədən).")
    CatalogView updateCategory(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID categoryId,
                               @Valid @RequestBody CategoryRequest request) {
        return catalog.updateCategory(principal, categoryId, request.name(), !Boolean.FALSE.equals(request.active()));
    }

    @DeleteMapping("/categories/{categoryId}")
    @Operation(summary = "Kateqoriyanı silmək", description = "Yalnız boş kateqoriya: içində məhsul varsa 409 `CATALOG_CATEGORY_NOT_EMPTY`.")
    CatalogView deleteCategory(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID categoryId) {
        return catalog.deleteCategory(principal, categoryId);
    }

    @PutMapping("/categories/{categoryId}/order")
    @Operation(summary = "Kateqoriyadakı məhsulların sırası", description = "Həmin kateqoriyanın bütün məhsullarının id-ləri yeni sıra ilə.")
    CatalogView reorderItems(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID categoryId,
                             @Valid @RequestBody OrderRequest request) {
        return catalog.reorderItems(principal, categoryId, request.ids());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Məhsul və ya xidmət yaratmaq", description = "Kateqoriyanın sonuna əlavə olunur. Ən çox 300 məhsul.")
    CatalogView createItem(@AuthenticationPrincipal AdminPrincipal principal, @Valid @RequestBody ItemRequest request) {
        return catalog.createItem(principal, request.toInput());
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Məhsulu dəyişmək", description = "Başqa kateqoriyaya köçürülən məhsul həmin kateqoriyanın sonuna düşür.")
    CatalogView updateItem(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID itemId,
                           @Valid @RequestBody ItemRequest request) {
        return catalog.updateItem(principal, itemId, request.toInput());
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Məhsulu silmək")
    CatalogView deleteItem(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID itemId) {
        return catalog.deleteItem(principal, itemId);
    }

    @PutMapping(path = "/items/{itemId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Məhsulun şəkli", description = """
            PNG və ya JPEG, 2 MB-a qədər, qısa tərəfi ən az 200 piksel; mərkəzdən kvadrat kəsilir, 640 piksələ \
            kiçildilir və PNG kimi saxlanılır (EXIF silinir). Uyğun deyilsə `CATALOG_IMAGE_INVALID`.""")
    CatalogView uploadImage(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID itemId,
                            @RequestParam("file") MultipartFile file) throws IOException {
        if (file.getSize() > CatalogService.IMAGE_MAX_BYTES) {
            throw new ApiException(ErrorCode.CATALOG_IMAGE_INVALID);
        }
        return catalog.uploadImage(principal, itemId, file.getBytes());
    }

    @DeleteMapping("/items/{itemId}/image")
    @Operation(summary = "Məhsulun şəklini silmək")
    CatalogView removeImage(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID itemId) {
        return catalog.removeImage(principal, itemId);
    }
}
