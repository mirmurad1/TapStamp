package az.tapstamp.tenancy.registry;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.tenancy.TenantInfo;
import az.tapstamp.tenancy.TenantStatus;
import az.tapstamp.tenancy.repository.TenantRegistryMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

/**
 * Platforma reyestri: tenant-lar və onların domenləri. Domen → tenant axtarışı hər sorğuda lazım olduğu
 * üçün qısa müddət keşlənir; status dəyişəndə keş təmizlənir.
 */
@Component
public class TenantDirectory {

    private final TenantRegistryMapper registry;
    private final Cache<String, Optional<TenantInfo>> byHost = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    public TenantDirectory(TenantRegistryMapper registry) {
        this.registry = registry;
    }

    /** Domen adına görə tenant (kiçik hərflə, portsuz). */
    public Optional<TenantInfo> findByHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return byHost.get(normalized, h -> registry.findByHost(h).map(TenantRecord::toInfo));
    }

    public Optional<TenantRecord> findById(UUID id) {
        return registry.findById(id);
    }

    public Optional<TenantRecord> findBySlug(String slug) {
        return registry.findBySlug(slug);
    }

    public List<TenantRecord> findAll() {
        return registry.findAll();
    }

    public Optional<String> primaryHost(UUID tenantId) {
        return registry.findPrimaryHost(tenantId);
    }

    public List<String> hostnames(UUID tenantId) {
        return registry.findHostnames(tenantId);
    }

    public void updateStatus(UUID tenantId, TenantStatus status, String error) {
        registry.updateStatus(tenantId, status, error);
        evictCaches();
    }

    public void evictCaches() {
        byHost.invalidateAll();
    }

}
