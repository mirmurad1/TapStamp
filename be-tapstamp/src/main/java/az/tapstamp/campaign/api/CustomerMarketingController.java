package az.tapstamp.campaign.api;

import az.tapstamp.campaign.CampaignService;
import az.tapstamp.customer.auth.CustomerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Müştərinin kampaniya SMS-lərinə razılığı (Parametrlər): standart olaraq açıqdır, istənilən an söndürülə bilər. */
@RestController
@RequestMapping("/api/v1/me/marketing")
@Tag(name = "Müştəri: profil")
public class CustomerMarketingController {

    private final CampaignService campaigns;

    public CustomerMarketingController(CampaignService campaigns) {
        this.campaigns = campaigns;
    }

    @Schema(description = "Kampaniya SMS-ləri: true — alır")
    public record MarketingSettings(@NotNull Boolean enabled) {
    }

    @GetMapping
    @Operation(summary = "Kampaniya SMS-ləri: cari seçim")
    MarketingSettings get(@AuthenticationPrincipal CustomerPrincipal principal) {
        return new MarketingSettings(campaigns.marketingEnabled(principal.customerId()));
    }

    @PatchMapping
    @Operation(summary = "Kampaniya SMS-lərini açmaq və ya söndürmək",
            description = "Söndürülsə, gələcək kampaniyalara düşmür; artıq təsdiqlənmiş kampaniyada da SMS getmir.")
    MarketingSettings set(@AuthenticationPrincipal CustomerPrincipal principal,
                          @Valid @RequestBody MarketingSettings request) {
        campaigns.setMarketingEnabled(principal.customerId(), request.enabled());
        return new MarketingSettings(request.enabled());
    }
}
