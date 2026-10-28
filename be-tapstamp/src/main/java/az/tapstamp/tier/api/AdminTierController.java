package az.tapstamp.tier.api;

import java.util.UUID;

import az.tapstamp.plan.Feature;
import az.tapstamp.plan.RequiresFeature;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.tier.TierService;
import az.tapstamp.tier.TierService.TierInput;
import az.tapstamp.tier.TierService.TierOverview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Müştəri səviyyələri — şirkət sahibi üçün. */
@RestController
@RequiresFeature(Feature.TIERS)
@RequestMapping("/api/v1/admin/tiers")
@PreAuthorize("hasRole('COMPANY_OWNER')")
@Tag(name = "Admin: səviyyələr")
public class AdminTierController {

    private final TierService tiers;

    public AdminTierController(TierService tiers) {
        this.tiers = tiers;
    }

    public record TierRequest(@NotNull @Valid LocalizedText name,
                              @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
                              @NotNull @Min(0) @Max(10000) Integer minVisits,
                              @NotNull @Min(0) @Max(3) Integer bonusStamps) {

        TierInput toInput() {
            return new TierInput(name, color, minVisits, bonusStamps);
        }
    }

    public record TierSettingsRequest(@NotNull Boolean enabled) {
    }

    @PutMapping("/settings")
    @Operation(summary = "Proqramı açmaq və ya söndürmək", description = """
            Söndürüləndə müştəri səviyyəni görmür və səviyyə bonusu verilmir; səviyyələr və tarixçə silinmir, \n            yenidən açanda hər şey əvvəlki kimi işləyir (səviyyə ziyarət sayından hesablanır). Auditə yazılır.""")
    TierOverview settings(@AuthenticationPrincipal AdminPrincipal principal,
                          @Valid @RequestBody TierSettingsRequest request) {
        return tiers.setEnabled(principal, request.enabled());
    }

    @GetMapping
    @Operation(summary = "Səviyyələr", description = """
            Həddə görə artan sırada; hər səviyyədəki aktiv müştərilərin sayı və heç bir səviyyəyə çatmayanlar \
            (`belowAll`). Ziyarət — təsdiqlənmiş NFC möhürü.""")
    TierOverview list(@AuthenticationPrincipal AdminPrincipal principal) {
        return tiers.overview(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Səviyyə yaratmaq", description = """
            Ən çox 5 səviyyə. `minVisits` — keçid üçün ziyarət sayı (hər səviyyədə fərqli, yoxsa 409 \
            `TIER_THRESHOLD_TAKEN`); `bonusStamps` — hər NFC möhürünə əlavə möhür (0–3, bonus qaydaları ilə \
            toplanır). Auditə yazılır.""")
    TierOverview create(@AuthenticationPrincipal AdminPrincipal principal, @Valid @RequestBody TierRequest request) {
        return tiers.create(principal, request.toInput());
    }

    @PutMapping("/{tierId}")
    @Operation(summary = "Səviyyəni dəyişmək", description = "Müştərilərin səviyyəsi dərhal yeni həddlə hesablanır.")
    TierOverview update(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tierId,
                        @Valid @RequestBody TierRequest request) {
        return tiers.update(principal, tierId, request.toInput());
    }

    @DeleteMapping("/{tierId}")
    @Operation(summary = "Səviyyəni silmək", description = "Artıq bonus möhür vermiş səviyyə silinmir: 409 `TIER_IN_USE`.")
    TierOverview delete(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tierId) {
        return tiers.delete(principal, tierId);
    }
}
