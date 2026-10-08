package az.cafeloyalty.analytics.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import az.cafeloyalty.analytics.AnalyticsService;
import az.cafeloyalty.analytics.AnalyticsService.Granularity;
import az.cafeloyalty.analytics.AnalyticsService.HeatCell;
import az.cafeloyalty.analytics.AnalyticsService.LanguageShare;
import az.cafeloyalty.analytics.AnalyticsService.Overview;
import az.cafeloyalty.analytics.AnalyticsService.Retention;
import az.cafeloyalty.analytics.AnalyticsService.SeriesPoint;
import az.cafeloyalty.security.admin.AdminPrincipal;
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

    @GetMapping("/heatmap")
    @Operation(summary = "Həftənin günü × saat üzrə möhürlər", description = "Gün ISO nömrəsi ilə (1 — bazar ertəsi).")
    List<HeatCell> heatmap(@AuthenticationPrincipal AdminPrincipal principal,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(required = false) UUID branchId) {
        return analytics.heatmap(analytics.query(principal, from, to, branchId));
    }

    @GetMapping("/retention")
    @Operation(summary = "Geri qayıtma", description = """
            Dövrdə qeydiyyatdan keçənlərdən ilk möhürdən sonrakı 30 gün ərzində başqa bir gündə yenidən gələnlərin payı.""")
    Retention retention(@AuthenticationPrincipal AdminPrincipal principal,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @RequestParam(required = false) UUID branchId) {
        return analytics.retention(analytics.query(principal, from, to, branchId));
    }

    @GetMapping("/languages")
    @Operation(summary = "Müştərilərin dil üzrə bölgüsü")
    List<LanguageShare> languages(@AuthenticationPrincipal AdminPrincipal principal,
                                  @RequestParam(required = false) UUID branchId) {
        return analytics.languages(analytics.query(principal, null, null, branchId));
    }
}
