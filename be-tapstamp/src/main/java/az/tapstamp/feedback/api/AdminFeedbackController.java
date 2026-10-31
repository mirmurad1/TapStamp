package az.tapstamp.feedback.api;

import java.time.LocalDate;
import java.util.UUID;

import az.tapstamp.feedback.FeedbackService;
import az.tapstamp.feedback.FeedbackService.FeedbackView;
import az.tapstamp.feedback.FeedbackService.FeedbackSummary;
import az.tapstamp.feedback.repository.FeedbackMapper.FeedbackSettings;
import az.tapstamp.plan.Feature;
import az.tapstamp.plan.RequiresFeature;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ziyarətdən sonra rəy — şirkət sahibi üçün parametrlər, hesabat və rəylər. */
@RestController
@RequiresFeature(Feature.FEEDBACK)
@RequestMapping("/api/v1/admin/feedback")
@PreAuthorize("hasRole('COMPANY_OWNER')")
@Tag(name = "Admin: rəylər")
public class AdminFeedbackController {

    private final FeedbackService feedback;

    public AdminFeedbackController(FeedbackService feedback) {
        this.feedback = feedback;
    }

    public record FeedbackSettingsRequest(@NotNull Boolean enabled, @NotNull Integer frequencyDays) {
    }

    public record FeedbackResolveRequest(@Nullable @Size(max = 500) String note) {
    }

    @GetMapping("/settings")
    @Operation(summary = "Parametrlər")
    FeedbackSettings settings(@AuthenticationPrincipal AdminPrincipal principal) {
        return feedback.settings(principal);
    }

    @PutMapping("/settings")
    @Operation(summary = "Parametrləri dəyişmək", description = """
            `frequencyDays` — müştəridən ən çox neçə gündə bir soruşulsun: 1, 7, 14, 30. Sorğu NFC möhüründən 15 \
            dəqiqə sonra push ilə gedir (saat məhdudiyyəti yoxdur). Auditə yazılır.""")
    FeedbackSettings updateSettings(@AuthenticationPrincipal AdminPrincipal principal,
                            @Valid @RequestBody FeedbackSettingsRequest request) {
        return feedback.updateSettings(principal, new FeedbackSettings(request.enabled(), request.frequencyDays()));
    }

    @GetMapping("/summary")
    @Operation(summary = "Xülasə", description = """
            Dövr üzrə (`from`/`to` verilməyibsə — son 30 gün): qiymətlər, məmnuniyyət (əla qiymətlərin payı), cavab \
            payı (cavablar / göndərilən sorğular); həll olunmamış pis rəylər — dövrdən asılı olmayaraq hamısı (admin \
            panelindəki bildiriş sayğacı). Dövr ən çox 366 gün.""")
    FeedbackSummary summary(@AuthenticationPrincipal AdminPrincipal principal,
                        @RequestParam(required = false) @Nullable UUID branchId,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to) {
        return feedback.summary(principal, branchId, from, to);
    }

    @GetMapping
    @Operation(summary = "Rəylər", description = """
            Ən yenisi birinci, səhifə-səhifə; `rating` və `unresolved` (yalnız həll olunmamış) süzgəcləri. Müştərinin \
            adı və tam telefon nömrəsi ilə.""")
    PageResult<FeedbackView> list(@AuthenticationPrincipal AdminPrincipal principal,
                                  @RequestParam(required = false) @Nullable String rating,
                                  @RequestParam(defaultValue = "false") boolean unresolved,
                                  @RequestParam(required = false) @Nullable UUID branchId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        return feedback.list(principal, rating, unresolved, branchId, PageQuery.of(page, size));
    }

    @PostMapping("/{feedbackId}/resolve")
    @Operation(summary = "Həll olundu", description = "İstəyə bağlı qeydlə. Auditə yazılır.")
    ResponseEntity<Void> resolve(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID feedbackId,
                                 @Valid @RequestBody FeedbackResolveRequest request) {
        feedback.resolve(principal, feedbackId, request.note());
        return ResponseEntity.noContent().build();
    }
}
