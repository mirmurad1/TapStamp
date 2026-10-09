package az.tapstamp.tenancy;

import java.time.Duration;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Verilənlər bazası topologiyası: bir platforma bazası və hər şirkət üçün ayrıca tenant bazası.
 *
 * @param dbHost      yeni tenant bazalarının yaradılacağı PostgreSQL serveri
 * @param dbPort      həmin serverin portu
 * @param platform    platforma bazasına qoşulma məlumatları
 * @param provisioner yeni baza və rol yaradan xüsusi DB rolu
 * @param pool        tenant connection pool-larının parametrləri
 */
@ConfigurationProperties("app.tenancy")
public record TenancyProperties(
        @DefaultValue("localhost") String dbHost,
        @DefaultValue("5432") int dbPort,
        @DefaultValue Platform platform,
        @DefaultValue Provisioner provisioner,
        @DefaultValue Pool pool) {

    public record Platform(
            @DefaultValue("loyalty_platform") String database,
            @DefaultValue("loyalty_platform_app") String appUsername,
            String appPassword,
            @DefaultValue("loyalty_platform_owner") String ownerUsername,
            String ownerPassword,
            @DefaultValue("10") int maxPoolSize) {

        @Override
        public @NonNull String toString() {
            return "Platform[database=" + database + ", appUsername=" + appUsername + "]";
        }
    }

    /**
     * @param enabled işləyən tətbiqdən provisioning-ə icazə; prod-da ayrıca, birdəfəlik əmrlə işlətmək tövsiyə olunur
     */
    public record Provisioner(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("loyalty_provisioner") String username,
            String password) {

        @Override
        public @NonNull String toString() {
            return "Provisioner[enabled=" + enabled + ", username=" + username + "]";
        }
    }

    public record Pool(
            @DefaultValue("3") int maxSize,
            @DefaultValue("5m") Duration idleTimeout,
            @DefaultValue("5s") Duration connectionTimeout) {
    }

    public String jdbcUrl(String host, int port, String database) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database + "?ApplicationName=tapstamp";
    }
}
