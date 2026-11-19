package az.tapstamp.campaign.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.tapstamp.campaign.CampaignService;
import az.tapstamp.campaign.CampaignService.Audience;
import az.tapstamp.campaign.CampaignService.Draft;
import az.tapstamp.campaign.CampaignService.Preview;
import az.tapstamp.campaign.CampaignService.Type;
import az.tapstamp.campaign.repository.CampaignMapper.Campaign;
import az.tapstamp.security.admin.AdminAuthService;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Kampaniya SMS-ləri — şirkət sahibi yaradır, platforma təsdiqindən sonra göndərilir. */
@RestController
@RequestMapping("/api/v1/admin/campaigns")
@PreAuthorize("hasRole('COMPANY_OWNER')")
@Tag(name = "Admin: kampaniyalar")
public class AdminCampaignController {

    private final CampaignService campaigns;
    private final AdminAuthService adminAuth;

    public AdminCampaignController(CampaignService campaigns, AdminAuthService adminAuth) {
        this.campaigns = campaigns;
        this.adminAuth = adminAuth;
    }

    @Schema(description = "Kampaniya: mətn (əvvəlinə şirkətin SMS adı əlavə olunur), auditoriya, istəyə bağlı filial")
    public record CampaignRequest(
            @Schema(description = "SMS (standart) və ya STAMPS — kartı boş olan müştərilərə SMS + möhür") Type type,
            @Schema(description = "Yalnız STAMPS: hər müştəriyə neçə möhür (1 … hədd − 1)") @Min(1) @Max(49)
            Integer stampCount,
            @NotBlank @Size(max = 1000) String text,
            @NotNull Audience audience,
            @Schema(description = "Yalnız INACTIVE: neçə gündür gəlməyənlər (7–365)") @Min(7) @Max(365) Integer inactiveDays,
            @Schema(description = "Yalnız bu filialda möhürü olan müştərilər; boşdursa hamısı") UUID branchId,
            @Schema(description = "Mətn \"Dəyərli <ad>,\" ilə başlasın (adı yoxdursa \"Dəyərli istifadəçi,\")")
            Boolean personalized,
            @Schema(description = "Bütün mesaj latın hərfləri ilə göndərilsin (ə→e, ş→s, ...): 160 simvol/SMS")
            Boolean latin,
            @Schema(description = "Cari şifrə — təsdiqə göndərəndə (yaratma və dəyişmə) məcburidir; önizləmədə lazım deyil")
            @Size(max = 256) String currentPassword) {

        @Override
        public @NonNull String toString() {
            return "CampaignRequest[type=" + type + ", audience=" + audience + ", currentPassword=***]";
        }

        Draft draft() {
            return new Draft(type == null ? Type.SMS : type, stampCount, text, audience, inactiveDays, branchId,
                    Boolean.TRUE.equals(personalized), Boolean.TRUE.equals(latin));
        }
    }

    @Schema(description = """
            Kampaniya və vəziyyəti. Sayğaclar canlıdır: sentCount — SMS göndərilib, failedCount — göndərilmədi, \
            pendingCount — gözləyir (təkrar cəhd daxil), skippedCount — ötürülüb (bloklanıb, silinib), stampsGranted — \
            möhür verilmiş müştərilər (yalnız STAMPS). pauseReason — PAUSED olanda provayderin xətası.""")
    public record CampaignView(UUID id, Type type, Integer stampCount, String text, boolean personalized,
                               boolean latin, Audience audience, Integer inactiveDays, UUID branchId,
                               int recipients, int segmentsPerMessage, int totalSms,
                               @Schema(allowableValues = {"PENDING_APPROVAL", "APPROVED", "SENDING", "PAUSED", "SENT",
                                       "REJECTED", "CANCELLED"}) String status,
                               String rejectReason, String pauseReason, Instant createdAt, Instant updatedAt,
                               Instant decidedAt, Instant deadlineAt, Instant finishedAt, int sentCount,
                               int failedCount, int pendingCount, int skippedCount, int stampsGranted) {

        public static CampaignView from(Campaign c) {
            return new CampaignView(c.id(), Type.valueOf(c.type()), c.stampCount(), c.text(), c.personalized(),
                    c.latin(), Audience.valueOf(c.audience()), c.inactiveDays(), c.branchId(), c.recipients(),
                    c.segmentsPerMessage(), c.totalSms(), c.status(), c.rejectReason(), c.pauseReason(), c.createdAt(),
                    c.updatedAt(), c.decidedAt(), c.deadlineAt(), c.finishedAt(), c.sentCount(), c.failedCount(),
                    c.pendingCount(), c.skippedCount(), c.stampsGranted());
        }
    }

    @Schema(description = "SMS-i göndərilməyən müştəri: ad, nömrə, səbəb")
    public record CampaignFailureView(UUID customerId, String name, String phone, String error, Instant at) {
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
        adminAuth.confirmCurrentPassword(principal, request.currentPassword());
        return CampaignView.from(campaigns.create(principal, request.draft()));
    }

    @GetMapping
    @Operation(summary = "Kampaniyalar", description = "Növə görə (SMS və ya STAMPS), yenidən köhnəyə; səhifə `page` (0-dan), `size` (5, 10, 20, 50).")
    PageResult<CampaignView> list(@AuthenticationPrincipal AdminPrincipal principal,
                                  @RequestParam(defaultValue = "SMS") Type type,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        return campaigns.list(principal, type, PageQuery.of(page, size)).map(CampaignView::from);
    }

    @GetMapping("/{campaignId}/failures")
    @Operation(summary = "SMS-i göndərilməyən müştərilər", description = "Ad, maskalanmış nömrə və səbəb; möhür də verilməyib.")
    PageResult<CampaignFailureView> failures(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID campaignId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        return campaigns.failures(principal, campaignId, PageQuery.of(page, size))
                .map(f -> new CampaignFailureView(f.customerId(), f.name(), f.phone(), f.error(), f.at()));
    }

    @GetMapping("/{campaignId}")
    @Operation(summary = "Kampaniya")
    CampaignView get(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID campaignId) {
        return CampaignView.from(campaigns.get(principal, campaignId));
    }

    @PutMapping("/{campaignId}")
    @Operation(summary = "Kampaniyanı dəyişmək", description = """
            Yalnız təsdiq gözləyərkən; alıcılar və SMS sayı yenidən hesablanır. Sonra `CAMPAIGN_STATE_CHANGED`.""")
    CampaignView update(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID campaignId,
                        @Valid @RequestBody CampaignRequest request) {
        adminAuth.confirmCurrentPassword(principal, request.currentPassword());
        return CampaignView.from(campaigns.update(principal, campaignId, request.draft()));
    }

    @PostMapping("/{campaignId}/cancel")
    @Operation(summary = "Kampaniyanı ləğv etmək", description = "Yalnız təsdiqə qədər; sonra `CAMPAIGN_STATE_CHANGED`.")
    CampaignView cancel(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID campaignId) {
        return CampaignView.from(campaigns.cancel(principal, campaignId));
    }
}
