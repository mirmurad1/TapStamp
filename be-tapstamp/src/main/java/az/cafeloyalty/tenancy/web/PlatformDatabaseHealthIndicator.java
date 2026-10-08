package az.cafeloyalty.tenancy.web;

import java.sql.Connection;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Hazırlıq yoxlaması: platforma bazası əlçatandırmı. Standart DB health indikatoru söndürülüb, çünki əsas
 * DataSource tenant marşrutlayıcısıdır və tenant konteksti olmadan bağlantı vermir.
 */
@Component("platformDatabase")
public class PlatformDatabaseHealthIndicator implements HealthIndicator {

    private final DataSource platformDataSource;

    public PlatformDatabaseHealthIndicator(@Qualifier("platformDataSource") DataSource platformDataSource) {
        this.platformDataSource = platformDataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = platformDataSource.getConnection()) {
            return connection.isValid(2) ? Health.up().build() : Health.down().build();
        } catch (Exception e) {
            return Health.down().build();
        }
    }
}
