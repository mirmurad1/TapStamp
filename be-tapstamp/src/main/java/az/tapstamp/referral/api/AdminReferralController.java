package az.tapstamp.referral.api;

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
    @Operation(summary = "Dəvətlər", description = "Yenidən köhnəyə; səhifə `page` (0-dan), `size` (5, 10, 20, 50). Telefonlar maskalı.")
    PageResult<ReferralRow> list(@AuthenticationPrincipal AdminPrincipal principal,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        return referrals.list(principal, PageQuery.of(page, size));
    }
}
