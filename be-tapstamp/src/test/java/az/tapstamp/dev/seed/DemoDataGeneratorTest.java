package az.tapstamp.dev.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import az.tapstamp.dev.seed.DemoDataGenerator.DemoCustomer;
import az.tapstamp.dev.seed.DemoDataGenerator.Visit;
import az.tapstamp.shared.i18n.Language;
import org.junit.jupiter.api.Test;

class DemoDataGeneratorTest {

    private static final ZoneId BAKU = ZoneId.of("Asia/Baku");
    private static final Instant NOW = Instant.parse("2026-10-06T08:00:00Z");

    private final List<DemoCustomer> customers = DemoDataGenerator.generate(20261006L, 150, 2, NOW, BAKU);

    @Test
    void sameSeedProducesSameDataset() {
        List<DemoCustomer> again = DemoDataGenerator.generate(20261006L, 150, 2, NOW, BAKU);
        assertThat(again).isEqualTo(customers);
    }

    @Test
    void generatesRequestedNumberOfUniqueAzerbaijaniMobileNumbers() {
        assertThat(customers).hasSize(150);
        assertThat(customers.stream().map(DemoCustomer::phoneE164).distinct()).hasSize(150);
        assertThat(customers).allSatisfy(c -> assertThat(c.phoneE164()).matches("^\\+994(50|51|55|70|77|99|10)\\d{7}$"));
    }

    @Test
    void historyFitsTheLast90DaysAndRespectsAntiAbuseRules() {
        Instant start = NOW.minus(Duration.ofDays(DemoDataGenerator.HISTORY_DAYS + 1));
        for (DemoCustomer customer : customers) {
            List<Visit> visits = customer.visits();
            assertThat(visits).isNotEmpty();
            assertThat(visits).allSatisfy(v -> assertThat(v.at()).isBetween(start, NOW));
            for (int i = 1; i < visits.size(); i++) {
                Duration gap = Duration.between(visits.get(i - 1).at(), visits.get(i).at());
                assertThat(gap).as("iki möhür arasında ən az 20 dəqiqə").isGreaterThanOrEqualTo(Duration.ofMinutes(20));
            }
            Map<LocalDate, Long> perDay = visits.stream()
                    .collect(Collectors.groupingBy(v -> v.at().atZone(BAKU).toLocalDate(), Collectors.counting()));
            assertThat(perDay.values()).allSatisfy(count -> assertThat(count).isLessThanOrEqualTo(3));
        }
    }

    @Test
    void languagesAndSegmentsLookRealistic() {
        Map<Language, Long> languages = customers.stream()
                .collect(Collectors.groupingBy(DemoCustomer::language, Collectors.counting()));
        assertThat(languages.get(Language.AZ)).isGreaterThan(languages.get(Language.RU));
        assertThat(languages.get(Language.RU)).isPositive();
        assertThat(languages.get(Language.EN)).isPositive();

        Map<DemoDataGenerator.Segment, Long> segments = customers.stream()
                .collect(Collectors.groupingBy(DemoCustomer::segment, Collectors.counting()));
        assertThat(segments).containsKeys(DemoDataGenerator.Segment.values());

        // "İtirilmiş" müştərilər son 30 gündə gəlməyib
        assertThat(customers.stream().filter(c -> c.segment() == DemoDataGenerator.Segment.CHURNED))
                .allSatisfy(c -> assertThat(c.lastVisit().at()).isBefore(NOW.minus(Duration.ofDays(30))));
    }

    @Test
    void someCustomersCollectEnoughStampsForRewards() {
        long withReward = customers.stream().filter(c -> c.visits().size() >= 9).count();
        assertThat(withReward).isGreaterThan(20);
        assertThat(customers.stream().collect(Collectors.groupingBy(DemoCustomer::homeBranch, Collectors.counting())))
                .containsKeys(0, 1);
        assertThat(customers.stream().map(DemoCustomer::name).filter(n -> n != null).map(Function.identity()))
                .anySatisfy(name -> assertThat(name).containsAnyOf("ə", "ü", "ş", "ğ", "ı", "ç", "ö", "İ"));
    }
}
