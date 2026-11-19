package az.tapstamp.catalog.api;

import java.time.Duration;
import java.util.UUID;

import az.tapstamp.catalog.CatalogService;
import az.tapstamp.catalog.CatalogService.CatalogView;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.image.SquarePng;
import az.tapstamp.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Menyu və ya xidmətlər — müştəri üçün ictimai səhifə (giriş tələb etmir, stol üstündəki QR ilə açılır). */
@RestController
@Tag(name = "İctimai: menyu və xidmətlər")
public class PublicCatalogController {

    private final CatalogService catalog;

    public PublicCatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/v1/public/catalog")
    @Operation(summary = "Menyu və ya xidmətlər (giriş tələb etmir)", description = """
            Əlavə açıq deyilsə və ya bölmə söndürülübsə `enabled = false` və boş siyahı. Gizlədilmiş və boş \
            kateqoriyalar göstərilmir; "hazırda yoxdur" məhsullar `available = false` ilə qaytarılır. `branchId` \
            verilibsə — bütün filiallar üçün olanlar və həmin filialınkılar.""")
    CatalogView get(@RequestParam(required = false) @Nullable UUID branchId) {
        TenantContext.require();
        return catalog.publicCatalog(branchId);
    }

    @GetMapping("/api/v1/public/catalog/items/{itemId}/image")
    @Operation(summary = "Məhsulun şəkli (640 px, JPEG və ya köhnə şəkillərdə PNG)", description = """
            Ünvan versiyalıdır (`imageUrl`-dakı `?v=N`), ona görə uzun müddət keşlənir. Şəkil yoxdursa 404.""")
    ResponseEntity<byte[]> image(@PathVariable UUID itemId) {
        TenantContext.require();
        return catalog.image(itemId)
                .map(found -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(SquarePng.contentType(found.png())))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(found.png()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    @GetMapping("/api/v1/public/catalog/items/{itemId}/thumb")
    @Operation(summary = "Məhsulun kiçik şəkli (192 px, JPEG)", description = """
            Menyu siyahısı üçün (`thumbUrl`). Ünvan versiyalıdır, uzun müddət keşlənir. Şəkil yoxdursa 404.""")
    ResponseEntity<byte[]> thumb(@PathVariable UUID itemId) {
        TenantContext.require();
        return catalog.thumbnail(itemId)
                .map(jpeg -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(jpeg))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
