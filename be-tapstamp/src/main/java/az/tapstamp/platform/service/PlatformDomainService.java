package az.tapstamp.platform.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.config.AppProperties;
import az.tapstamp.security.admin.AdminAuthService;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.security.admin.AdminRealms;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.tenancy.registry.TenantDirectory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Şirkətin domenləri (məs. {@code gloriyajeans.mockcoffe.com}). Tenant yalnız {@code Host} başlığından müəyyən olunur,
 * ona görə domen dəyişikliyi marşrutlaşdırmaya dərhal təsir edir: domen keşi təmizlənir, əsas domenin dəyişməsi və
 * domenin silinməsi step-up tələb edir. Əsas domen silinə bilməz (çiplərdəki linklər ona yazılıb).
 */
@Service
public class PlatformDomainService {

    public record Domain(UUID id, String hostname, boolean primary, String tlsStatus, Instant createdAt) {
    }

    private final JdbcClient platform;
    private final TenantDirectory directory;
    private final AdminAuthService auth;
    private final AdminRealms realms;
    private final AppProperties appProperties;
    private final Clock clock;

    public PlatformDomainService(@Qualifier("platformJdbc") JdbcClient platform, TenantDirectory directory,
                                 AdminAuthService auth, AdminRealms realms, AppProperties appProperties,
                                 Clock clock) {
        this.platform = platform;
        this.directory = directory;
        this.auth = auth;
        this.realms = realms;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    public List<Domain> list(UUID tenantId) {
        requireTenant(tenantId);
        return platform.sql("""
                        SELECT id, hostname, is_primary, tls_status, created_at FROM tenant_domains
                        WHERE tenant_id = :id ORDER BY is_primary DESC, hostname""")
                .param("id", tenantId)
                .query((rs, n) -> new Domain(rs.getObject("id", UUID.class), rs.getString("hostname"),
                        rs.getBoolean("is_primary"), rs.getString("tls_status"), instant(rs.getTimestamp("created_at"))))
                .list();
    }

    /** Yeni domen (kiçik hərflə, portsuz). Platforma domeni və başqa şirkətin domeni qəbul olunmur. */
    public Domain add(AdminPrincipal principal, UUID tenantId, String hostname) {
        requireTenant(tenantId);
        String host = normalize(hostname);
        UUID id = Ids.newId();
        boolean exists = platform.sql("SELECT count(*) FROM tenant_domains WHERE hostname = :host")
                .param("host", host).query(Integer.class).single() > 0;
        if (exists) {
            throw new ApiException(ErrorCode.ALREADY_EXISTS);
        }
        platform.sql("""
                        INSERT INTO tenant_domains (id, tenant_id, hostname, is_primary, tls_status, created_at)
                        VALUES (:id, :tenantId, :host, false, 'PENDING', :now)""")
                .param("id", id)
                .param("tenantId", tenantId)
                .param("host", host)
                .param("now", clock.instant().atOffset(ZoneOffset.UTC))
                .update();
        directory.evictCaches();
        realms.audit(AdminRealm.PLATFORM, "TENANT_DOMAIN_ADDED", principal.userId(), "tenant", tenantId,
                Map.of("hostname", host));
        return domain(tenantId, id);
    }

    /** Əsas domenin dəyişməsi (step-up): dəvət və bərpa linkləri, yeni çiplər bu domenə yazılır. */
    public Domain makePrimary(AdminPrincipal principal, UUID tenantId, UUID domainId) {
        auth.requireStepUp(principal);
        Domain target = domain(tenantId, domainId);
        if (!target.primary()) {
            realms.tx(AdminRealm.PLATFORM).executeWithoutResult(status -> {
                platform.sql("UPDATE tenant_domains SET is_primary = false WHERE tenant_id = :id AND is_primary")
                        .param("id", tenantId).update();
                platform.sql("UPDATE tenant_domains SET is_primary = true WHERE id = :id").param("id", domainId).update();
            });
            directory.evictCaches();
            realms.audit(AdminRealm.PLATFORM, "TENANT_PRIMARY_DOMAIN_CHANGED", principal.userId(), "tenant", tenantId,
                    Map.of("hostname", target.hostname()));
        }
        return domain(tenantId, domainId);
    }

    /** Domenin silinməsi (step-up); əsas domen silinə bilməz. */
    public void remove(AdminPrincipal principal, UUID tenantId, UUID domainId) {
        auth.requireStepUp(principal);
        Domain target = domain(tenantId, domainId);
        if (target.primary()) {
            throw new ApiException(ErrorCode.DOMAIN_IS_PRIMARY);
        }
        platform.sql("DELETE FROM tenant_domains WHERE id = :id").param("id", domainId).update();
        directory.evictCaches();
        realms.audit(AdminRealm.PLATFORM, "TENANT_DOMAIN_REMOVED", principal.userId(), "tenant", tenantId,
                Map.of("hostname", target.hostname()));
    }

    private Domain domain(UUID tenantId, UUID domainId) {
        return list(tenantId).stream().filter(d -> d.id().equals(domainId)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private void requireTenant(UUID tenantId) {
        directory.findById(tenantId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private String normalize(String hostname) {
        String host = hostname == null ? "" : hostname.strip().toLowerCase(Locale.ROOT);
        if (host.length() > 253 || !host.matches("^[a-z0-9-]+(\\.[a-z0-9-]+)+$") || host.startsWith("-")
                || host.equals(appProperties.platformDomain().toLowerCase(Locale.ROOT))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return host;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
