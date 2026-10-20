package az.tapstamp.audit.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import az.tapstamp.audit.AuditLogQuery;
import az.tapstamp.audit.AuditLogQuery.Entry;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
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
    @Operation(summary = "Audit jurnalı", description = "Filtrlər: əməliyyat, icraçı, tarix aralığı (şirkətin yerli tarixləri). Yenidən köhnəyə; səhifə `page` (0-dan), `size` (5, 10, 20, 50).")
    PageResult<Entry> list(@RequestParam(required = false) @Pattern(regexp = "^[A-Z_]{1,64}$") String action,
                           @RequestParam(required = false) UUID actorId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        return audit.list(action, actorId, from, to, PageQuery.of(page, size));
    }

    @GetMapping("/actions")
    @Operation(summary = "Jurnalda olan əməliyyat adları (filtr üçün)")
    List<String> actions() {
        return audit.actions();
    }
}
