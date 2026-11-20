package az.tapstamp.catalog.api;

import java.util.UUID;

import az.tapstamp.catalog.DealNotificationService;
import az.tapstamp.customer.auth.CustomerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Müştəri: endirim bildirişinə toxunub menyunu açmağın qeydi (elanın nəticəsi üçün). */
@RestController
@RequestMapping("/api/v1/me/deal-notifications")
@Tag(name = "Müştəri: bildirişlər")
public class CustomerDealNotificationController {

    private final DealNotificationService deals;

    public CustomerDealNotificationController(DealNotificationService deals) {
        this.deals = deals;
    }

    @PostMapping("/{announcementId}/opened")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Endirim bildirişinə toxunub menyunu açdı", description = "Elanın nəticəsində bir dəfə sayılır.")
    void opened(@AuthenticationPrincipal CustomerPrincipal principal, @PathVariable UUID announcementId) {
        deals.opened(principal.customerId(), announcementId);
    }
}
