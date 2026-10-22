package az.tapstamp.analytics;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.analytics.repository.AnalyticsMapper.LanguageCount;
import az.tapstamp.analytics.repository.AnalyticsMapper.OverviewCounts;
import az.tapstamp.analytics.repository.AnalyticsMapper.RetentionCounts;
import az.tapstamp.analytics.repository.AnalyticsMapper.Scope;
import az.tapstamp.analytics.repository.AnalyticsMapper;
import az.tapstamp.company.service.CompanySettings;
import az.tapstamp.plan.Feature;
import az.tapstamp.plan.PlanService;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.security.admin.BranchScope;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * İdarəetmə panelinin göstəriciləri. Bütün "gün" hesablamaları şirkətin saat qurşağındadır; yalnız CONFIRMED möhürlər
 * sayılır. Menecer yalnız öz filiallarının məlumatını görür ({@link BranchScope}). Tərifləri: {@code docs/metrics.md}.
 */
@Service
public class AnalyticsService {

    /** Bir sorğuda ən uzun dövr — böyük aralıqlarda bazanı yükləməmək üçün. */
    static final int MAX_PERIOD_DAYS = 366;

    public enum Granularity { DAY, WEEK }

    /** Seçilmiş dövr (hər iki tarix daxil) və filial çərçivəsi. */
    public record Query(LocalDate from, LocalDate to, BranchScope scope, ZoneId zone) {
    }

    public record Overview(long totalCustomers, long active7, long active30, long active90, long newThisWeek,
                           long newThisMonth, long stampsInPeriod, long newCustomersInPeriod, long rewardsEarned,
                           long rewardsRedeemed, Double redemptionRate) {
    }

    public record SeriesPoint(LocalDate date, long stamps, long rewardsRedeemed) {
    }

    /** {@code dayOfWeek}: 1 — bazar ertəsi … 7 — bazar (ISO); {@code hour}: 0–23. */
    public record HeatCell(int dayOfWeek, int hour, long stamps) {
    }

    public record Retention(long cohort, long returned, Double rate) {
    }

    public record LanguageShare(String language, long customers) {
    }

    private final AnalyticsMapper analytics;
    private final CompanySettings company;
    private final Clock clock;
    private final PlanService plans;

    public AnalyticsService(AnalyticsMapper analytics, CompanySettings company, Clock clock, PlanService plans) {
        this.analytics = analytics;
        this.company = company;
        this.clock = clock;
        this.plans = plans;
    }

    /** Parametrləri yoxlayır; tarix verilməyibsə son 30 gün (bu gün daxil). */
    public Query query(AdminPrincipal principal, LocalDate from, LocalDate to, UUID branchId) {
        ZoneId zone = company.current().timezone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= MAX_PERIOD_DAYS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (branchId != null && principal.hasRole(AdminRole.COMPANY_OWNER)) {
            // Sahibin filial seçməsi — filialların müqayisəsi (menecer həmişə öz filiallarını görür)
            plans.require(Feature.BRANCH_COMPARISON);
        }
        return new Query(start, end, BranchScope.of(principal, branchId), zone);
    }

    public Overview overview(Query q) {
        LocalDate today = LocalDate.now(clock.withZone(q.zone()));
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        LocalDate monthStart = today.withDayOfMonth(1);
        OverviewCounts counts = analytics.overview(scope(q), today, weekStart, monthStart);
        return new Overview(counts.total(), counts.active7(), counts.active30(), counts.active90(), counts.newWeek(),
                counts.newMonth(), counts.stamps(), counts.newPeriod(), counts.earned(), counts.redeemed(),
                counts.earned() == 0 ? null : (double) counts.redeemed() / counts.earned());
    }

    /** Möhürlər və təhvil verilmiş hədiyyələr — boş günlər də (0 ilə) qaytarılır. Həftə bazar ertəsindən başlayır. */
    public List<SeriesPoint> series(Query q, Granularity granularity) {
        return analytics.series(scope(q), granularity == Granularity.WEEK ? "week" : "day");
    }

    /** Həftənin günü × saat üzrə möhürlər (şirkətin yerli vaxtı ilə); yalnız sıfırdan böyük xanalar. */
    public List<HeatCell> heatmap(Query q) {
        return analytics.heatmap(scope(q));
    }

    /**
     * Geri qayıtma: dövrdə qeydiyyatdan keçən (və ilk möhürünü almış) müştərilərdən ilk möhür günündən sonrakı 30 gün
     * ərzində başqa bir gündə yenidən möhür alanların payı.
     */
    public Retention retention(Query q) {
        RetentionCounts counts = analytics.retention(scope(q));
        return new Retention(counts.cohort(), counts.returned(),
                counts.cohort() == 0 ? null : (double) counts.returned() / counts.cohort());
    }

    /** Müştərilərin interfeys dili üzrə bölgüsü (az, en, ru — hamısı, sıfır olsa da). */
    public List<LanguageShare> languages(Query q) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("az", 0L);
        counts.put("en", 0L);
        counts.put("ru", 0L);
        for (LanguageCount count : analytics.languages(scope(q))) {
            counts.put(count.language(), count.customers());
        }
        List<LanguageShare> result = new ArrayList<>();
        counts.forEach((lang, n) -> result.add(new LanguageShare(lang, n)));
        return result;
    }

    private static Scope scope(Query q) {
        return new Scope(q.from(), q.to(), q.zone().getId(), q.scope().all(), q.scope().branchIds());
    }
}
