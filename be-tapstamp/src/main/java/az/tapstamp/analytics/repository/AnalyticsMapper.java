package az.tapstamp.analytics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import az.tapstamp.analytics.AnalyticsService.HeatCell;
import az.tapstamp.analytics.AnalyticsService.SeriesPoint;
import az.tapstamp.shared.persistence.TenantMapper;
import org.apache.ibatis.annotations.Param;

/**
 * İdarəetmə panelinin göstəriciləri. "Gün" hesablamaları şirkətin saat qurşağındadır, yalnız CONFIRMED möhürlər
 * sayılır. SQL: {@code mapper/tenant/analytics/AnalyticsMapper.xml}.
 */
@TenantMapper
public interface AnalyticsMapper {

    /**
     * Dövr (hər iki tarix daxil), saat qurşağı və filial çərçivəsi: {@code all} — bütün filiallar, əks halda yalnız
     * {@code branchIds}.
     */
    record Scope(LocalDate from, LocalDate to, String timezone, boolean all, List<UUID> branchIds) {
    }

    record OverviewCounts(long total, long active7, long active30, long active90, long newWeek, long newMonth,
                          long newPeriod, long stamps, long earned, long redeemed) {
    }

    record RetentionCounts(long cohort, long returned) {
    }

    record LanguageCount(String language, long customers) {
    }

    OverviewCounts overview(@Param("q") Scope q, LocalDate today, LocalDate weekStart, LocalDate monthStart);

    /** {@code unit}: {@code day} və ya {@code week} (yalnız bu iki dəyər — {@code AnalyticsService.Granularity}). */
    List<SeriesPoint> series(@Param("q") Scope q, String unit);

    List<HeatCell> heatmap(@Param("q") Scope q);

    RetentionCounts retention(@Param("q") Scope q);

    List<LanguageCount> languages(@Param("q") Scope q);
}
