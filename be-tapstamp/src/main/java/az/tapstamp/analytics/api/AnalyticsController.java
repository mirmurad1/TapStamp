package az.tapstamp.analytics.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import az.tapstamp.analytics.AnalyticsService;
import az.tapstamp.analytics.AnalyticsService.Granularity;
import az.tapstamp.analytics.AnalyticsService.HeatCell;
import az.tapstamp.analytics.AnalyticsService.LanguageShare;
import az.tapstamp.analytics.AnalyticsService.Overview;
import az.tapstamp.analytics.AnalyticsService.Retention;
import az.tapstamp.analytics.AnalyticsService.SeriesPoint;
import az.tapstamp.plan.Feature;
import az.tapstamp.plan.RequiresFeature;
import az.tapstamp.security.admin.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * İdarəetmə panelinin analitikası. Ümumi parametrlər: {@code from}, {@code to} (şirkətin yerli tarixləri, hər ikisi
 * daxil; standart — son 30 gün, ən çox 366 gün) və {@code branchId} (menecer yalnız öz filiallarını görür).
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasAnyRole('COMPANY_OWNER', 'BRANCH_MANAGER')")
@Tag(name = "Admin: analitika")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @RequiresFeature(Feature.BRANCH_COMPARISON)
    @GetMapping("/comparison")
    @Operation(summary = "Filialların və işçilərin müqayisəsi", description = """
            Filiallar: ziyarətlər (NFC möhürləri), müştərilər, yeni (ilk ziyarəti bu filialda) və geri qayıdan (2+ gün) \
            müştərilər, verilən və təhvil verilən hədiyyələr, bonus möhürlər (kampaniya və səviyyə), rəylər — cari və \
            əvvəlki eyni uzunluqlu dövr. İşçilər: təsdiqlədiyi və rədd etdiyi möhürlər, əl ilə verdiyi möhürlər, təhvil \
            verdiyi hədiyyələr. Menecer yalnız öz filiallarını görür.""")
    AnalyticsService.Comparison comparison(@AuthenticationPrincipal AdminPrincipal principal,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.comparison(analytics.query(principal, from, to, null));
    }

    @GetMapping("/overview")
    @Operation(summary = "Əsas göstəricilər", description = """
            Ümumi və aktiv (7/30/90 gün) müştərilər, bu həftə/ay yeni müştərilər, dövrdə möhürlər, verilən və təhvil \
            verilən hədiyyələr. Aktiv müştəri — son N gündə ən azı bir təsdiqlənmiş möhürü olan.""")
    Overview overview(@AuthenticationPrincipal AdminPrincipal principal,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      @RequestParam(required = false) UUID branchId) {
        return analytics.overview(analytics.query(principal, from, to, branchId));
    }

    @RequiresFeature(Feature.FULL_ANALYTICS)
    @GetMapping("/stamps-series")
    @Operation(summary = "Möhürlər və təhvil verilən hədiyyələr (zaman sırası)",
            description = "Günlük və ya həftəlik (bazar ertəsindən); boş dövrlər 0 ilə qaytarılır.")
    List<SeriesPoint> series(@AuthenticationPrincipal AdminPrincipal principal,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @RequestParam(required = false) UUID branchId,
                             @Parameter(description = "DAY və ya WEEK") @RequestParam(defaultValue = "DAY")
                             Granularity granularity) {
        return analytics.series(analytics.query(principal, from, to, branchId), granularity);
    }

    @RequiresFeature(Feature.FULL_ANALYTICS)
    @GetMapping("/heatmap")
    @Operation(summary = "Həftənin günü × saat üzrə möhürlər", description = "Gün ISO nömrəsi ilə (1 — bazar ertəsi).")
    List<HeatCell> heatmap(@AuthenticationPrincipal AdminPrincipal principal,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(required = false) UUID branchId) {
        return analytics.heatmap(analytics.query(principal, from, to, branchId));
    }

    @RequiresFeature(Feature.FULL_ANALYTICS)
    @GetMapping("/retention")
    @Operation(summary = "Geri qayıtma", description = """
            Dövrdə qeydiyyatdan keçənlərdən ilk möhürdən sonrakı 30 gün ərzində başqa bir gündə yenidən gələnlərin payı.""")
    Retention retention(@AuthenticationPrincipal AdminPrincipal principal,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @RequestParam(required = false) UUID branchId) {
        return analytics.retention(analytics.query(principal, from, to, branchId));
    }

    @RequiresFeature(Feature.FULL_ANALYTICS)
    @GetMapping("/languages")
    @Operation(summary = "Müştərilərin dil üzrə bölgüsü")
    List<LanguageShare> languages(@AuthenticationPrincipal AdminPrincipal principal,
                                  @RequestParam(required = false) UUID branchId) {
        return analytics.languages(analytics.query(principal, null, null, branchId));
    }
}
