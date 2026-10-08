package az.tapstamp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;

/**
 * TapStamp platformasının giriş nöqtəsi.
 *
 * <p>SQL skript inisializasiyası söndürülüb: sxemi {@code DatabaseMigrator} (Flyway) idarə edir, əsas DataSource
 * isə tenant marşrutlayıcısıdır və başlanğıcda heç bir tenant konteksti olmadan bağlantı vermir.
 */
@SpringBootApplication(exclude = DataSourceInitializationAutoConfiguration.class)
@ConfigurationPropertiesScan
public class TapStampApplication {

    public static void main(String[] args) {
        SpringApplication.run(TapStampApplication.class, args);
    }
}
