package az.cafeloyalty.audit.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import az.cafeloyalty.audit.AuditLogQuery;
import az.cafeloyalty.audit.AuditLogQuery.Entry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Şirkətin audit jurnalı — yalnız sahib. */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('COMPANY_OWNER')")
@Tag(name = "Admin: audit jurnalı")
public class AdminAuditController {

    private final AuditLogQuery audit;

    public AdminAuditController(AuditLogQuery audit) {
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Audit jurnalı", description = """
            Filtrlər: əməliyyat, icraçı, tarix aralığı (şirkətin yerli tarixləri). 50-lik səhifələr; növbəti səhifə üçün \
            `before` — son elementin vaxtı.""")
    List<Entry> list(@RequestParam(required = false) @Pattern(regexp = "^[A-Z_]{1,64}$") String action,
                     @RequestParam(required = false) UUID actorId,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                     @RequestParam(required = false) Instant before) {
        return audit.list(action, actorId, from, to, before);
    }

    @GetMapping("/actions")
    @Operation(summary = "Jurnalda olan əməliyyat adları (filtr üçün)")
    List<String> actions() {
        return audit.actions();
    }
}
