package az.tapstamp.catalog;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.catalog.repository.CatalogMapper;
import az.tapstamp.catalog.repository.CatalogMapper.Category;
import az.tapstamp.catalog.repository.CatalogMapper.Image;
import az.tapstamp.catalog.repository.CatalogMapper.Item;
import az.tapstamp.catalog.repository.CatalogMapper.Settings;
import az.tapstamp.plan.Addon;
import az.tapstamp.plan.PlanService;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.image.SquarePng;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menyu və ya xidmətlər kataloqu (əlavə {@code CATALOG}, qərar #64): şirkət sahibi kateqoriyaları və onların
 * məhsul/xidmətlərini idarə edir, müştəri ictimai səhifədə görür (giriş tələb etmir). Yalnız kataloqdur — sifariş və
 * ödəniş yoxdur. Gizlədilmiş kateqoriya müştəriyə göstərilmir; "hazırda yoxdur" məhsul göstərilir (solğun).
 */
@Service
public class CatalogService {

    public static final int MAX_CATEGORIES = 30;
    public static final int MAX_ITEMS = 300;
    public static final Set<String> TITLES = Set.of("MENU", "SERVICES", "PRICES");
    /** Məhsul şəkli: PNG və ya JPEG, 2 MB-a qədər, qısa tərəfi ən az 200 piksel; 640 px kvadrat JPEG saxlanılır. */
    public static final int IMAGE_MAX_BYTES = 2 * 1024 * 1024;
    static final SquarePng IMAGE = new SquarePng(IMAGE_MAX_BYTES, 200, 4096, 640, ErrorCode.CATALOG_IMAGE_INVALID);
    /** Siyahıdakı şəklin tərəfi: ekranda 88 px, 2x ekranlar üçün ehtiyatla 192 px. */
    static final int THUMB_SIZE = 192;

    public record ItemInput(UUID categoryId, LocalizedText name, @Nullable LocalizedText description,
                            @Nullable BigDecimal price, @Nullable Integer durationMinutes, boolean available,
                            @Nullable UUID branchId) {
    }

    /** {@code imageUrl} — versiyalı ictimai ünvan (şəkil yoxdursa null); {@code thumbUrl} — siyahı üçün kiçik variant. */
    public record ItemView(UUID id, UUID categoryId, LocalizedText name, @Nullable LocalizedText description,
                           @Nullable BigDecimal price, @Nullable Integer durationMinutes, @Nullable String imageUrl,
                           @Nullable String thumbUrl, boolean available, @Nullable UUID branchId) {
    }

    public record CategoryView(UUID id, LocalizedText name, boolean active, List<ItemView> items) {
    }

    /** Admin üçün bütün kataloq; müştəri üçün — yalnız göstərilənlər ({@code enabled = false} olanda boş). */
    public record CatalogView(boolean enabled, String title, List<CategoryView> categories) {
    }

    private final CatalogMapper catalog;
    private final PlanService plans;
    private final AuditLogger audit;
    private final Clock clock;

    public CatalogService(CatalogMapper catalog, PlanService plans, AuditLogger audit, Clock clock) {
        this.catalog = catalog;
        this.plans = plans;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- müştəri

    /**
     * İctimai kataloq: əlavə açıq və bölmə aktiv olmalıdır. Gizlədilmiş və boş kateqoriyalar göstərilmir; filial
     * verilibsə, yalnız bütün filiallar üçün olan və həmin filiala aid məhsullar.
     */
    public CatalogView publicCatalog(@Nullable UUID branchId) {
        Settings settings = catalog.settings();
        if (!plans.has(Addon.CATALOG) || !settings.enabled()) {
            return new CatalogView(false, settings.title(), List.of());
        }
        List<CategoryView> categories = build(catalog.categories(), catalog.items()).stream()
                .filter(CategoryView::active)
                .map(category -> new CategoryView(category.id(), category.name(), true, category.items().stream()
                        .filter(item -> branchId == null || item.branchId() == null || item.branchId().equals(branchId))
                        .toList()))
                .filter(category -> !category.items().isEmpty())
                .toList();
        return new CatalogView(true, settings.title(), categories);
    }

    /** Bölmənin adı (MENU, SERVICES, PRICES) — link önizləməsi üçün; menyu göstərilmirsə boş. */
    public Optional<String> publicTitle() {
        Settings settings = catalog.settings();
        return plans.has(Addon.CATALOG) && settings.enabled() ? Optional.of(settings.title()) : Optional.empty();
    }

    /** Şəkil (versiya ünvandakı ilə eyni olmasa da qaytarılır — köhnə keşlənmiş səhifələr üçün). */
    public Optional<Image> image(UUID itemId) {
        if (!plans.has(Addon.CATALOG)) {
            return Optional.empty();
        }
        return catalog.findImage(itemId);
    }

    /**
     * Şəklin kiçik variantı (JPEG). Bu dəyişiklikdən əvvəl yüklənmiş şəkillər üçün ilk sorğuda yaradılıb saxlanılır —
     * ayrıca köçürmə lazım deyil.
     */
    public Optional<byte[]> thumbnail(UUID itemId) {
        if (!plans.has(Addon.CATALOG)) {
            return Optional.empty();
        }
        Optional<byte[]> stored = catalog.findThumbnail(itemId).map(CatalogMapper.Thumbnail::jpeg);
        if (stored.isPresent()) {
            return stored;
        }
        return catalog.findImage(itemId).map(image -> {
            byte[] thumb = SquarePng.jpegThumbnail(image.png(), THUMB_SIZE);
            catalog.saveThumbnail(itemId, thumb);
            return thumb;
        });
    }

    // ---------------------------------------------------------------- admin

    public CatalogView adminCatalog(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        Settings settings = catalog.settings();
        return new CatalogView(settings.enabled(), settings.title(), build(catalog.categories(), catalog.items()));
    }

    @Transactional
    public CatalogView updateSettings(AdminPrincipal principal, boolean enabled, String title) {
        AccessPolicy.requireCompanyWide(principal);
        if (!TITLES.contains(title)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        catalog.updateSettings(enabled, title, principal.userId(), clock.instant());
        audit(principal, "CATALOG_SETTINGS_UPDATED", null, Map.of("enabled", enabled, "title", title));
        return adminCatalog(principal);
    }

    @Transactional
    public CatalogView createCategory(AdminPrincipal principal, LocalizedText name, boolean active) {
        AccessPolicy.requireCompanyWide(principal);
        if (catalog.countCategories() >= MAX_CATEGORIES) {
            throw new ApiException(ErrorCode.CATALOG_LIMIT_REACHED);
        }
        UUID id = Ids.newId();
        catalog.insertCategory(id, name, catalog.nextCategoryPosition(), active, principal.userId(), clock.instant());
        audit(principal, "CATALOG_CATEGORY_CREATED", id, Map.of("name", name.az()));
        return adminCatalog(principal);
    }

    @Transactional
    public CatalogView updateCategory(AdminPrincipal principal, UUID id, LocalizedText name, boolean active) {
        AccessPolicy.requireCompanyWide(principal);
        category(id);
        catalog.updateCategory(id, name, active, principal.userId(), clock.instant());
        audit(principal, "CATALOG_CATEGORY_UPDATED", id, Map.of("name", name.az(), "active", active));
        return adminCatalog(principal);
    }

    /** İçində məhsul olan kateqoriya silinmir: 409 {@code CATALOG_CATEGORY_NOT_EMPTY}. */
    @Transactional
    public CatalogView deleteCategory(AdminPrincipal principal, UUID id) {
        AccessPolicy.requireCompanyWide(principal);
        Category category = category(id);
        if (catalog.deleteEmptyCategory(id) == 0) {
            throw new ApiException(ErrorCode.CATALOG_CATEGORY_NOT_EMPTY);
        }
        audit(principal, "CATALOG_CATEGORY_DELETED", id, Map.of("name", category.name().az()));
        return adminCatalog(principal);
    }

    /** Kateqoriyaların yeni sırası: bütün kateqoriyalar, hər biri bir dəfə. */
    @Transactional
    public CatalogView reorderCategories(AdminPrincipal principal, List<UUID> ids) {
        AccessPolicy.requireCompanyWide(principal);
        requireSameSet(ids, catalog.categories().stream().map(Category::id).toList());
        for (int i = 0; i < ids.size(); i++) {
            catalog.setCategoryPosition(ids.get(i), i);
        }
        audit(principal, "CATALOG_REORDERED", null, Map.of("categories", ids.size()));
        return adminCatalog(principal);
    }

    @Transactional
    public CatalogView createItem(AdminPrincipal principal, ItemInput input) {
        AccessPolicy.requireCompanyWide(principal);
        validate(input);
        if (catalog.countItems() >= MAX_ITEMS) {
            throw new ApiException(ErrorCode.CATALOG_LIMIT_REACHED);
        }
        UUID id = Ids.newId();
        catalog.insertItem(id, input.categoryId(), input.name(), input.description(), input.price(),
                input.durationMinutes(), input.available(), input.branchId(),
                catalog.nextItemPosition(input.categoryId()), principal.userId(), clock.instant());
        audit(principal, "CATALOG_ITEM_CREATED", id, Map.of("name", input.name().az()));
        return adminCatalog(principal);
    }

    /** Başqa kateqoriyaya köçürülən məhsul həmin kateqoriyanın sonuna düşür. */
    @Transactional
    public CatalogView updateItem(AdminPrincipal principal, UUID id, ItemInput input) {
        AccessPolicy.requireCompanyWide(principal);
        Item before = item(id);
        validate(input);
        int position = before.categoryId().equals(input.categoryId()) ? before.position()
                : catalog.nextItemPosition(input.categoryId());
        catalog.updateItem(id, input.categoryId(), input.name(), input.description(), input.price(),
                input.durationMinutes(), input.available(), input.branchId(), position, principal.userId(),
                clock.instant());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", input.name().az());
        details.put("price", input.price() == null ? "" : input.price().toPlainString());
        details.put("available", input.available());
        audit(principal, "CATALOG_ITEM_UPDATED", id, details);
        return adminCatalog(principal);
    }

    @Transactional
    public CatalogView deleteItem(AdminPrincipal principal, UUID id) {
        AccessPolicy.requireCompanyWide(principal);
        Item item = item(id);
        catalog.deleteItem(id);
        audit(principal, "CATALOG_ITEM_DELETED", id, Map.of("name", item.name().az()));
        return adminCatalog(principal);
    }

    /** Kateqoriyadakı məhsulların yeni sırası: həmin kateqoriyanın bütün məhsulları, hər biri bir dəfə. */
    @Transactional
    public CatalogView reorderItems(AdminPrincipal principal, UUID categoryId, List<UUID> ids) {
        AccessPolicy.requireCompanyWide(principal);
        category(categoryId);
        requireSameSet(ids, catalog.items().stream().filter(item -> item.categoryId().equals(categoryId))
                .map(Item::id).toList());
        for (int i = 0; i < ids.size(); i++) {
            catalog.setItemPosition(ids.get(i), i);
        }
        audit(principal, "CATALOG_REORDERED", categoryId, Map.of("items", ids.size()));
        return adminCatalog(principal);
    }

    @Transactional
    public CatalogView uploadImage(AdminPrincipal principal, UUID itemId, byte[] file) {
        AccessPolicy.requireCompanyWide(principal);
        item(itemId);
        byte[] image = IMAGE.normalizeJpeg(file);
        byte[] thumb = SquarePng.jpegThumbnail(image, THUMB_SIZE);
        catalog.updateImage(itemId, image, thumb, principal.userId(), clock.instant());
        audit(principal, "CATALOG_ITEM_IMAGE_UPDATED", itemId, Map.of("bytes", image.length));
        return adminCatalog(principal);
    }

    @Transactional
    public CatalogView removeImage(AdminPrincipal principal, UUID itemId) {
        AccessPolicy.requireCompanyWide(principal);
        item(itemId);
        catalog.updateImage(itemId, null, null, principal.userId(), clock.instant());
        audit(principal, "CATALOG_ITEM_IMAGE_REMOVED", itemId, Map.of());
        return adminCatalog(principal);
    }

    // ---------------------------------------------------------------- köməkçilər

    private void validate(ItemInput input) {
        category(input.categoryId());
        if (input.branchId() != null && !catalog.branchExists(input.branchId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private Category category(UUID id) {
        return catalog.findCategory(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private Item item(UUID id) {
        return catalog.findItem(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private static void requireSameSet(List<UUID> given, List<UUID> existing) {
        if (given.size() != existing.size() || !new HashSet<>(given).equals(new HashSet<>(existing))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private static List<CategoryView> build(List<Category> categories, List<Item> items) {
        Map<UUID, List<ItemView>> byCategory = new LinkedHashMap<>();
        for (Item item : items) {
            byCategory.computeIfAbsent(item.categoryId(), key -> new ArrayList<>()).add(view(item));
        }
        return categories.stream()
                .map(category -> new CategoryView(category.id(), category.name(), category.active(),
                        byCategory.getOrDefault(category.id(), List.of())))
                .toList();
    }

    private static ItemView view(Item item) {
        String base = "/api/v1/public/catalog/items/" + item.id();
        String imageUrl = item.imageVersion() == 0 ? null : base + "/image?v=" + item.imageVersion();
        String thumbUrl = item.imageVersion() == 0 ? null : base + "/thumb?v=" + item.imageVersion();
        return new ItemView(item.id(), item.categoryId(), item.name(), item.description(), item.price(),
                item.durationMinutes(), imageUrl, thumbUrl, item.available(), item.branchId());
    }

    private void audit(AdminPrincipal principal, String action, @Nullable UUID targetId, Map<String, Object> details) {
        audit.tenant(action, AuditLogger.ActorType.STAFF, principal.userId(), "catalog", targetId, details);
    }
}
