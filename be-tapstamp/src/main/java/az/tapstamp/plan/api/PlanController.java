package az.tapstamp.plan.api;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import az.tapstamp.plan.Addon;
import az.tapstamp.plan.Feature;
import az.tapstamp.plan.PlanService;
import az.tapstamp.plan.Subscription;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Şirkətin paketi: frontend bölmələri və "xidmət dayandırılıb" ekranını buna görə göstərir. */
@RestController
@Tag(name = "Paket")
public class PlanController {

    private final PlanService plans;
    private final Clock clock;

    public PlanController(PlanService plans, Clock clock) {
        this.plans = plans;
        this.clock = clock;
    }

    public record PlanResponse(
            @Schema(description = "BASIC, STANDARD və ya PREMIUM") String plan,
            @Schema(description = "TRIAL (sınaq) və ya ACTIVE (ödənişli)") String status,
            Instant trialEndsAt,
            @Schema(description = "Sınaq bitib — şirkətin xidməti dayandırılıb") boolean expired,
            @Schema(description = "Açıq imkanlar (sınaq bitibsə boşdur)") List<Feature> features,
            int branchLimit,
            @Schema(description = "Sahiblərdən başqa işçilərin limiti; null — limitsiz") Integer staffLimit,
            @Schema(description = "Açıq əlavələr (ayrıca satılır; sınaq bitibsə boşdur)") List<Addon> addons) {
    }

    @GetMapping("/api/v1/public/plan")
    @Operation(summary = "Şirkətin paketi (giriş tələb etmir)", description = """
            Paket, sınaq müddəti və açıq imkanlar. `expired = true` olanda şirkətin qalan API-si `PLAN_TRIAL_EXPIRED` \
            qaytarır.""")
    PlanResponse plan() {
        Subscription subscription = plans.current();
        boolean expired = subscription.expired(clock.instant());
        List<Feature> features = expired ? List.of()
                : subscription.plan().features().stream().sorted().toList();
        return new PlanResponse(subscription.plan().name(), subscription.status().name(), subscription.trialEndsAt(),
                expired, features, subscription.branchLimit(), subscription.plan().staffLimit(),
                plans.activeAddons().stream().sorted().toList());
    }
}
