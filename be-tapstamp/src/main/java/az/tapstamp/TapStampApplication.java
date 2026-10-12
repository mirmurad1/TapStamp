package az.tapstamp;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * TapStamp platformasının giriş nöqtəsi.
 *
 * <p>SQL skript inisializasiyası söndürülüb: sxemi {@code DatabaseMigrator} (Flyway) idarə edir, əsas DataSource
 * isə tenant marşrutlayıcısıdır və başlanğıcda heç bir tenant konteksti olmadan bağlantı vermir.
 *
 * <p>Birdəfəlik əmrlər ({@code --create-platform-admin}, {@code --provision-tenant}) işini görəndən sonra tətbiq
 * bağlanır: əks halda fon işləri (planlaşdırıcı) prosesi açıq saxlayardı və əmr heç vaxt bitməzdi.
 */
@SpringBootApplication(exclude = DataSourceInitializationAutoConfiguration.class)
@ConfigurationPropertiesScan
public class TapStampApplication {

    private static final List<String> ONE_SHOT_COMMANDS = List.of("--create-platform-admin", "--provision-tenant");

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(TapStampApplication.class, args);
        if (isOneShotCommand(args)) {
            System.exit(SpringApplication.exit(context));
        }
    }

    static boolean isOneShotCommand(String[] args) {
        return Arrays.stream(args).anyMatch(arg -> ONE_SHOT_COMMANDS.stream()
                .anyMatch(command -> arg.equals(command) || arg.startsWith(command + "=")));
    }
}
