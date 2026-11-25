package az.tapstamp.platform.api;

import java.util.UUID;

import az.tapstamp.customer.admin.CustomerDirectory;
import az.tapstamp.customer.admin.CustomerDirectory.Sort;
import az.tapstamp.platform.service.PlatformCustomerService;
import az.tapstamp.platform.service.PlatformCustomerService.CustomerList;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.paging.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Şirkətin müştəriləri platformada — şirkət panelindəki siyahı ilə eyni, telefonlar maskalanmış. */
@RestController
@Validated
@RequestMapping("/api/v1/platform/tenants/{tenantId}/customers")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Platforma: şirkətin müştəriləri")
public class PlatformCustomerController {

    private final PlatformCustomerService customers;

    public PlatformCustomerController(PlatformCustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    @Operation(summary = "Şirkətin müştəriləri", description = """
            Şirkət panelindəki "Müştərilər" siyahısı: axtarış (`q` — ad və ya tam nömrə), status (`ACTIVE` — aktiv \
            müştərilər), sıralama və səhifələmə. Telefonlar yalnız maskalanmış; `stampsRequired` — aktiv qaydanın möhür \
            sayı.""")
    CustomerList list(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId,
                      @RequestParam(required = false) @Size(max = 80) String q,
                      @RequestParam(required = false) @Pattern(regexp = "ACTIVE|BLOCKED") String status,
                      @RequestParam(defaultValue = "REGISTERED_AT") Sort sort,
                      @RequestParam(defaultValue = "desc") @Pattern(regexp = "asc|desc") String direction,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        return customers.list(principal, tenantId, new CustomerDirectory.Filter(q, null, status, null, null, null, null,
                sort, direction.equals("desc")), page, size);
    }
}
