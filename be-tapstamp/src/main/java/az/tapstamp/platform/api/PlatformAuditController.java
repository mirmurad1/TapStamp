package az.tapstamp.platform.api;

import java.time.Instant;
import java.util.List;

import az.tapstamp.audit.AuditLogQuery;
import az.tapstamp.audit.PlatformAuditEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platforma audit jurnalı (şirkətlərin yaradılması, dayandırılması, domenlər, sahiblərin 2FA-sı) — yalnız oxuma. */
@RestController
@RequestMapping("/api/v1/platform/audit-logs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Platforma: audit jurnalı")
public class PlatformAuditController {

    private final AuditLogQuery audit;

    public PlatformAuditController(AuditLogQuery audit) {
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Platforma audit jurnalı", description = "Yenidən köhnəyə, 50-lik səhifələr; növbəti səhifə üçün `before`.")
    List<PlatformAuditEntry> list(@RequestParam(required = false) @Pattern(regexp = "^[A-Z_]{1,64}$") String action,
                                  @RequestParam(required = false) Instant before) {
        return audit.platform(action, before);
    }
}
