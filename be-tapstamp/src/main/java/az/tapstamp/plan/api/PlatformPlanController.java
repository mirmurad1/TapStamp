package az.tapstamp.plan.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.tapstamp.plan.Plan;
import az.tapstamp.plan.PlanService;
import az.tapstamp.plan.Subscription;
import az.tapstamp.security.admin.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platforma paneli: şirkətin paketi və sınaq müddəti. */
@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}/plan")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Platforma: paketlər")
public class PlatformPlanController {

    private final PlanService plans;

    public PlatformPlanController(PlanService plans) {
        this.plans = plans;
    }

    public record TenantPlanTrial(String plan, Instant startedAt, Instant endsAt) {
    }

    public record TenantPlanResponse(String plan, String status, Instant trialEndsAt, boolean expired,
                                     int extraBranches, int branchLimit, Integer staffLimit,
                                     @Schema(description = "İstifadə olunmuş sınaqlar (hər paket üçün bir dəfə)")
                                     List<TenantPlanTrial> trials) {

        static TenantPlanResponse from(PlanService.PlanView view) {
            Subscription s = view.subscription();
            return new TenantPlanResponse(s.plan().name(), s.status().name(), s.trialEndsAt(), view.expired(),
                    s.extraBranches(), s.branchLimit(), s.plan().staffLimit(),
                    view.trials().stream().map(t -> new TenantPlanTrial(t.plan(), t.startedAt(), t.endsAt())).toList());
        }
    }

    public record TenantPlanRequest(
            @NotNull Plan plan,
            @Schema(description = "TRIAL — 14 günlük sınaq (hər paket üçün bir dəfə), ACTIVE — ödəniş qeyd olunub")
            @NotNull Subscription.Status status,
            @Schema(description = "Yalnız Premium: paketə daxil olan 5 filialdan əlavə") @Min(0) @Max(100) int extraBranches) {
    }

    @GetMapping
    @Operation(summary = "Şirkətin paketi")
    TenantPlanResponse get(@PathVariable UUID tenantId) {
        return TenantPlanResponse.from(plans.view(tenantId));
    }

    @PutMapping
    @Operation(summary = "Paketi dəyişmək", description = """
            `TRIAL` — həmin paketin 14 günlük sınağı; bu paketin sınağı artıq istifadə olunubsa `PLAN_TRIAL_ALREADY_USED`. \
            `ACTIVE` — ödənişli (sınaq bitir). Əlavə filial yalnız Premium-da. Audit jurnalına yazılır.""")
    TenantPlanResponse update(@PathVariable UUID tenantId, @Valid @RequestBody TenantPlanRequest request,
                              @AuthenticationPrincipal AdminPrincipal principal) {
        return TenantPlanResponse.from(plans.change(principal, tenantId, request.plan(), request.status(),
                request.extraBranches()));
    }
}
