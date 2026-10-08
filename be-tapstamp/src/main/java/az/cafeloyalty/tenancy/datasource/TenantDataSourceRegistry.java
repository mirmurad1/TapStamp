package az.cafeloyalty.tenancy.datasource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import az.cafeloyalty.tenancy.TenancyProperties;
import az.cafeloyalty.tenancy.TenantInfo;
import az.cafeloyalty.tenancy.registry.TenantDirectory;
import az.cafeloyalty.tenancy.registry.TenantRecord;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Hər tenant üçün ayrıca, kiçik connection pool. Pool-lar tənbəl yaradılır (ilk sorğuda) və tenant-ın öz
 * DB rolu ilə qoşulur — bu rol yalnız öz bazasına {@code CONNECT} hüququna malikdir.
 */
@Component
public class TenantDataSourceRegistry implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TenantDataSourceRegistry.class);

    private final TenantDirectory directory;
    private final TenantCredentials credentials;
    private final TenancyProperties properties;
    private final Map<UUID, HikariDataSource> pools = new ConcurrentHashMap<>();

    public TenantDataSourceRegistry(TenantDirectory directory, TenantCredentials credentials, TenancyProperties properties) {
        this.directory = directory;
        this.credentials = credentials;
        this.properties = properties;
    }

    public DataSource dataSourceFor(TenantInfo tenant) {
        if (!tenant.status().servesRequests()) {
            throw new IllegalStateException("Tenant is not active: " + tenant.slug());
        }
        return pools.computeIfAbsent(tenant.id(), this::createPool);
    }

    /** Tenant dayandırılanda və ya şifrə dəyişəndə pool bağlanır. */
    public void evict(UUID tenantId) {
        HikariDataSource pool = pools.remove(tenantId);
        if (pool != null) {
            pool.close();
        }
    }

    private HikariDataSource createPool(UUID tenantId) {
        TenantRecord tenant = directory.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Unknown tenant: " + tenantId));
        HikariConfig config = new HikariConfig();
        config.setPoolName("tenant-" + tenant.slug());
        config.setJdbcUrl(credentials.jdbcUrl(tenant));
        config.setUsername(tenant.appUsername());
        config.setPassword(credentials.appPassword(tenant));
        config.setMaximumPoolSize(properties.pool().maxSize());
        config.setMinimumIdle(0);
        config.setIdleTimeout(properties.pool().idleTimeout().toMillis());
        config.setConnectionTimeout(properties.pool().connectionTimeout().toMillis());
        config.setInitializationFailTimeout(-1);
        log.info("Tenant connection pool yaradılır: {}", tenant.slug());
        return new HikariDataSource(config);
    }

    @Override
    public void destroy() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
