package az.tapstamp.tier.api;

import az.tapstamp.customer.auth.CustomerPrincipal;
import az.tapstamp.tier.TierService;
import az.tapstamp.tier.TierService.CustomerTier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Müştəri səviyyəsi — müştəri tətbiqi üçün. */
@RestController
@Tag(name = "Müştəri: səviyyə")
public class CustomerTierController {

    private final TierService tiers;

    public CustomerTierController(TierService tiers) {
        this.tiers = tiers;
    }

    @GetMapping("/api/v1/me/tier")
    @Operation(summary = "Mənim səviyyəm", description = """
            Ziyarətlərin sayı, cari səviyyə (hələ yoxdursa boş), növbəti səviyyə və ona qalan ziyarətlər. Şirkətdə \
            səviyyə yoxdursa və ya paketdə imkan yoxdursa `enabled = false`.""")
    CustomerTier mine(@AuthenticationPrincipal CustomerPrincipal principal) {
        return tiers.forCustomer(principal);
    }
}
