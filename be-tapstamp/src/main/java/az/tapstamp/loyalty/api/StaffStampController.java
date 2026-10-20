package az.tapstamp.loyalty.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.tapstamp.loyalty.stamp.StampApprovalService;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** İşçi konsolu: təsdiq gözləyən möhürlər (filialda "işçi təsdiqi" rejimi aktivdirsə). */
@RestController
@RequestMapping("/api/v1/admin/staff-console/pending-stamps")
@Tag(name = "Admin: işçi konsolu")
public class StaffStampController {

    private final StampApprovalService approvals;

    public StaffStampController(StampApprovalService approvals) {
        this.approvals = approvals;
    }

    @Schema(description = "Təsdiq gözləyən möhür. Telefon yalnız maskalı göstərilir.")
    public record PendingStampResponse(UUID id, UUID branchId, String customerName, String phoneMasked,
                                       Instant createdAt, Instant expiresAt) {
    }

    public record RejectStampRequest(@NotBlank @Size(max = 200) String reason) {
    }

    public record StampDecisionResponse(UUID stampId, String status) {
    }

    @GetMapping
    @Operation(summary = "Gözləyən möhürlər", description = "Yalnız işçinin öz filialı; başqa filial üçün 404. Ən köhnəsi birinci; səhifə `page` (0-dan), `size` (5, 10, 20, 50).")
    PageResult<PendingStampResponse> pending(@RequestParam UUID branchId, @AuthenticationPrincipal AdminPrincipal principal,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        return approvals.pending(principal, branchId, PageQuery.of(page, size))
                .map(stamp -> new PendingStampResponse(stamp.id(), stamp.branchId(), stamp.customerName(),
                        stamp.phoneMasked(), stamp.createdAt(), stamp.expiresAt()));
    }

    @PostMapping("/{stampId}/confirm")
    @Operation(summary = "Möhürü təsdiqlə", description = "Möhür karta yazılır; kart dolubsa hədiyyə kartı yaranır.")
    StampDecisionResponse confirm(@PathVariable UUID stampId, @AuthenticationPrincipal AdminPrincipal principal) {
        StampApprovalService.Decision decision = approvals.confirm(principal, stampId);
        return new StampDecisionResponse(decision.stampId(), decision.status().name());
    }

    @PostMapping("/{stampId}/reject")
    @Operation(summary = "Möhürü rədd et", description = "Səbəb məcburidir və auditə yazılır.")
    StampDecisionResponse reject(@PathVariable UUID stampId, @Valid @RequestBody RejectStampRequest request,
                                 @AuthenticationPrincipal AdminPrincipal principal) {
        StampApprovalService.Decision decision = approvals.reject(principal, stampId, request.reason().strip());
        return new StampDecisionResponse(decision.stampId(), decision.status().name());
    }
}
