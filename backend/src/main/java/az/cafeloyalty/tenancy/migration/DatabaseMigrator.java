package az.cafeloyalty.tenancy.migration;

import java.util.Map;

import az.cafeloyalty.tenancy.TenancyProperties;
import az.cafeloyalty.tenancy.TenantStatus;
import az.cafeloyalty.tenancy.datasource.TenantCredentials;
import az.cafeloyalty.tenancy.datasource.TenantDataSourceRegistry;
import az.cafeloyalty.tenancy.registry.TenantDirectory;
import az.cafeloyalty.tenancy.registry.TenantRecord;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Başlanğıcda (veb server sorğu qəbul etməzdən əvvəl) əvvəlcə platforma, sonra bütün tenant bazalarını
 * miqrasiya edir. Miqrasiyalar sxem sahibi rolu ilə işləyir; tətbiqin özü DDL hüququna malik deyil.
 *
 * <p>Bir tenant-ın miqrasiyası uğursuz olarsa, həmin tenant {@code MIGRATION_FAILED} statusu alır və xidmət
 * göstərmir (fail closed); digər tenant-lar işləməyə davam edir. Platforma miqrasiyası uğursuzdursa, tətbiq başlamır.
 */
@Component
public class DatabaseMigrator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrator.class);

    private final TenancyProperties properties;
    private final TenantDirectory directory;
    private final TenantCredentials credentials;
    private final TenantDataSourceRegistry dataSources;
    private final JdbcClient platform;

    public DatabaseMigrator(TenancyProperties properties, TenantDirectory directory, TenantCredentials credentials,
                            TenantDataSourceRegistry dataSources, @Qualifier("platformJdbc") JdbcClient platform) {
        this.properties = properties;
        this.directory = directory;
        this.credentials = credentials;
        this.dataSources = dataSources;
        this.platform = platform;
    }

    @Override
    public void afterPropertiesSet() {
        migratePlatform();
        for (TenantRecord tenant : directory.findAll()) {
            if (tenant.status() == TenantStatus.ACTIVE || tenant.status() == TenantStatus.MIGRATION_FAILED) {
                migrateTenantSafely(tenant);
            }
        }
    }

    public void migratePlatform() {
        TenancyProperties.Platform config = properties.platform();
        MigrateResult result = Flyway.configure()
                .dataSource(properties.jdbcUrl(properties.dbHost(), properties.dbPort(), config.database()),
                        config.ownerUsername(), config.ownerPassword())
                .locations("classpath:db/migration/platform")
                .placeholders(Map.of("appRole", config.appUsername()))
                .cleanDisabled(true)
                .load()
                .migrate();
        log.info("Platforma bazası miqrasiya olundu: versiya {}", result.targetSchemaVersion);
    }

    /** Tenant bazasını miqrasiya edir; xəta olarsa istisna atır (provisioning bunu gözləyir). */
    public String migrateTenant(TenantRecord tenant) {
        MigrateResult result = Flyway.configure()
                .dataSource(credentials.jdbcUrl(tenant), tenant.ownerUsername(), credentials.ownerPassword(tenant))
                .locations("classpath:db/migration/tenant")
                .placeholders(Map.of("appRole", tenant.appUsername(), "ownerRole", tenant.ownerUsername()))
                .cleanDisabled(true)
                .load()
                .migrate();
        String version = result.targetSchemaVersion;
        platform.sql("UPDATE tenants SET schema_version = :version, updated_at = now() WHERE id = :id")
                .param("version", version)
                .param("id", tenant.id())
                .update();
        return version;
    }

    private void migrateTenantSafely(TenantRecord tenant) {
        try {
            String version = migrateTenant(tenant);
            if (tenant.status() == TenantStatus.MIGRATION_FAILED) {
                directory.updateStatus(tenant.id(), TenantStatus.ACTIVE, null);
            }
            log.info("Tenant '{}' miqrasiya olundu: versiya {}", tenant.slug(), version);
        } catch (RuntimeException e) {
            log.error("Tenant '{}' miqrasiyası uğursuz oldu; tenant xidmətdən çıxarılır", tenant.slug(), e);
            dataSources.evict(tenant.id());
            directory.updateStatus(tenant.id(), TenantStatus.MIGRATION_FAILED, truncate(e.getMessage()));
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
