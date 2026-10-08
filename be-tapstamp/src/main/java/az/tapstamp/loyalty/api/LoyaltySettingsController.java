package az.tapstamp.loyalty.api;

import az.tapstamp.loyalty.settings.LoyaltySettingsService;
import az.tapstamp.loyalty.settings.LoyaltySettingsService.RewardSettings;
import az.tapstamp.loyalty.settings.LoyaltySettingsService.StampRules;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.i18n.LocalizedText;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Loyallıq parametrləri — yalnız şirkətin sahibi (COMPANY_OWNER). */
@RestController
@RequestMapping("/api/v1/admin/settings")
@Tag(name = "Admin: parametrlər")
public class LoyaltySettingsController {

    private final LoyaltySettingsService settings;

    public LoyaltySettingsController(LoyaltySettingsService settings) {
        this.settings = settings;
    }

    @Schema(description = "Möhür qaydası (şirkət üzrə): cooldown bütün filiallar üzrə müştərinin son möhüründən hesablanır")
    public record StampPolicyRequest(
            @Schema(description = "İki möhür arasında ən az dəqiqə (0 — limitsiz)") @NotNull @Min(0) @Max(1440)
            Integer minIntervalMinutes,
            @Schema(description = "Bir gündə ən çox möhür (şirkətin saat qurşağı ilə)") @NotNull @Min(1) @Max(50)
            Integer maxStampsPerDay,
            @Schema(description = "Möhür işçi təsdiqlənənə qədər gözləyir") @NotNull Boolean requireStaffApproval,
            @Schema(description = "Təsdiqlənməyən möhür bu qədər dəqiqədən sonra rədd edilir") @NotNull @Min(1) @Max(1440)
            Integer pendingTimeoutMinutes) {
    }

    public record StampPolicyResponse(int minIntervalMinutes, int maxStampsPerDay, boolean requireStaffApproval,
                                      int pendingTimeoutMinutes) {
        static StampPolicyResponse from(StampRules rules) {
            return new StampPolicyResponse(rules.minIntervalMinutes(), rules.maxStampsPerDay(),
                    rules.requireStaffApproval(), rules.pendingTimeoutMinutes());
        }
    }

    @Schema(description = "Hədiyyə: neçənci möhürdə verilir, adı (az məcburi, en/ru istəyə bağlı), kartın müddəti")
    public record RewardSettingsRequest(
            @NotNull @Min(1) @Max(50) Integer stampsRequired,
            @NotNull @Valid LocalizedText rewardName,
            @Valid LocalizedText rewardDescription,
            @Schema(description = "Hədiyyə kartının müddəti (gün); null — müddətsiz") @Min(1) @Max(3650)
            Integer validityDays) {
    }

    public record RewardSettingsResponse(int version, int stampsRequired, LocalizedText rewardName,
                                     LocalizedText rewardDescription, Integer validityDays,
                                     @Schema(description = "Yeni hədd səbəbindən dərhal verilən hədiyyə kartları")
                                     Integer giftCardsIssued) {
        static RewardSettingsResponse from(RewardSettings rule, Integer issued) {
            return rule == null ? null : new RewardSettingsResponse(rule.version(), rule.stampsRequired(), rule.name(),
                    rule.description(), rule.validityDays(), issued);
        }
    }

    @GetMapping("/stamp-policy")
    @Operation(summary = "Möhür qaydası")
    StampPolicyResponse stampPolicy(@AuthenticationPrincipal AdminPrincipal principal) {
        return StampPolicyResponse.from(settings.stampRules(principal));
    }

    @PutMapping("/stamp-policy")
    @Operation(summary = "Möhür qaydasının dəyişdirilməsi", description = "Dərhal qüvvəyə minir və auditə yazılır.")
    StampPolicyResponse updateStampPolicy(@Valid @RequestBody StampPolicyRequest request,
                                          @AuthenticationPrincipal AdminPrincipal principal) {
        return StampPolicyResponse.from(settings.updateStampRules(principal, new StampRules(
                request.minIntervalMinutes(), request.maxStampsPerDay(), request.requireStaffApproval(),
                request.pendingTimeoutMinutes())));
    }

    @GetMapping("/reward-rule")
    @Operation(summary = "Hədiyyə qaydası")
    RewardSettingsResponse rewardRule(@AuthenticationPrincipal AdminPrincipal principal) {
        return RewardSettingsResponse.from(settings.reward(principal), null);
    }

    @PutMapping("/reward-rule")
    @Operation(summary = "Hədiyyə qaydasının dəyişdirilməsi", description = """
            Yeni versiya yaradılır; verilmiş hədiyyə kartları öz adını saxlayır. Kartında yeni həddən çox və ya bərabər \
            möhür olan müştərilərə hədiyyə kartı dərhal verilir.""")
    RewardSettingsResponse updateRewardRule(@Valid @RequestBody RewardSettingsRequest request,
                                        @AuthenticationPrincipal AdminPrincipal principal) {
        LoyaltySettingsService.RewardChange change = settings.updateReward(principal,
                new LoyaltySettingsService.RewardUpdate(request.stampsRequired(), request.rewardName(),
                        request.rewardDescription(), request.validityDays()));
        return RewardSettingsResponse.from(change.settings(), change.giftCardsIssued());
    }
}
