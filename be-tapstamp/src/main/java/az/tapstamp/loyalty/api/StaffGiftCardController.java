package az.tapstamp.loyalty.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.tapstamp.loyalty.reward.GiftCardRedemptionService;
import az.tapstamp.loyalty.reward.GiftCardRedemptionService.GiftCardCheck;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.i18n.LocalizedText;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** İşçi konsolu: hədiyyə kartının kod və ya QR ilə yoxlanılması və təhvili. */
@RestController
@RequestMapping("/api/v1/admin/staff-console/gift-cards")
@Tag(name = "Admin: işçi konsolu")
public class StaffGiftCardController {

    private final GiftCardRedemptionService redemptions;

    public StaffGiftCardController(GiftCardRedemptionService redemptions) {
        this.redemptions = redemptions;
    }

    public record GiftCardCheckRequest(
            @Schema(description = "Kartın kodu (tire və boşluqlar nəzərə alınmır), QR-dakı mətn eynidir")
            @NotBlank @Size(max = 32) String code) {
        @Override
        public String toString() {
            return "GiftCardCheckRequest[***]";
        }
    }

    public record GiftCardRedeemRequest(@NotBlank @Size(max = 32) String code, @NotNull UUID branchId) {
        @Override
        public String toString() {
            return "GiftCardRedeemRequest[branchId=" + branchId + "]";
        }
    }

    @Schema(description = "Hədiyyə kartı: status AVAILABLE olduqda təhvil verilə bilər. Telefon yalnız maskalıdır.")
    public record GiftCardCheckResponse(
            UUID id,
            String code,
            @Schema(allowableValues = {"AVAILABLE", "REDEEMED", "REVOKED", "EXPIRED"}) String status,
            LocalizedText rewardName,
            String customerName,
            String phoneMasked,
            Instant earnedAt,
            Instant expiresAt,
            Instant redeemedAt,
            LocalizedText redeemedBranchName) {
        static GiftCardCheckResponse from(GiftCardCheck card) {
            return new GiftCardCheckResponse(card.id(), card.code(), card.status().name(), card.name(),
                    card.customerName(), card.phoneMasked(), card.earnedAt(), card.expiresAt(), card.redeemedAt(),
                    card.redeemedBranchName());
        }
    }

    @PostMapping("/check")
    @Operation(summary = "Hədiyyə kartının yoxlanması", description = """
            Kart tapılırsa statusu ilə qaytarılır (yan təsiri yoxdur), tapılmırsa `GIFT_CARD_NOT_FOUND`. İşçi üzrə \
            dəqiqədə 30 cəhd; uğursuz cəhdlər audit jurnalına yazılır.""")
    GiftCardCheckResponse check(@Valid @RequestBody GiftCardCheckRequest request,
                                @AuthenticationPrincipal AdminPrincipal principal) {
        return GiftCardCheckResponse.from(redemptions.check(principal, request.code()));
    }

    @PostMapping("/redeem")
    @Operation(summary = "Hədiyyənin təhvili", description = """
            Kart birdəfəlik istifadə olunmuş sayılır (filial və işçi qeyd olunur). Eyni işçinin təkrar basışı eyni \
            nəticəni qaytarır; başqa təhvil cəhdi `GIFT_CARD_ALREADY_REDEEMED`. Müddəti bitmiş kart \
            `GIFT_CARD_EXPIRED`.""")
    GiftCardCheckResponse redeem(@Valid @RequestBody GiftCardRedeemRequest request,
                                 @AuthenticationPrincipal AdminPrincipal principal) {
        return GiftCardCheckResponse.from(redemptions.redeem(principal, request.code(), request.branchId()));
    }

    @GetMapping("/recent")
    @Operation(summary = "Son təhvillər", description = "Filialda son 24 saatda təhvil verilmiş hədiyyə kartları.")
    List<GiftCardCheckResponse> recent(@RequestParam UUID branchId, @AuthenticationPrincipal AdminPrincipal principal) {
        return redemptions.recent(principal, branchId).stream().map(GiftCardCheckResponse::from).toList();
    }
}
