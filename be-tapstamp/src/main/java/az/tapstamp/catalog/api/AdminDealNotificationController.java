package az.tapstamp.catalog.api;

import java.time.LocalTime;

import az.tapstamp.catalog.DealNotificationService;
import az.tapstamp.catalog.DealNotificationService.DealNotificationOverview;
import az.tapstamp.catalog.DealNotificationService.DealSendResult;
import az.tapstamp.plan.Addon;
import az.tapstamp.plan.RequiresAddon;
import az.tapstamp.security.admin.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endirim bildirişləri — şirkət sahibi üçün (əlavə {@code CATALOG}): parametrlər, "İndi göndər" və nəticələr. */
@RestController
@RequiresAddon(Addon.CATALOG)
@RequestMapping("/api/v1/admin/catalog/deal-notifications")
@PreAuthorize("hasRole('COMPANY_OWNER')")
@Tag(name = "Admin: menyu və xidmətlər")
public class AdminDealNotificationController {

    private final DealNotificationService deals;

    public AdminDealNotificationController(DealNotificationService deals) {
        this.deals = deals;
    }

    public record DealNotificationSettingsRequest(@NotNull Boolean autoEnabled,
                                  @Schema(description = "Avtomatik göndərişin saatı, HH:mm (30 dəqiqəlik addım)")
                                  @NotNull @Pattern(regexp = "^([01]\\d|2[0-3]):(00|30)$") String sendTime,
                                  @NotNull Boolean lastDayEnabled) {
    }

    @GetMapping
    @Operation(summary = "Endirim bildirişləri", description = """
            Parametrlər, elan gözləyən yeni endirimlər və bildirişin önizləməsi, bu gün göndərilibmi, son elanların \
            nəticəsi (göndərilən, çatdırılan, açan müştərilər).""")
    DealNotificationOverview overview(@AuthenticationPrincipal AdminPrincipal principal) {
        return deals.overview(principal);
    }

    @PutMapping("/settings")
    @Operation(summary = "Endirim bildirişlərinin parametrləri")
    DealNotificationOverview updateSettings(@AuthenticationPrincipal AdminPrincipal principal,
                            @Valid @RequestBody DealNotificationSettingsRequest request) {
        return deals.updateSettings(principal, request.autoEnabled(), LocalTime.parse(request.sendTime()),
                request.lastDayEnabled());
    }

    @PostMapping("/send")
    @Operation(summary = "İndi göndər", description = """
            Elan gözləyən yeni endirimlər bir bildirişdə, gündə bir elan. Müştəriyə filialının iş saatında çatır: \
            `recipients` — dərhal göndərildi, `waiting` — filial açılanda gedəcək. Nəticə `status`-dadır (SENT, \
            NOTHING_TO_SEND, ALREADY_SENT_TODAY, UNAVAILABLE).""")
    DealSendResult send(@AuthenticationPrincipal AdminPrincipal principal) {
        return deals.sendNow(principal);
    }
}
