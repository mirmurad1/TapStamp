package az.tapstamp.campaign.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.tapstamp.campaign.CampaignService;
import az.tapstamp.campaign.CampaignService.Audience;
import az.tapstamp.campaign.CampaignService.Draft;
import az.tapstamp.campaign.CampaignService.Preview;
import az.tapstamp.campaign.repository.CampaignMapper.Campaign;
import az.tapstamp.security.admin.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Kampaniya SMS-ləri — şirkət sahibi yaradır, platforma təsdiqindən sonra göndərilir. */
@RestController
@RequestMapping("/api/v1/admin/campaigns")
@PreAuthorize("hasRole('COMPANY_OWNER')")
@Tag(name = "Admin: kampaniyalar")
public class AdminCampaignController {

    private final CampaignService campaigns;

    public AdminCampaignController(CampaignService campaigns) {
        this.campaigns = campaigns;
    }

    @Schema(description = "Kampaniya: mətn (əvvəlinə şirkətin SMS adı əlavə olunur), auditoriya, istəyə bağlı filial")
    public record CampaignRequest(
            @NotBlank @Size(max = 1000) String text,
            @NotNull Audience audience,
            @Schema(description = "Yalnız INACTIVE: neçə gündür gəlməyənlər (7–365)") @Min(7) @Max(365) Integer inactiveDays,
            @Schema(description = "Yalnız bu filialda möhürü olan müştərilər; boşdursa hamısı") UUID branchId,
            @Schema(description = "Mətn \"Hörmətli <ad>,\" ilə başlasın (adı yoxdursa \"Hörmətli istifadəçi,\")")
            Boolean personalized,
            @Schema(description = "Bütün mesaj latın hərfləri ilə göndərilsin (ə→e, ş→s, ...): 160 simvol/SMS")
            Boolean latin) {

        Draft draft() {
            return new Draft(text, audience, inactiveDays, branchId, Boolean.TRUE.equals(personalized),
                    Boolean.TRUE.equals(latin));
        }
    }

    @Schema(description = "Kampaniya və vəziyyəti")
    public record CampaignView(UUID id, String text, boolean personalized, boolean latin, Audience audience, Integer inactiveDays, UUID branchId,
                               int recipients, int segmentsPerMessage, int totalSms,
                               @Schema(allowableValues = {"PENDING_APPROVAL", "APPROVED", "SENDING", "SENT", "REJECTED",
                                       "CANCELLED"}) String status,
                               String rejectReason, Instant createdAt, Instant decidedAt, int sentCount, int failedCount,
                               Instant finishedAt) {

        public static CampaignView from(Campaign c) {
            return new CampaignView(c.id(), c.text(), c.personalized(), c.latin(), Audience.valueOf(c.audience()), c.inactiveDays(), c.branchId(),
                    c.recipients(), c.segmentsPerMessage(), c.totalSms(), c.status(), c.rejectReason(), c.createdAt(),
                    c.decidedAt(), c.sentCount(), c.failedCount(), c.finishedAt());
        }
    }

    @PostMapping("/preview")
    @Operation(summary = "Kampaniyanın önizləməsi", description = """
            Alıcı sayı, göndəriləcək tam mətn, simvol və SMS sayı; həmçinin latın hərfləri ilə variant və onun SMS sayı \
            (Azərbaycan hərfləri bir SMS-i 160 yox, 70 simvol edir).""")
    Preview preview(@AuthenticationPrincipal AdminPrincipal principal, @Valid @RequestBody CampaignRequest request) {
        return campaigns.preview(principal, request.draft());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Kampaniya yaratmaq", description = """
            Alıcılar və SMS sayı bu an müəyyənləşir; kampaniya platformanın təsdiqini gözləyir (PENDING_APPROVAL). \
            Alıcı yoxdursa `CAMPAIGN_NO_RECIPIENTS`.""")
    CampaignView create(@AuthenticationPrincipal AdminPrincipal principal, @Valid @RequestBody CampaignRequest request) {
        return CampaignView.from(campaigns.create(principal, request.draft()));
    }

    @GetMapping
    @Operation(summary = "Kampaniyalar", description = "Yenidən köhnəyə, son 100.")
    List<CampaignView> list(@AuthenticationPrincipal AdminPrincipal principal) {
        return campaigns.list(principal).stream().map(CampaignView::from).toList();
    }

    @GetMapping("/{campaignId}")
    @Operation(summary = "Kampaniya")
    CampaignView get(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID campaignId) {
        return CampaignView.from(campaigns.get(principal, campaignId));
    }

    @PostMapping("/{campaignId}/cancel")
    @Operation(summary = "Kampaniyanı ləğv etmək", description = "Yalnız təsdiqə qədər; sonra `CAMPAIGN_STATE_CHANGED`.")
    CampaignView cancel(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID campaignId) {
        return CampaignView.from(campaigns.cancel(principal, campaignId));
    }
}
