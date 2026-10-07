package az.cafeloyalty.analytics;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.cafeloyalty.company.service.CompanySettings;
import az.cafeloyalty.security.admin.AdminPrincipal;
import az.cafeloyalty.security.admin.BranchScope;
import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
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

    private final JdbcClient jdbc;
    private final CompanySettings company;
    private final Clock clock;

    public AnalyticsService(@Qualifier("tenantJdbc") JdbcClient jdbc, CompanySettings company, Clock clock) {
        this.jdbc = jdbc;
        this.company = company;
        this.clock = clock;
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
        return new Query(start, end, BranchScope.of(principal, branchId), zone);
    }

    public Overview overview(Query q) {
        LocalDate today = LocalDate.now(clock.withZone(q.zone()));
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        LocalDate monthStart = today.withDayOfMonth(1);
        Map<String, Object> row = jdbc.sql("""
                        WITH scoped AS (
                            SELECT c.id, (c.registered_at AT TIME ZONE :tz)::date AS reg_day FROM customers c
                            WHERE c.status <> 'DELETED'
                              AND (:all OR EXISTS (SELECT 1 FROM stamps s WHERE s.customer_id = c.id
                                                   AND s.branch_id IN (:branches)))
                        ),
                        visits AS (
                            SELECT s.customer_id, max(s.local_date) AS last_day FROM stamps s
                            WHERE s.status = 'CONFIRMED' AND (:all OR s.branch_id IN (:branches))
                            GROUP BY s.customer_id
                        )
                        SELECT
                            (SELECT count(*) FROM scoped) AS total,
                            (SELECT count(*) FROM visits WHERE last_day > :today - 7) AS active7,
                            (SELECT count(*) FROM visits WHERE last_day > :today - 30) AS active30,
                            (SELECT count(*) FROM visits WHERE last_day > :today - 90) AS active90,
                            (SELECT count(*) FROM scoped WHERE reg_day >= :weekStart) AS new_week,
                            (SELECT count(*) FROM scoped WHERE reg_day >= :monthStart) AS new_month,
                            (SELECT count(*) FROM scoped WHERE reg_day BETWEEN :from AND :to) AS new_period,
                            (SELECT count(*) FROM stamps s WHERE s.status = 'CONFIRMED'
                                AND s.local_date BETWEEN :from AND :to
                                AND (:all OR s.branch_id IN (:branches))) AS stamps,
                            (SELECT count(*) FROM rewards r WHERE (r.earned_at AT TIME ZONE :tz)::date BETWEEN :from AND :to
                                AND (:all OR EXISTS (SELECT 1 FROM stamps s WHERE s.reward_id = r.id
                                                     AND s.branch_id IN (:branches)))) AS earned,
                            (SELECT count(*) FROM rewards r WHERE r.status = 'REDEEMED'
                                AND (r.redeemed_at AT TIME ZONE :tz)::date BETWEEN :from AND :to
                                AND (:all OR r.redeemed_branch_id IN (:branches))) AS redeemed""")
                .params(params(q))
                .param("today", today)
                .param("weekStart", weekStart)
                .param("monthStart", monthStart)
                .query()
                .singleRow();
        long earned = number(row, "earned");
        long redeemed = number(row, "redeemed");
        return new Overview(number(row, "total"), number(row, "active7"), number(row, "active30"),
                number(row, "active90"), number(row, "new_week"), number(row, "new_month"), number(row, "stamps"),
                number(row, "new_period"), earned, redeemed, earned == 0 ? null : (double) redeemed / earned);
    }

    /** Möhürlər və təhvil verilmiş hədiyyələr — boş günlər də (0 ilə) qaytarılır. Həftə bazar ertəsindən başlayır. */
    public List<SeriesPoint> series(Query q, Granularity granularity) {
        String unit = granularity == Granularity.WEEK ? "week" : "day";
        return jdbc.sql("""
                        WITH buckets AS (
                            SELECT generate_series(date_trunc('%1$s', CAST(:from AS date)), CAST(:to AS date),
                                                   CAST('1 %1$s' AS interval))::date AS bucket
                        ),
                        st AS (
                            SELECT date_trunc('%1$s', s.local_date)::date AS bucket, count(*) AS n FROM stamps s
                            WHERE s.status = 'CONFIRMED' AND s.local_date BETWEEN :from AND :to
                              AND (:all OR s.branch_id IN (:branches))
                            GROUP BY 1
                        ),
                        rd AS (
                            SELECT date_trunc('%1$s', (r.redeemed_at AT TIME ZONE :tz)::date)::date AS bucket, count(*) AS n
                            FROM rewards r
                            WHERE r.status = 'REDEEMED' AND (r.redeemed_at AT TIME ZONE :tz)::date BETWEEN :from AND :to
                              AND (:all OR r.redeemed_branch_id IN (:branches))
                            GROUP BY 1
                        )
                        SELECT b.bucket, coalesce(st.n, 0) AS stamps, coalesce(rd.n, 0) AS redeemed
                        FROM buckets b LEFT JOIN st ON st.bucket = b.bucket LEFT JOIN rd ON rd.bucket = b.bucket
                        ORDER BY b.bucket""".formatted(unit))
                .params(params(q))
                .query((rs, n) -> new SeriesPoint(rs.getObject("bucket", LocalDate.class), rs.getLong("stamps"),
                        rs.getLong("redeemed")))
                .list();
    }

    /** Həftənin günü × saat üzrə möhürlər (şirkətin yerli vaxtı ilə); yalnız sıfırdan böyük xanalar. */
    public List<HeatCell> heatmap(Query q) {
        return jdbc.sql("""
                        SELECT extract(isodow FROM s.created_at AT TIME ZONE :tz)::int AS dow,
                               extract(hour FROM s.created_at AT TIME ZONE :tz)::int AS hour, count(*) AS n
                        FROM stamps s
                        WHERE s.status = 'CONFIRMED' AND s.local_date BETWEEN :from AND :to
                          AND (:all OR s.branch_id IN (:branches))
                        GROUP BY 1, 2 ORDER BY 1, 2""")
                .params(params(q))
                .query((rs, n) -> new HeatCell(rs.getInt("dow"), rs.getInt("hour"), rs.getLong("n")))
                .list();
    }

    /**
     * Geri qayıtma: dövrdə qeydiyyatdan keçən (və ilk möhürünü almış) müştərilərdən ilk möhür günündən sonrakı 30 gün
     * ərzində başqa bir gündə yenidən möhür alanların payı.
     */
    public Retention retention(Query q) {
        Map<String, Object> row = jdbc.sql("""
                        WITH first_visit AS (
                            SELECT s.customer_id, min(s.local_date) AS first_day FROM stamps s
                            JOIN customers c ON c.id = s.customer_id
                            WHERE s.status = 'CONFIRMED' AND (:all OR s.branch_id IN (:branches))
                              AND (c.registered_at AT TIME ZONE :tz)::date BETWEEN :from AND :to
                            GROUP BY s.customer_id
                        )
                        SELECT count(*) AS cohort,
                               count(*) FILTER (WHERE EXISTS (
                                   SELECT 1 FROM stamps s WHERE s.customer_id = f.customer_id AND s.status = 'CONFIRMED'
                                     AND (:all OR s.branch_id IN (:branches))
                                     AND s.local_date > f.first_day AND s.local_date <= f.first_day + 30)) AS returned
                        FROM first_visit f""")
                .params(params(q))
                .query()
                .singleRow();
        long cohort = number(row, "cohort");
        long returned = number(row, "returned");
        return new Retention(cohort, returned, cohort == 0 ? null : (double) returned / cohort);
    }

    /** Müştərilərin interfeys dili üzrə bölgüsü (az, en, ru — hamısı, sıfır olsa da). */
    public List<LanguageShare> languages(Query q) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("az", 0L);
        counts.put("en", 0L);
        counts.put("ru", 0L);
        jdbc.sql("""
                        SELECT c.preferred_language AS lang, count(*) AS n FROM customers c
                        WHERE c.status <> 'DELETED'
                          AND (:all OR EXISTS (SELECT 1 FROM stamps s WHERE s.customer_id = c.id
                                               AND s.branch_id IN (:branches)))
                        GROUP BY 1""")
                .params(params(q))
                .query((rs, n) -> counts.put(rs.getString("lang"), rs.getLong("n")))
                .list();
        List<LanguageShare> result = new ArrayList<>();
        counts.forEach((lang, n) -> result.add(new LanguageShare(lang, n)));
        return result;
    }

    private static Map<String, Object> params(Query q) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tz", q.zone().getId());
        params.put("from", q.from());
        params.put("to", q.to());
        params.put("all", q.scope().all());
        params.put("branches", q.scope().branchIds());
        return params;
    }

    private static long number(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).longValue();
    }
}
