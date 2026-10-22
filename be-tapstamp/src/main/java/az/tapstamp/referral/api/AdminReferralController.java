package az.tapstamp.referral.api;

import java.time.LocalDate;
import java.util.UUID;

import az.tapstamp.plan.Feature;
import az.tapstamp.plan.RequiresFeature;
import az.tapstamp.referral.ReferralService;
import az.tapstamp.referral.repository.ReferralMapper.ReferralRow;
import az.tapstamp.referral.repository.ReferralMapper.ReferralSettings;
import az.tapstamp.referral.repository.ReferralMapper.ReferralSummary;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dost dəvəti — şirkət sahibi üçün parametrlər və hesabat. */
@RestController
@RequiresFeature(Feature.REFERRALS)
@RequestMapping("/api/v1/admin/referrals")
@PreAuthorize("hasRole('COMPANY_OWNER')")
@Tag(name = "Admin: dost dəvəti")
public class AdminReferralController {

    private final ReferralService referrals;

    public AdminReferralController(ReferralService referrals) {
        this.referrals = referrals;
    }

    public record ReferralSettingsRequest(@NotNull Boolean enabled,
                                          @NotNull @Pattern(regexp = "FIRST_VISIT|REGISTRATION") String rewardOn,
                                          @NotNull @Min(1) @Max(3) Integer referrerStamps,
                                          @NotNull @Min(1) @Max(3) Integer friendStamps,
                                          @NotNull @Min(1) @Max(50) Integer monthlyCap,
                                          @NotNull @Min(7) @Max(90) Integer validDays) {
    }

    @GetMapping("/settings")
    @Operation(summary = "Dəvət proqramının parametrləri")
    ReferralSettings settings(@AuthenticationPrincipal AdminPrincipal principal) {
        return referrals.settings(principal);
    }

    @PutMapping("/settings")
    @Operation(summary = "Parametrləri dəyişmək", description = """
            Aktiv/söndürülmüş, bonusun vaxtı (FIRST_VISIT — dostun ilk NFC möhüründə, REGISTRATION — qeydiyyat \
            anında), dəvət edənə və dosta bonus möhür (1–3), dəvət edənin aylıq limiti (1–50), dəvətin \
            etibarlılıq müddəti (7–90 gün). Dəyişiklik auditə yazılır.""")
    ReferralSettings updateSettings(@AuthenticationPrincipal AdminPrincipal principal,
                            @Valid @RequestBody ReferralSettingsRequest request) {
        return referrals.updateSettings(principal, new ReferralSettings(request.enabled(), request.rewardOn(), request.referrerStamps(),
                request.friendStamps(), request.monthlyCap(), request.validDays()));
    }

    @GetMapping("/summary")
    @Operation(summary = "Xülasə", description = "Bütün dəvətlər, bonus qazandıranlar, gözləyənlər, müddəti bitənlər, limitə düşənlər və verilən bonus möhürlər.")
    ReferralSummary summary(@AuthenticationPrincipal AdminPrincipal principal) {
        return referrals.summary(principal);
    }

    @GetMapping
    @Operation(summary = "Dəvətlər", description = """
            Yenidən köhnəyə; telefonlar maskalı; dostun gəlişləri (təsdiqlənmiş NFC möhürləri) və son gəlişi ilə. \
            `activity`: ALL, NONE (gəlməyib), ONCE (bir gün gəlib), RETURNED (2+ fərqli gündə). Səhifə `page` (0-dan), \
            `size` (5, 10, 20, 50).""")
    PageResult<ReferralRow> list(@AuthenticationPrincipal AdminPrincipal principal,
                                 @RequestParam(defaultValue = "ALL") String activity,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        return referrals.list(principal, activity, PageQuery.of(page, size));
    }

    @RequiresFeature(Feature.REFERRAL_STATS)
    @GetMapping("/dashboard")
    @Operation(summary = "İdarəetmə paneli üçün statistika", description = """
            Dövrdə yaranan dəvətlər: dəvət olunanlar, gələnlər (1+ NFC möhürü), qayıdanlar (2+ fərqli gün), gəlişlər, \
            hədiyyə kartı qazananlar, bonus möhürlər və ən çox dost gətirən 5 müştəri. `from`/`to` — şirkətin yerli \
            tarixləri (default son 30 gün); `branchId` verilibsə, gəlişlər yalnız o filialda sayılır.""")
    ReferralService.Dashboard dashboard(@AuthenticationPrincipal AdminPrincipal principal,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) UUID branchId) {
        return referrals.dashboard(principal, from, to, branchId);
    }
}
