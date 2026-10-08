package az.tapstamp.loyalty.api;

import java.time.Instant;
import java.util.UUID;

import az.tapstamp.customer.auth.CustomerPrincipal;
import az.tapstamp.loyalty.stamp.StampClaimService;
import az.tapstamp.nfc.BrowserIdCookie;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.web.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Toxunuşdan sonrakı səhifə: tokenin vəziyyəti və möhürün alınması. */
@RestController
@RequestMapping("/api/v1/taps/{token}")
@Tag(name = "Toxunuş (NFC)")
public class TapClaimController {

    private final StampClaimService claims;
    private final BrowserIdCookie browserIds;

    public TapClaimController(StampClaimService claims, BrowserIdCookie browserIds) {
        this.claims = claims;
        this.browserIds = browserIds;
    }

    @Schema(description = "Toxunuşun vəziyyəti. sameBrowser=false — link başqa brauzerdə açılıb, möhür alınmayacaq.")
    public record TapStatusResponse(
            @Schema(allowableValues = {"ACTIVE", "RESERVED", "CONSUMED", "REJECTED", "EXPIRED"}) String status,
            UUID branchId, LocalizedText branchName, Instant expiresAt, boolean sameBrowser) {
    }

    @Schema(description = "Möhürün nəticəsi. PENDING — işçi təsdiqi gözlənilir; giftCardId — kart doldu, hədiyyə kartı yarandı.")
    public record ClaimResponse(
            UUID stampId,
            @Schema(allowableValues = {"PENDING", "CONFIRMED"}) String status,
            int currentStamps,
            int stampsRequired,
            UUID giftCardId,
            UUID branchId) {
    }

    @GetMapping
    @Operation(summary = "Toxunuşun vəziyyəti", description = "Yan təsiri yoxdur. Token tanınmırsa `TAP_TOKEN_EXPIRED`.")
    TapStatusResponse status(@PathVariable String token, HttpServletRequest http) {
        StampClaimService.TapStatus status = claims.status(token, browserIds.read(http).orElse(null))
                .orElseThrow(() -> new ApiException(ErrorCode.TAP_TOKEN_EXPIRED));
        return new TapStatusResponse(status.status().name(), status.branchId(), status.branchName(),
                status.expiresAt(), status.sameBrowser());
    }

    @PostMapping("/claim")
    @Operation(summary = "Toxunuşa görə möhür", description = """
            Token 60 saniyə etibarlıdır, birdəfəlikdir və toxunuşun edildiyi brauzerə bağlıdır \
            (`TAP_BINDING_MISMATCH`). Eyni müştəri təkrar göndərsə eyni nəticə qaytarılır (idempotent), başqa müştəri \
            `TAP_TOKEN_USED` alır. Qaydalar: `STAMP_COOLDOWN_ACTIVE`, `STAMP_DAILY_LIMIT_REACHED` (hər ikisində \
            `retryAfterSeconds`), `OUTSIDE_BUSINESS_HOURS`.""")
    ClaimResponse claim(@PathVariable String token, @AuthenticationPrincipal CustomerPrincipal principal,
                        HttpServletRequest http) {
        StampClaimService.ClaimResult result = claims.claim(token, principal.customerId(),
                browserIds.read(http).orElse(null), ClientInfo.of(http));
        return new ClaimResponse(result.stampId(), result.status().name(), result.currentStamps(),
                result.stampsRequired(), result.giftCardId(), result.branchId());
    }
}
