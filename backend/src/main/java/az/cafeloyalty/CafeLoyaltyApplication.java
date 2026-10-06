package az.cafeloyalty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Kafe loyallıq platformasının giriş nöqtəsi. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CafeLoyaltyApplication {

    public static void main(String[] args) {
        SpringApplication.run(CafeLoyaltyApplication.class, args);
    }
}
