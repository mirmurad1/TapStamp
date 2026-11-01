package az.tapstamp.catalog.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.TenantMapper;
import org.jspecify.annotations.Nullable;

/**
 * Menyu və ya xidmətlər kataloqu ({@code catalog_settings}, {@code catalog_categories}, {@code catalog_items}).
 * SQL: {@code mapper/tenant/catalog/CatalogMapper.xml}.
 */
@TenantMapper
public interface CatalogMapper {

    /** {@code title} — MENU, SERVICES və ya PRICES (bölmənin müştəriyə görünən adı). */
    record Settings(boolean enabled, String title) {
    }

    record Category(UUID id, LocalizedText name, int position, boolean active) {
    }

    /** {@code imageVersion} — 0 olanda şəkil yoxdur. */
    record Item(UUID id, UUID categoryId, LocalizedText name, @Nullable LocalizedText description,
                @Nullable BigDecimal price, @Nullable Integer durationMinutes, int imageVersion, boolean available,
                @Nullable UUID branchId, int position) {
    }

    record Image(byte[] png, int version) {
    }

    Settings settings();

    void updateSettings(boolean enabled, String title, UUID staffId, Instant now);

    // --- kateqoriyalar

    /** Sıraya görə. */
    List<Category> categories();

    Optional<Category> findCategory(UUID id);

    long countCategories();

    int nextCategoryPosition();

    void insertCategory(UUID id, LocalizedText name, int position, boolean active, UUID staffId, Instant now);

    void updateCategory(UUID id, LocalizedText name, boolean active, UUID staffId, Instant now);

    void setCategoryPosition(UUID id, int position);

    /** Boş kateqoriya silinir; içində məhsul varsa 0. */
    int deleteEmptyCategory(UUID id);

    // --- məhsullar və xidmətlər

    /** Kateqoriya, sonra sıraya görə. */
    List<Item> items();

    Optional<Item> findItem(UUID id);

    long countItems();

    int nextItemPosition(UUID categoryId);

    void insertItem(UUID id, UUID categoryId, LocalizedText name, @Nullable LocalizedText description,
                    @Nullable BigDecimal price, @Nullable Integer durationMinutes, boolean available,
                    @Nullable UUID branchId, int position, UUID staffId, Instant now);

    void updateItem(UUID id, UUID categoryId, LocalizedText name, @Nullable LocalizedText description,
                    @Nullable BigDecimal price, @Nullable Integer durationMinutes, boolean available,
                    @Nullable UUID branchId, int position, UUID staffId, Instant now);

    void setItemPosition(UUID id, int position);

    void deleteItem(UUID id);

    /** Şəkil (null — silinir); versiya hər dəfə artır. */
    void updateImage(UUID id, byte @Nullable [] png, UUID staffId, Instant now);

    Optional<Image> findImage(UUID id);

    boolean branchExists(UUID branchId);
}
