package az.cafeloyalty.shared.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Vaxt həmişə inject olunan {@link Clock}-dan götürülür — testlərdə vaxtı idarə etmək mümkün olsun deyə. */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
