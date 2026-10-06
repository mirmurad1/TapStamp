package az.cafeloyalty.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import az.cafeloyalty.support.IntegrationTest;
import az.cafeloyalty.support.TestDatabase;
import az.cafeloyalty.tenancy.datasource.TenantCredentials;
import az.cafeloyalty.tenancy.registry.TenantDirectory;
import az.cafeloyalty.tenancy.registry.TenantRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * DB səviyyəsində izolyasiya: hər tenant-ın tətbiq rolu yalnız öz bazasına qoşula bilir, DDL hüququ yoxdur,
 * audit və toxunuş jurnalını dəyişə bilmir. Tətbiq kodunda səhv olsa belə, bu hüquqlar qorunur.
 */
class TenantDatabaseIsolationIT extends IntegrationTest {

    @Autowired
    TenantDirectory directory;

    @Autowired
    TenantCredentials credentials;

    private TenantRecord tenant(String slug) {
        return directory.findBySlug(slug).orElseThrow();
    }

    private Connection connectAsApp(TenantRecord credentialsOf, TenantRecord database) throws SQLException {
        return DriverManager.getConnection(TestDatabase.jdbcUrl(database.dbName()),
                credentialsOf.appUsername(), credentials.appPassword(credentialsOf));
    }

    @Test
    void appRoleCannotConnectToAnotherTenantsDatabase() {
        TenantRecord alpha = tenant("alpha");
        TenantRecord beta = tenant("beta");
        assertThatThrownBy(() -> connectAsApp(alpha, beta).close())
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    @Test
    void provisionerCannotReadTenantDatabases() {
        TenantRecord alpha = tenant("alpha");
        assertThatThrownBy(() -> DriverManager.getConnection(TestDatabase.jdbcUrl(alpha.dbName()),
                "loyalty_provisioner", TestDatabase.PROVISIONER_PASSWORD).close())
                .isInstanceOf(SQLException.class);
    }

    @Test
    void appRoleHasNoDdlAndCannotTamperWithAuditTrail() throws SQLException {
        TenantRecord alpha = tenant("alpha");
        try (Connection connection = connectAsApp(alpha, alpha); Statement statement = connection.createStatement()) {
            assertThat(statement.executeQuery("SELECT count(*) FROM branches").next()).isTrue();

            assertDenied(statement, "CREATE TABLE evil (id int)");
            assertDenied(statement, "DROP TABLE customers");
            assertDenied(statement, "UPDATE audit_logs SET action = 'x'");
            assertDenied(statement, "DELETE FROM audit_logs");
            assertDenied(statement, "DELETE FROM tap_events");
            assertDenied(statement, "TRUNCATE tap_events");
            assertDenied(statement, "SELECT * FROM flyway_schema_history");
        }
    }

    @Test
    void appRoleCanUseRetentionFunctionsOnly() throws SQLException {
        TenantRecord alpha = tenant("alpha");
        try (Connection connection = connectAsApp(alpha, alpha); Statement statement = connection.createStatement()) {
            assertThat(statement.executeQuery("SELECT purge_tap_event_ips(now() - interval '90 days')").next()).isTrue();
        }
    }

    @Test
    void platformAppRoleCannotAlterMigrationHistory() throws SQLException {
        try (Connection connection = DriverManager.getConnection(TestDatabase.jdbcUrl("loyalty_platform"),
                "loyalty_platform_app", TestDatabase.PLATFORM_APP_PASSWORD);
             Statement statement = connection.createStatement()) {
            assertDenied(statement, "DELETE FROM flyway_schema_history");
            assertDenied(statement, "DELETE FROM platform_audit_logs");
            assertDenied(statement, "CREATE TABLE evil (id int)");
        }
    }

    private static void assertDenied(Statement statement, String sql) {
        assertThatThrownBy(() -> statement.execute(sql))
                .as(sql)
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(e.getMessage()).containsAnyOf("permission denied", "must be owner"));
    }
}
