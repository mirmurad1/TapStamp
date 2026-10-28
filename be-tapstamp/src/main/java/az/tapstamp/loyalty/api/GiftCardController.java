package az.tapstamp.loyalty.api;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.tapstamp.customer.auth.CustomerPrincipal;
import az.tapstamp.loyalty.reward.GiftCardService;
import az.tapstamp.shared.i18n.LocalizedText;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Müştərinin "Hədiyyə kartları" bölməsi. */
@RestController
@Tag(name = "Müştəri: hədiyyə kartları")
public class GiftCardController {

    private final GiftCardService giftCards;
    private final Clock clock;

    public GiftCardController(GiftCardService giftCards, Clock clock) {
        this.giftCards = giftCards;
        this.clock = clock;
    }

    @Schema(description = "Hədiyyə kartı. code — daimi unikal kod: işçiyə göstərilir (QR-da da eyni kod var).")
    public record GiftCardResponse(
            UUID id,
            String code,
            LocalizedText name,
            @Schema(allowableValues = {"AVAILABLE", "REDEEMED", "REVOKED", "EXPIRED"}) String status,
            Instant earnedAt,
            @Schema(description = "Müddəti; null — müddətsiz") Instant expiresAt,
            Instant redeemedAt,
            @Schema(description = "Niyə verilib: CARD — möhür kartı doldu, TIER — səviyyəyə çatdı",
                    allowableValues = {"CARD", "TIER"}) String source,
            @Schema(description = "source = TIER olanda səviyyənin adı") LocalizedText tierName) {
    }

    @GetMapping("/api/v1/me/gift-cards")
    @Operation(summary = "Hədiyyə kartları", description = "Əvvəlcə istifadə oluna bilənlər, sonra tarixçə.")
    List<GiftCardResponse> list(@AuthenticationPrincipal CustomerPrincipal principal) {
        return giftCards.list(principal.customerId(), clock.instant()).stream()
                .map(card -> new GiftCardResponse(card.id(), card.code(), card.name(), card.status().name(),
                        card.earnedAt(), card.expiresAt(), card.redeemedAt(), card.source(), card.tierName()))
                .toList();
    }
}
