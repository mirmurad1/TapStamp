package az.tapstamp.onboarding.api;

import az.tapstamp.onboarding.SetupService;
import az.tapstamp.onboarding.SetupService.SetupView;
import az.tapstamp.security.admin.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Başlanğıc siyahısı — şirkət sahibi üçün. */
@RestController
@RequestMapping("/api/v1/admin/setup")
@PreAuthorize("hasRole('COMPANY_OWNER')")
@Tag(name = "Admin: başlanğıc siyahısı")
public class AdminSetupController {

    private final SetupService setup;

    public AdminSetupController(SetupService setup) {
        this.setup = setup;
    }

    public record RecommendationsRequest(@NotNull Boolean hidden) {
    }

    @GetMapping
    @Operation(summary = "Başlanğıc siyahısı", description = """
            Məcburi addımlar (BRANDING, REWARD_RULE, BRANCH, TEAM, NFC_TAG) və paketə görə müştəri cəlbi tövsiyələri (FEEDBACK, \
            GOOGLE_REVIEW, REFERRALS, TIERS, REMINDERS, BONUS_RULES, CATALOG). Vəziyyət mövcud məlumatdan hesablanır.""")
    SetupView get(@AuthenticationPrincipal AdminPrincipal principal) {
        return setup.view(principal);
    }

    @PutMapping("/recommendations")
    @Operation(summary = "Tövsiyələri gizlətmək və ya göstərmək", description = "Məcburi addımlar gizlədilmir.")
    SetupView recommendations(@AuthenticationPrincipal AdminPrincipal principal,
                              @Valid @RequestBody RecommendationsRequest request) {
        return setup.hideRecommendations(principal, request.hidden());
    }
}
