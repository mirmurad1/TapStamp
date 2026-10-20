package az.tapstamp.referral.api;

import az.tapstamp.customer.auth.CustomerPrincipal;
import az.tapstamp.referral.ReferralService;
import az.tapstamp.referral.ReferralService.CustomerReferral;
import az.tapstamp.referral.ReferralService.ProgramInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dost dəvəti — müştəri tətbiqi üçün. */
@RestController
@Tag(name = "Müştəri: dost dəvəti")
public class CustomerReferralController {

    private final ReferralService referrals;

    public CustomerReferralController(ReferralService referrals) {
        this.referrals = referrals;
    }

    @GetMapping("/api/v1/me/referral")
    @Operation(summary = "Mənim dəvət kodum", description = """
            Proqram aktivdirsə: şəxsi kod (ilk dəfə istəniləndə yaranır), bonus möhürlər və dəvətlər üzrə saylar \
            (dəvət olunanlar, bonus qazandıranlar, hələ gəlməyənlər). Söndürülübsə `enabled = false`.""")
    CustomerReferral mine(@AuthenticationPrincipal CustomerPrincipal principal) {
        return referrals.forCustomer(principal);
    }

    @GetMapping("/api/v1/public/referral-program")
    @Operation(summary = "Dəvət proqramı (giriş tələb etmir)", description = """
            Dəvət linki açılanda göstərmək üçün: proqram aktivdirmi və dosta neçə bonus möhür verilir.""")
    ProgramInfo program() {
        return referrals.program();
    }
}
