package az.tapstamp.tenancy.datasource;

import java.util.Map;

import javax.sql.DataSource;

import az.tapstamp.security.crypto.CryptoProperties;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.security.crypto.JdbcDataKeyProvider;
import az.tapstamp.security.crypto.KeyEncryptionService;
import az.tapstamp.security.crypto.LocalKeyEncryptionService;
import az.tapstamp.security.crypto.PhoneHasher;
import az.tapstamp.tenancy.TenancyProperties;
import az.tapstamp.tenancy.TenantContext;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * İki növ baza üçün bağlantılar:
 * <ul>
 *   <li><b>platforma</b> — tenant reyestri; {@code platformJdbc} və {@code platformTransactionManager};</li>
 *   <li><b>tenant</b> — JPA və {@code tenantJdbc}; bağlantı cari tenant-ın bazasından verilir.</li>
 * </ul>
 * JPA {@link TenantRoutingDataSource} üzərində qurulub; Hibernate başlanğıcda bazaya qoşulmur (tenant hələ yoxdur),
 * ona görə dialekt açıq göstərilir.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "az.tapstamp")
public class TenancyPersistenceConfig {

    // --- Platforma bazası ---

    @Bean(destroyMethod = "close")
    HikariDataSource platformDataSource(TenancyProperties properties) {
        TenancyProperties.Platform platform = properties.platform();
        HikariConfig config = new HikariConfig();
        config.setPoolName("platform");
        config.setJdbcUrl(properties.jdbcUrl(properties.dbHost(), properties.dbPort(), platform.database()));
        config.setUsername(platform.appUsername());
        config.setPassword(platform.appPassword());
        config.setMaximumPoolSize(platform.maxPoolSize());
        config.setMinimumIdle(1);
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    @Bean
    JdbcClient platformJdbc(@Qualifier("platformDataSource") DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    @Bean
    PlatformTransactionManager platformTransactionManager(@Qualifier("platformDataSource") DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }

    @Bean
    TransactionTemplate platformTransactionTemplate(
            @Qualifier("platformTransactionManager") PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    // --- Tenant bazaları ---

    @Bean
    @Primary
    DataSource tenantDataSource(TenantDataSourceRegistry registry) {
        return new TenantRoutingDataSource(registry);
    }

    @Bean
    JdbcClient tenantJdbc(@Qualifier("tenantDataSource") DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(@Qualifier("tenantDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("az.tapstamp");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPersistenceUnitName("tenant");
        factory.setJpaPropertyMap(Map.of(
                // Başlanğıcda heç bir tenant bazasına qoşulmuruq; dialekt aşağıdakı versiya məlumatından seçilir
                "hibernate.boot.allow_jdbc_metadata_access", "false",
                "jakarta.persistence.database-product-name", "PostgreSQL",
                "jakarta.persistence.database-major-version", "16",
                "hibernate.hbm2ddl.auto", "none",
                "hibernate.jdbc.time_zone", "UTC",
                "hibernate.jdbc.batch_size", "50",
                "hibernate.order_inserts", "true",
                "hibernate.order_updates", "true",
                "hibernate.query.fail_on_pagination_over_collection_fetch", "true"));
        return factory;
    }

    @Bean
    @Primary
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    // --- Kriptoqrafiya ---

    @Bean
    KeyEncryptionService keyEncryptionService(CryptoProperties crypto) {
        return new LocalKeyEncryptionService(crypto.keks(), crypto.activeKekId());
    }

    @Bean
    JdbcDataKeyProvider platformDataKeys(@Qualifier("platformJdbc") JdbcClient jdbc, KeyEncryptionService kek) {
        return new JdbcDataKeyProvider(jdbc, kek, () -> "platform");
    }

    @Bean
    EnvelopeCipher platformCipher(@Qualifier("platformDataKeys") JdbcDataKeyProvider keys) {
        return new EnvelopeCipher(keys);
    }

    @Bean
    JdbcDataKeyProvider tenantDataKeys(@Qualifier("tenantJdbc") JdbcClient jdbc, KeyEncryptionService kek) {
        return new JdbcDataKeyProvider(jdbc, kek, () -> "t:" + TenantContext.require().id());
    }

    @Bean
    EnvelopeCipher tenantCipher(@Qualifier("tenantDataKeys") JdbcDataKeyProvider keys) {
        return new EnvelopeCipher(keys);
    }

    @Bean
    PhoneHasher phoneHasher(CryptoProperties crypto) {
        return new PhoneHasher(crypto.phoneHmacKey());
    }
}
