package az.tapstamp.feedback.api;

import java.util.List;
import java.util.UUID;

import az.tapstamp.customer.auth.CustomerPrincipal;
import az.tapstamp.feedback.FeedbackService;
import az.tapstamp.feedback.FeedbackService.FeedbackPrompt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Ziyarətdən sonra rəy — müştəri tətbiqi üçün. */
@RestController
@Tag(name = "Müştəri: rəy")
public class CustomerFeedbackController {

    private final FeedbackService feedback;

    public CustomerFeedbackController(FeedbackService feedback) {
        this.feedback = feedback;
    }

    public record FeedbackAnswerRequest(@NotNull String rating, @Nullable @Size(max = 6) List<String> reasons,
                                @Nullable @Size(max = 500) String comment) {
    }

    @GetMapping("/api/v1/me/feedback-request")
    @Operation(summary = "Cavab gözləyən rəy sorğusu", description = """
            Ən son ziyarət üçün göndərilmiş və hələ cavablanmamış sorğu (3 gün etibarlıdır); yoxdursa 204.""")
    ResponseEntity<FeedbackPrompt> open(@AuthenticationPrincipal CustomerPrincipal principal) {
        FeedbackPrompt request = feedback.openRequest(principal.customerId());
        return request == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(request);
    }

    @PostMapping("/api/v1/me/feedback-request/{requestId}")
    @Operation(summary = "Rəy vermək", description = """
            `rating`: GOOD, OK, BAD; `reasons` (OK/BAD üçün): SERVICE, WAIT, QUALITY, CLEANLINESS, PRICE, OTHER; \
            `comment` istəyə bağlıdır. Hər sorğuya bir cavab; sorğu tapılmırsa və ya vaxtı keçibsə 404.""")
    ResponseEntity<Void> answer(@AuthenticationPrincipal CustomerPrincipal principal, @PathVariable UUID requestId,
                                @Valid @RequestBody FeedbackAnswerRequest request) {
        feedback.answer(principal.customerId(), requestId, request.rating(),
                request.reasons() == null ? List.of() : request.reasons(), request.comment());
        return ResponseEntity.noContent().build();
    }
}
