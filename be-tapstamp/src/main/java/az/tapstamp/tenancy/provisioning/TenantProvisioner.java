package az.tapstamp.tenancy.provisioning;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.security.crypto.JdbcDataKeyProvider;
import az.tapstamp.security.crypto.KeyEncryptionService;
import az.tapstamp.security.crypto.KeyPurpose;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.persistence.LocalizedTextConverter;
import az.tapstamp.tenancy.TenancyProperties;
import az.tapstamp.tenancy.TenantStatus;
import az.tapstamp.tenancy.datasource.TenantCredentials;
import az.tapstamp.tenancy.migration.DatabaseMigrator;
import az.tapstamp.tenancy.registry.TenantDirectory;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

/**
 * Yeni şirkət üçün ayrıca baza, iki DB rolu (sxem sahibi və tətbiq), miqrasiyalar və ilkin məlumatlar yaradır.
 *
 * <p>Proses idempotentdir: yarımçıq qalarsa (məs. bağlantı kəsilərsə) təkrar çağırış qaldığı yerdən davam edir.
 * Rol və baza adları slug-dan törədilir və ağ siyahı ilə yoxlanılır ({@link DbIdentifiers}).
 */
@Service
// JdbcClient-in :ad parametrləri JPA sorğusu deyil — IDE-nin JPA yoxlaması burada tətbiq olunmur
@SuppressWarnings("JpaQueryApiInspection")
public class TenantProvisioner {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioner.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final LocalizedTextConverter TEXT = new LocalizedTextConverter();

    private final TenancyProperties properties;
    private final TenantDirectory directory;
    private final TenantCredentials credentials;
    private final DatabaseMigrator migrator;
    private final KeyEncryptionService kek;
    private final JdbcClient platform;

    public TenantProvisioner(TenancyProperties properties, TenantDirectory directory, TenantCredentials credentials,
                             DatabaseMigrator migrator, KeyEncryptionService kek,
                             @Qualifier("platformJdbc") JdbcClient platform) {
        this.properties = properties;
        this.directory = directory;
        this.credentials = credentials;
        this.migrator = migrator;
        this.kek = kek;
        this.platform = platform;
    }

    public TenantRecord provision(ProvisionTenantCommand command) {
        if (!properties.provisioner().enabled()) {
            throw new IllegalStateException("Tenant provisioning is disabled in this instance");
        }
        String slug = DbIdentifiers.requireSlug(command.slug());
        String host = DbIdentifiers.requireHostname(command.primaryDomain().trim().toLowerCase(Locale.ROOT));
        Objects.requireNonNull(ZoneId.of(command.timezone()), "timezone");
        Objects.requireNonNull(command.displayName(), "displayName");
        Objects.requireNonNull(command.defaultLanguage(), "defaultLanguage");

        TenantRecord tenant = directory.findBySlug(slug).orElseGet(() -> register(command, slug));
        if (tenant.status() == TenantStatus.ACTIVE) {
            ensureDomain(tenant.id(), host);
            return tenant;
        }
        if (tenant.status() != TenantStatus.PROVISIONING) {
            throw new IllegalStateException("Tenant '" + slug + "' cannot be provisioned in status " + tenant.status());
        }

        log.info("Tenant provisioning başladı: {}", slug);
        String ownerPassword = credentials.ownerPassword(tenant);
        createRolesAndDatabase(tenant, ownerPassword, credentials.appPassword(tenant));
        secureDatabase(tenant, ownerPassword);
        migrator.migrateTenant(tenant);
        initializeTenantData(tenant, ownerPassword, command);
        ensureDomain(tenant.id(), host);
        directory.updateStatus(tenant.id(), TenantStatus.ACTIVE, null);
        log.info("Tenant provisioning tamamlandı: {} ({})", slug, host);
        return directory.findById(tenant.id()).orElseThrow();
    }

    private TenantRecord register(ProvisionTenantCommand command, String slug) {
        UUID id = Ids.newId();
        platform.sql("""
                        INSERT INTO tenants (id, slug, display_name, status, default_language, timezone,
                                             db_host, db_port, db_name,
                                             db_owner_username, db_owner_password_enc,
                                             db_app_username, db_app_password_enc, created_at, updated_at)
                        VALUES (:id, :slug, CAST(:name AS jsonb), 'PROVISIONING', :lang, :tz,
                                :host, :port, :db, :owner, :ownerPwd, :app, :appPwd, now(), now())""")
                .param("id", id)
                .param("slug", slug)
                .param("name", TEXT.toJson(command.displayName()))
                .param("lang", command.defaultLanguage().tag())
                .param("tz", command.timezone())
                .param("host", properties.dbHost())
                .param("port", properties.dbPort())
                .param("db", DbIdentifiers.databaseName(slug))
                .param("owner", DbIdentifiers.ownerRole(slug))
                .param("ownerPwd", credentials.encryptOwnerPassword(id, randomPassword()))
                .param("app", DbIdentifiers.appRole(slug))
                .param("appPwd", credentials.encryptAppPassword(id, randomPassword()))
                .update();
        return directory.findById(id).orElseThrow();
    }

    /** Provisioner rolu ilə: iki DB rolu və baza. CREATE DATABASE tranzaksiyada işləmir — autocommit. */
    private void createRolesAndDatabase(TenantRecord tenant, String ownerPassword, String appPassword) {
        TenancyProperties.Provisioner provisioner = properties.provisioner();
        String url = properties.jdbcUrl(tenant.dbHost(), tenant.dbPort(), "postgres");
        try (Connection connection = DriverManager.getConnection(url, provisioner.username(), provisioner.password())) {
            connection.setAutoCommit(true);
            ensureRole(connection, tenant.ownerUsername(), ownerPassword);
            ensureRole(connection, tenant.appUsername(), appPassword);
            if (!exists(connection, "SELECT 1 FROM pg_database WHERE datname = ?", tenant.dbName())) {
                execute(connection, "CREATE DATABASE " + DbIdentifiers.quote(tenant.dbName())
                        + " OWNER " + DbIdentifiers.quote(tenant.ownerUsername())
                        + " ENCODING 'UTF8' TEMPLATE template0");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Tenant database creation failed for " + tenant.slug(), e);
        }
    }

    /** Sxem sahibi ilə: bazaya yalnız tətbiq rolu qoşula bilər, PUBLIC-in heç bir hüququ yoxdur. */
    private void secureDatabase(TenantRecord tenant, String ownerPassword) {
        String db = DbIdentifiers.quote(tenant.dbName());
        String app = DbIdentifiers.quote(tenant.appUsername());
        try (Connection connection = DriverManager.getConnection(
                credentials.jdbcUrl(tenant), tenant.ownerUsername(), ownerPassword)) {
            connection.setAutoCommit(true);
            execute(connection, "REVOKE ALL ON DATABASE " + db + " FROM PUBLIC");
            execute(connection, "GRANT CONNECT, TEMPORARY ON DATABASE " + db + " TO " + app);
            execute(connection, "REVOKE ALL ON SCHEMA public FROM PUBLIC");
            execute(connection, "GRANT USAGE ON SCHEMA public TO " + app);
        } catch (SQLException e) {
            throw new IllegalStateException("Securing tenant database failed for " + tenant.slug(), e);
        }
    }

    /** İlkin məlumatlar: şirkət profili, standart sui-istifadə qaydaları, məlumat açarları (DEK). */
    private void initializeTenantData(TenantRecord tenant, String ownerPassword, ProvisionTenantCommand command) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                credentials.jdbcUrl(tenant), tenant.ownerUsername(), ownerPassword);
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                        INSERT INTO company_profile (singleton, name, timezone, default_language, updated_at)
                        VALUES (true, CAST(:name AS jsonb), :tz, :lang, now())
                        ON CONFLICT (singleton) DO NOTHING""")
                .param("name", TEXT.toJson(command.displayName()))
                .param("tz", command.timezone())
                .param("lang", command.defaultLanguage().tag())
                .update();
        jdbc.sql("""
                        INSERT INTO stamp_policies (id, branch_id, updated_at) VALUES (:id, NULL, now())
                        ON CONFLICT DO NOTHING""")
                .param("id", Ids.newId())
                .update();
        new JdbcDataKeyProvider(jdbc, kek, () -> "t:" + tenant.id())
                .ensureKeys(KeyPurpose.PII, KeyPurpose.NFC_KEYS, KeyPurpose.TOTP);
    }

    private void ensureDomain(UUID tenantId, String host) {
        Optional<UUID> owner = platform.sql("SELECT tenant_id FROM tenant_domains WHERE hostname = :host")
                .param("host", host)
                .query(UUID.class)
                .optional();
        if (owner.isPresent()) {
            if (!owner.get().equals(tenantId)) {
                throw new IllegalStateException("Domain is already assigned to another tenant: " + host);
            }
            return;
        }
        boolean hasPrimary = directory.primaryHost(tenantId).isPresent();
        platform.sql("""
                        INSERT INTO tenant_domains (id, tenant_id, hostname, is_primary, tls_status, created_at)
                        VALUES (:id, :tenantId, :host, :primary, 'PENDING', now())""")
                .param("id", Ids.newId())
                .param("tenantId", tenantId)
                .param("host", host)
                .param("primary", !hasPrimary)
                .update();
        directory.evictCaches();
    }

    private static void ensureRole(Connection connection, String role, String password) throws SQLException {
        String quoted = DbIdentifiers.quote(role);
        if (exists(connection, "SELECT 1 FROM pg_roles WHERE rolname = ?", role)) {
            execute(connection, "ALTER ROLE " + quoted + " WITH LOGIN PASSWORD " + DbIdentifiers.passwordLiteral(password));
        } else {
            execute(connection, "CREATE ROLE " + quoted + " WITH LOGIN NOINHERIT PASSWORD "
                    + DbIdentifiers.passwordLiteral(password));
        }
    }

    private static boolean exists(Connection connection, String sql, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    static String randomPassword() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
