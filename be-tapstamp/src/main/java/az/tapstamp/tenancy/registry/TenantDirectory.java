package az.tapstamp.tenancy.registry;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.persistence.LocalizedTextConverter;
import az.tapstamp.tenancy.TenantInfo;
import az.tapstamp.tenancy.TenantStatus;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Platforma reyestri: tenant-lar və onların domenləri. Domen → tenant axtarışı hər sorğuda lazım olduğu
 * üçün qısa müddət keşlənir; status dəyişəndə keş təmizlənir.
 */
@Component
public class TenantDirectory {

    private static final String SELECT_TENANT = """
            SELECT t.id, t.slug, t.display_name, t.status, t.default_language, t.timezone,
                   t.db_host, t.db_port, t.db_name, t.db_owner_username, t.db_owner_password_enc,
                   t.db_app_username, t.db_app_password_enc
            FROM tenants t""";

    private static final LocalizedTextConverter TEXT = new LocalizedTextConverter();

    private final JdbcClient platform;
    private final Cache<String, Optional<TenantInfo>> byHost = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    public TenantDirectory(@Qualifier("platformJdbc") JdbcClient platform) {
        this.platform = platform;
    }

    /** Domen adına görə tenant (kiçik hərflə, portsuz). */
    public Optional<TenantInfo> findByHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return byHost.get(normalized, h -> platform.sql(SELECT_TENANT + """
                         JOIN tenant_domains d ON d.tenant_id = t.id
                        WHERE d.hostname = :host""")
                .param("host", h)
                .query(TenantDirectory::map)
                .optional()
                .map(TenantRecord::toInfo));
    }

    public Optional<TenantRecord> findById(UUID id) {
        return platform.sql(SELECT_TENANT + " WHERE t.id = :id").param("id", id).query(TenantDirectory::map).optional();
    }

    public Optional<TenantRecord> findBySlug(String slug) {
        return platform.sql(SELECT_TENANT + " WHERE t.slug = :slug").param("slug", slug).query(TenantDirectory::map).optional();
    }

    public List<TenantRecord> findAll() {
        return platform.sql(SELECT_TENANT + " ORDER BY t.created_at").query(TenantDirectory::map).list();
    }

    public Optional<String> primaryHost(UUID tenantId) {
        return platform.sql("SELECT hostname FROM tenant_domains WHERE tenant_id = :id AND is_primary")
                .param("id", tenantId)
                .query(String.class)
                .optional();
    }

    public List<String> hostnames(UUID tenantId) {
        return platform.sql("SELECT hostname FROM tenant_domains WHERE tenant_id = :id ORDER BY hostname")
                .param("id", tenantId)
                .query(String.class)
                .list();
    }

    public void updateStatus(UUID tenantId, TenantStatus status, String error) {
        platform.sql("""
                        UPDATE tenants SET status = :status, last_migration_error = :error, updated_at = now(),
                               version = version + 1
                        WHERE id = :id""")
                .param("status", status.name())
                .param("error", error)
                .param("id", tenantId)
                .update();
        evictCaches();
    }

    public void evictCaches() {
        byHost.invalidateAll();
    }

    private static TenantRecord map(ResultSet rs, int ignoredRowNum) throws SQLException {
        return new TenantRecord(
                rs.getObject("id", UUID.class),
                rs.getString("slug"),
                TEXT.convertToEntityAttribute(rs.getString("display_name")),
                TenantStatus.valueOf(rs.getString("status")),
                Language.fromTag(rs.getString("default_language")).orElse(Language.DEFAULT),
                ZoneId.of(rs.getString("timezone")),
                rs.getString("db_host"),
                rs.getInt("db_port"),
                rs.getString("db_name"),
                rs.getString("db_owner_username"),
                rs.getBytes("db_owner_password_enc"),
                rs.getString("db_app_username"),
                rs.getBytes("db_app_password_enc"));
    }
}
