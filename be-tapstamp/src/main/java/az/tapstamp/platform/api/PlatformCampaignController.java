package az.tapstamp.platform.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.tapstamp.campaign.CampaignService.Audience;
import az.tapstamp.campaign.CampaignService.Type;
import az.tapstamp.campaign.repository.CampaignMapper.Campaign;
import az.tapstamp.platform.service.PlatformCampaignService;
import az.tapstamp.platform.service.PlatformCampaignService.TenantCampaign;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Şirkətlərin kampaniya SMS-ləri: siyahı və qərar (ödənişdən sonra təsdiq və ya rədd). */
@RestController
@RequestMapping("/api/v1/platform/campaigns")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Platforma: kampaniyalar")
public class PlatformCampaignController {

    private final PlatformCampaignService campaigns;

    public PlatformCampaignController(PlatformCampaignService campaigns) {
        this.campaigns = campaigns;
    }

    @Schema(description = "Şirkətin kampaniyası: mətn, auditoriya, alıcı və SMS sayı, vəziyyət")
    public record PlatformCampaignView(UUID tenantId, String slug, LocalizedText displayName, UUID id, Type type,
                                       Integer stampCount, String text, boolean personalized, boolean latin,
                                       Audience audience, Integer inactiveDays, int recipients, int segmentsPerMessage,
                                       int totalSms, String status, String rejectReason, String pauseReason,
                                       Instant createdAt, Instant updatedAt, Instant decidedAt, Instant deadlineAt,
                                       Instant finishedAt, int sentCount, int failedCount, int pendingCount,
                                       int skippedCount, int stampsGranted) {

        static PlatformCampaignView from(TenantCampaign tc) {
            Campaign c = tc.campaign();
            return new PlatformCampaignView(tc.tenant().id(), tc.tenant().slug(), tc.tenant().displayName(), c.id(),
                    Type.valueOf(c.type()), c.stampCount(), c.text(), c.personalized(), c.latin(),
                    Audience.valueOf(c.audience()), c.inactiveDays(), c.recipients(), c.segmentsPerMessage(),
                    c.totalSms(), c.status(), c.rejectReason(), c.pauseReason(), c.createdAt(), c.updatedAt(),
                    c.decidedAt(), c.deadlineAt(), c.finishedAt(), c.sentCount(), c.failedCount(), c.pendingCount(),
                    c.skippedCount(), c.stampsGranted());
        }
    }

    @Schema(description = "Rədd səbəbi (şirkət sahibinə göstərilir)")
    public record RejectRequest(@NotBlank @Size(max = 300) String reason) {
    }

    @GetMapping
    @Operation(summary = "Kampaniyalar", description = """
            `status` — PENDING (diqqət tələb edənlər: təsdiq gözləyənlər və dayandırılmışlar) və ya ALL. Bütün \
            şirkətlər üzrə yenidən köhnəyə; səhifə `page` (0-dan), `size` (5, 10, 20, 50).""")
    PageResult<PlatformCampaignView> list(@RequestParam(defaultValue = "PENDING") @Pattern(regexp = "PENDING|ALL") String status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        List<String> statuses = "PENDING".equals(status) ? List.of("PENDING_APPROVAL", "PAUSED")
                : List.of("PENDING_APPROVAL", "APPROVED", "SENDING", "PAUSED", "SENT", "REJECTED", "CANCELLED");
        return campaigns.list(statuses, PageQuery.of(page, size)).map(PlatformCampaignView::from);
    }

    @PostMapping("/{tenantId}/{campaignId}/approve")
    @Operation(summary = "Kampaniyanı təsdiqləmək", description = """
            Ödəniş alındıqdan sonra. Göndəriş 09:00–21:00 arası (şirkətin saatı ilə) avtomatik başlayır.""")
    PlatformCampaignView approve(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId,
                                 @PathVariable UUID campaignId) {
        return PlatformCampaignView.from(campaigns.decide(principal, tenantId, campaignId, true, null));
    }

    @PostMapping("/{tenantId}/{campaignId}/resume")
    @Operation(summary = "Dayandırılmış kampaniyanı davam etdirmək", description = """
            Provayderin hesab xətası (icazə, balans) həll olunandan sonra; göndəriş qaldığı yerdən davam edir.""")
    PlatformCampaignView resume(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId,
                                @PathVariable UUID campaignId) {
        return PlatformCampaignView.from(campaigns.resume(principal, tenantId, campaignId));
    }

    @PostMapping("/{tenantId}/{campaignId}/reject")
    @Operation(summary = "Kampaniyanı ləğv etmək (rədd)", description = "Səbəb sahibə göstərilir. Platforma kampaniyanı dəyişə bilməz.")
    PlatformCampaignView reject(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId,
                                @PathVariable UUID campaignId, @Valid @RequestBody RejectRequest request) {
        return PlatformCampaignView.from(campaigns.decide(principal, tenantId, campaignId, false, request.reason()));
    }
}
