package az.cafeloyalty.dev.seed;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import az.cafeloyalty.shared.i18n.Language;
import org.springframework.context.annotation.Profile;

/**
 * Real görünən test müştəriləri və ziyarət tarixçəsi yaradır (yalnız dev). Sabit toxumla (seed) eyni nəticə
 * alınır ki, testlər və nümayiş təkrarlana bilsin. Tarixçə sui-istifadə qaydalarına tam uyğundur:
 * iki möhür arasında ən az 2 saat (qayda 20 dəqiqədir) və gündə ən çox 2 ziyarət (qayda 3-dür).
 */
@Profile("dev")
public final class DemoDataGenerator {

    public static final int HISTORY_DAYS = 90;
    static final Duration MIN_GAP_SAME_DAY = Duration.ofMinutes(150);

    private static final String[] OPERATORS = {"50", "51", "55", "70", "77", "99", "10"};
    private static final String[] NAMES_AZ = {"Aysel", "Leyla", "Nigar", "Günel", "Səbinə", "Aynur", "Fidan", "Nərmin",
            "Ülviyyə", "Kamran", "Elvin", "Rauf", "Orxan", "Tural", "Elnur", "Murad", "Fərid", "Ramil", "Rəşad", "Zaur",
            "Nicat", "Ceyhun", "Aytən", "Lalə", "Könül", "Şəbnəm", "Gülnar", "İlkin", "Emin", "Sevinc"};
    private static final String[] NAMES_RU = {"Анна", "Мария", "Елена", "Ольга", "Наталья", "Ирина", "Алексей",
            "Дмитрий", "Сергей", "Андрей", "Игорь", "Максим", "Екатерина", "Татьяна", "Павел"};
    private static final String[] NAMES_EN = {"Emily", "Sarah", "Olivia", "James", "Daniel", "Michael", "Sophie",
            "Liam", "Emma", "Noah", "Grace", "Ryan"};

    // Saatlara görə ziyarət ehtimalının çəkiləri (səhər, nahar və axşam pikləri)
    private static final int[] WEEKDAY_HOURS = {7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21};
    private static final int[] WEEKDAY_WEIGHTS = {4, 10, 12, 7, 4, 6, 9, 7, 4, 4, 7, 8, 6, 3, 2};
    private static final int[] WEEKEND_HOURS = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21};
    private static final int[] WEEKEND_WEIGHTS = {4, 7, 9, 9, 8, 8, 8, 8, 7, 6, 5, 4, 3};

    private DemoDataGenerator() {
    }

    public enum Segment {
        /** Həftədə 3–5 dəfə gələn daimi müştərilər. */
        REGULAR,
        /** Həftədə 1–2 dəfə. */
        FREQUENT,
        /** Ayda bir neçə dəfə. */
        OCCASIONAL,
        /** Əvvəllər gəlib, son 1–2 ayda gəlməyən ("itirilmiş"). */
        CHURNED
    }

    public record Visit(Instant at, int branchIndex) {
    }

    public record DemoCustomer(String phoneE164, String name, Language language, Segment segment,
                               int homeBranch, List<Visit> visits) {
        public Instant registeredAt() {
            return visits.getFirst().at();
        }

        public Visit lastVisit() {
            return visits.getLast();
        }
    }

    public static List<DemoCustomer> generate(long seed, int count, int branchCount, Instant now, ZoneId zone) {
        Random random = new Random(seed);
        Set<String> phones = new HashSet<>();
        List<DemoCustomer> customers = new ArrayList<>(count);
        LocalDate today = now.atZone(zone).toLocalDate();
        while (customers.size() < count) {
            Segment segment = pickSegment(random);
            Language language = pickLanguage(random);
            String phone = uniquePhone(random, phones);
            String name = random.nextDouble() < 0.15 ? null : pickName(random, language);
            int homeBranch = branchCount == 1 || random.nextDouble() < 0.7 ? 0 : 1 + random.nextInt(branchCount - 1);
            List<Visit> visits = visits(random, segment, homeBranch, branchCount, today, now, zone);
            if (!visits.isEmpty()) {
                customers.add(new DemoCustomer(phone, name, language, segment, homeBranch, visits));
            }
        }
        return customers;
    }

    private static List<Visit> visits(Random random, Segment segment, int homeBranch, int branchCount,
                                      LocalDate today, Instant now, ZoneId zone) {
        double weeklyRate = switch (segment) {
            case REGULAR -> 3 + random.nextDouble() * 2;
            case FREQUENT -> 1 + random.nextDouble();
            case OCCASIONAL -> 0.3 + random.nextDouble() * 0.5;
            case CHURNED -> 0.8 + random.nextDouble();
        };
        int registrationDaysAgo = switch (segment) {
            case REGULAR -> 70 + random.nextInt(HISTORY_DAYS - 70);
            case CHURNED -> 60 + random.nextInt(HISTORY_DAYS - 60);
            default -> 3 + random.nextInt(HISTORY_DAYS - 3);
        };
        int lastActiveDaysAgo = segment == Segment.CHURNED ? 35 + random.nextInt(20) : 0;

        List<Visit> visits = new ArrayList<>();
        for (int daysAgo = registrationDaysAgo; daysAgo >= lastActiveDaysAgo; daysAgo--) {
            LocalDate day = today.minusDays(daysAgo);
            boolean firstDay = daysAgo == registrationDaysAgo;
            if (!firstDay && random.nextDouble() >= Math.min(0.9, weeklyRate / 7.0)) {
                continue;
            }
            Instant first = visitTime(random, day, zone);
            if (first.isAfter(now)) {
                continue;
            }
            visits.add(new Visit(first, branchFor(random, homeBranch, branchCount)));
            // Bəzən eyni gündə ikinci ziyarət — qaydalara uyğun aralıqla
            if (random.nextDouble() < 0.08) {
                Instant second = first.plus(MIN_GAP_SAME_DAY).plus(Duration.ofMinutes(random.nextInt(180)));
                boolean sameDay = second.atZone(zone).toLocalDate().equals(day);
                if (sameDay && second.atZone(zone).toLocalTime().isBefore(LocalTime.of(22, 30)) && second.isBefore(now)) {
                    visits.add(new Visit(second, branchFor(random, homeBranch, branchCount)));
                }
            }
        }
        visits.sort(Comparator.comparing(Visit::at));
        return visits;
    }

    private static Instant visitTime(Random random, LocalDate day, ZoneId zone) {
        boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
        int hour = weighted(random, weekend ? WEEKEND_HOURS : WEEKDAY_HOURS, weekend ? WEEKEND_WEIGHTS : WEEKDAY_WEIGHTS);
        return ZonedDateTime.of(day, LocalTime.of(hour, random.nextInt(60), random.nextInt(60)), zone).toInstant();
    }

    private static int branchFor(Random random, int homeBranch, int branchCount) {
        if (branchCount == 1 || random.nextDouble() < 0.85) {
            return homeBranch;
        }
        return random.nextInt(branchCount);
    }

    private static Segment pickSegment(Random random) {
        double r = random.nextDouble();
        if (r < 0.15) {
            return Segment.REGULAR;
        }
        if (r < 0.45) {
            return Segment.FREQUENT;
        }
        return r < 0.80 ? Segment.OCCASIONAL : Segment.CHURNED;
    }

    private static Language pickLanguage(Random random) {
        double r = random.nextDouble();
        if (r < 0.65) {
            return Language.AZ;
        }
        return r < 0.85 ? Language.RU : Language.EN;
    }

    private static String pickName(Random random, Language language) {
        String[] names = switch (language) {
            case AZ -> NAMES_AZ;
            case RU -> NAMES_RU;
            case EN -> NAMES_EN;
        };
        return names[random.nextInt(names.length)];
    }

    private static String uniquePhone(Random random, Set<String> used) {
        String phone;
        do {
            phone = "+994" + OPERATORS[random.nextInt(OPERATORS.length)] + String.format(Locale.ROOT, "%07d", random.nextInt(10_000_000));
        } while (!used.add(phone));
        return phone;
    }

    private static int weighted(Random random, int[] values, int[] weights) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int r = random.nextInt(total);
        for (int i = 0; i < values.length; i++) {
            r -= weights[i];
            if (r < 0) {
                return values[i];
            }
        }
        return values[values.length - 1];
    }
}
