package az.tapstamp.bonus.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import az.tapstamp.bonus.BonusRuleService;
import az.tapstamp.bonus.BonusRuleService.RuleInput;
import az.tapstamp.bonus.BonusRuleService.RuleView;
import az.tapstamp.bonus.repository.BonusRuleMapper.DeliveryRow;
import az.tapstamp.plan.Feature;
import az.tapstamp.plan.RequiresFeature;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Bonus qaydaları (xoşbəxt saatlar, tarixli kampaniyalar) — şirkət sahibi üçün. */
@RestController
@RequiresFeature(Feature.BONUS_RULES)
@RequestMapping("/api/v1/admin/bonus-rules")
@PreAuthorize("hasRole('COMPANY_OWNER')")
@Tag(name = "Admin: bonus qaydaları")
public class AdminBonusRuleController {

    private final BonusRuleService bonusRules;

    public AdminBonusRuleController(BonusRuleService bonusRules) {
        this.bonusRules = bonusRules;
    }

    public record BonusRuleRequest(@NotNull @Valid LocalizedText name, @NotNull @Min(1) @Max(5) Integer bonusStamps,
                                   @NotEmpty List<@NotNull @Min(1) @Max(7) Integer> days,
                                   @Nullable LocalTime startTime, @Nullable LocalTime endTime,
                                   @Nullable LocalDate startsOn, @Nullable LocalDate endsOn, @Nullable UUID branchId,
                                   @NotNull Boolean active, @NotNull Boolean notifyOnStart) {

        RuleInput toInput() {
            return new RuleInput(name, bonusStamps, days, startTime, endTime, startsOn, endsOn, branchId, active,
                    notifyOnStart);
        }
    }

    public record DeleteResult(boolean deleted) {
    }

    @GetMapping
    @Operation(summary = "Qaydalar", description = """
            Bütün qaydalar: vəziyyət (`ACTIVE`, `SCHEDULED`, `ENDED`, `OFF`), bu an qüvvədədirmi (`runningNow`) və son \
            30 gündə verilən bonus möhürlər ilə onları alan müştərilərin sayı.""")
    List<RuleView> list(@AuthenticationPrincipal AdminPrincipal principal) {
        return bonusRules.list(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Qayda yaratmaq", description = """
            `bonusStamps` — hər NFC möhürünə əlavə bonus (1 = ikiqat möhür); `days` — ISO günləri (1 = bazar ertəsi); \
            saat aralığı (şirkətin saatı ilə) və tarix aralığı istəyə bağlıdır; `branchId` boşdursa — bütün \
            filiallar. `notifyOnStart` — başlayanda bildirişi açıq müştərilərə bir dəfə push (10:00–20:00): qayda \
            bu gün qüvvədədirsə — dərhal, gələcək tarixlidirsə — başladığı gün fon işi ilə. Bir neçə \
            qayda uyğun gəlirsə, ən böyük bonus tətbiq olunur. Auditə yazılır.""")
    RuleView create(@AuthenticationPrincipal AdminPrincipal principal, @Valid @RequestBody BonusRuleRequest request) {
        RuleView created = bonusRules.create(principal, request.toInput());
        // Tranzaksiya bitib — bu gün başlamış qaydanın bildirişi dərhal (fon işini gözləmədən)
        bonusRules.notifyStartedForCurrentTenant();
        return created;
    }

    @PutMapping("/{ruleId}")
    @Operation(summary = "Qaydanı dəyişmək")
    RuleView update(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID ruleId,
                    @Valid @RequestBody BonusRuleRequest request) {
        RuleView updated = bonusRules.update(principal, ruleId, request.toInput());
        bonusRules.notifyStartedForCurrentTenant();
        return updated;
    }

    @GetMapping("/{ruleId}/deliveries")
    @Operation(summary = "Başlanğıc bildirişini alanlar", description = """
            Bildiriş göndərilən müştərilər (ad, maskalı telefon) və nəticə: `PENDING` — növbədədir, `DELIVERED` — push \n            xidməti (Google, Apple) qəbul edib, `FAILED` — cihaz yoxdur və ya alınmadı. `status` ilə süzgəc; \n            səhifə-səhifə. "Çatdırıldı" bildirişin oxunduğu demək deyil.""")
    PageResult<DeliveryRow> deliveries(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID ruleId,
                                       @RequestParam(required = false) @Nullable String status,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        return bonusRules.deliveries(principal, ruleId, status, PageQuery.of(page, size));
    }

    @DeleteMapping("/{ruleId}")
    @Operation(summary = "Qaydanı silmək", description = """
            Bonus möhür verməmiş qayda silinir (`deleted = true`); artıq veribsə, tarixçə üçün saxlanılır və \
            söndürülür (`deleted = false`).""")
    DeleteResult delete(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID ruleId) {
        return new DeleteResult(bonusRules.delete(principal, ruleId));
    }
}
