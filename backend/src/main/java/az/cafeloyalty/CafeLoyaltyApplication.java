package az.cafeloyalty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;

/**
 * Kafe loyallıq platformasının giriş nöqtəsi.
 *
 * <p>SQL skript inisializasiyası söndürülüb: sxemi {@code DatabaseMigrator} (Flyway) idarə edir, əsas DataSource
 * isə tenant marşrutlayıcısıdır və başlanğıcda heç bir tenant konteksti olmadan bağlantı vermir.
 */
@SpringBootApplication(exclude = DataSourceInitializationAutoConfiguration.class)
@ConfigurationPropertiesScan
public class CafeLoyaltyApplication {

    public static void main(String[] args) {
        SpringApplication.run(CafeLoyaltyApplication.class, args);
    }
}
