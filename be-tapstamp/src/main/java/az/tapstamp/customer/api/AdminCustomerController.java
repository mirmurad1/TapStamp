package az.tapstamp.customer.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import az.tapstamp.customer.admin.CustomerAdminService.AdjustmentResult;
import az.tapstamp.customer.admin.CustomerAdminService.FraudFlag;
import az.tapstamp.customer.admin.CustomerAdminService;
import az.tapstamp.customer.admin.CustomerCsvExporter;
import az.tapstamp.customer.admin.CustomerDirectory.Page;
import az.tapstamp.customer.admin.CustomerDirectory.Row;
import az.tapstamp.customer.admin.CustomerDirectory.Sort;
import az.tapstamp.customer.admin.CustomerDirectory.TimelineItem;
import az.tapstamp.customer.admin.CustomerDirectory;
import az.tapstamp.plan.Feature;
import az.tapstamp.plan.RequiresFeature;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin paneli: müştərilər (sahib və filial meneceri; menecer yalnız öz filiallarında möhürü olanları görür). */
@RestController
@RequestMapping("/api/v1/admin/customers")
@PreAuthorize("hasAnyRole('COMPANY_OWNER', 'BRANCH_MANAGER')")
@Tag(name = "Admin: müştərilər")
public class AdminCustomerController {

    private final CustomerDirectory directory;
    private final CustomerAdminService admin;
    private final CustomerCsvExporter exporter;

    public AdminCustomerController(CustomerDirectory directory, CustomerAdminService admin, CustomerCsvExporter exporter) {
        this.directory = directory;
        this.admin = admin;
        this.exporter = exporter;
    }

    public record StatusRequest(@NotNull @Pattern(regexp = "ACTIVE|BLOCKED") String status) {
    }

    @Schema(description = "Əl ilə düzəliş: delta müsbət — əlavə (filial məcburidir), mənfi — silmə")
    public record AdjustmentRequest(
            @NotNull @Min(-50) @Max(50) Integer delta,
            @NotBlank @Size(min = 5, max = 200) String reason,
            UUID branchId) {
    }

    public record ResolveRequest(@NotBlank @Size(min = 3, max = 500) String note) {
    }

    @GetMapping
    @Operation(summary = "Müştərilərin siyahısı", description = """
            Axtarış (`q`): ad hissəsi və ya tam telefon nömrəsi (nömrə HMAC ilə dəqiq tapılır). Filtrlər: dil, status, \
            `inactiveDays` (son N gündə gəlməyənlər), filial, qeydiyyat tarixi. Səhifələmə `page` (0-dan) və `size` \
            (5, 10, 20, 50).""")
    Page list(@AuthenticationPrincipal AdminPrincipal principal,
              @RequestParam(required = false) @Size(max = 80) String q,
              @RequestParam(required = false) String language,
              @RequestParam(required = false) String status,
              @RequestParam(required = false) Integer inactiveDays,
              @RequestParam(required = false) UUID branchId,
              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredFrom,
              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredTo,
              @RequestParam(defaultValue = "REGISTERED_AT") Sort sort,
              @RequestParam(defaultValue = "desc") @Pattern(regexp = "asc|desc") String direction,
              @RequestParam(defaultValue = "0") int page,
              @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        return directory.list(principal, new CustomerDirectory.Filter(q, language, status, inactiveDays, branchId,
                registeredFrom, registeredTo, sort, direction.equals("desc")), page, size);
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Müştərinin detalları")
    Row get(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID customerId) {
        return directory.get(principal, customerId);
    }

    @GetMapping("/{customerId}/timeline")
    @Operation(summary = "Möhür və hədiyyə tarixçəsi", description = "Yenidən köhnəyə; səhifə `page` (0-dan), `size` (5, 10, 20, 50).")
    PageResult<TimelineItem> timeline(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID customerId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        return directory.timeline(principal, customerId, PageQuery.of(page, size));
    }

    @PostMapping("/{customerId}/stamp-adjustments")
    @Operation(summary = "Möhürlərin əl ilə düzəlişi", description = """
            `Idempotency-Key` başlığı məcburidir (8–100 simvol): eyni açarla təkrar sorğu eyni nəticəni qaytarır. \
            Silmə kartdakı saydan çox olarsa, istifadə olunmamış son hədiyyə kartı ləğv edilir; təhvil verilmiş \
            hədiyyəni geri almaq olmaz (`STAMP_ADJUSTMENT_TOO_LARGE`).""")
    AdjustmentResult adjust(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID customerId,
                            @Parameter(description = "Təkrar göndərişdən qorunma açarı")
                            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                            @Valid @RequestBody AdjustmentRequest request) {
        return admin.adjust(principal, customerId,
                new CustomerAdminService.Adjustment(request.delta(), request.reason(), request.branchId()), idempotencyKey);
    }

    @PatchMapping("/{customerId}/status")
    @PreAuthorize("hasRole('COMPANY_OWNER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Bloklamaq və ya blokdan çıxarmaq", description = "Bloklananda müştərinin bütün sessiyaları bağlanır.")
    void updateStatus(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID customerId,
                      @Valid @RequestBody StatusRequest request) {
        admin.updateStatus(principal, customerId, request.status());
    }

    @GetMapping("/{customerId}/fraud-flags")
    @Operation(summary = "Fırıldaq işarələri")
    PageResult<FraudFlag> fraudFlags(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID customerId,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        return admin.fraudFlags(principal, customerId, PageQuery.of(page, size));
    }

    @PostMapping("/fraud-flags/{flagId}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Fırıldaq işarəsini bağlamaq", description = "Qeyd məcburidir; auditə yazılır.")
    void resolve(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID flagId,
                 @Valid @RequestBody ResolveRequest request) {
        admin.resolveFraudFlag(principal, flagId, request.note());
    }

    @RequiresFeature(Feature.CSV_EXPORT)
    @PostMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("hasRole('COMPANY_OWNER')")
    @Operation(summary = "CSV ixracı", description = """
            Siyahı ilə eyni filtrlər. Yalnız sahib, step-up tələb edir. Başlıqlar adminin dilində, UTF-8 (BOM), \
            formula yeridilməsindən qorunur; tam telefon nömrəsi daxil deyil. Auditə yazılır.""")
    void export(@AuthenticationPrincipal AdminPrincipal principal,
                @RequestParam(required = false) @Size(max = 80) String q,
                @RequestParam(required = false) String language,
                @RequestParam(required = false) String status,
                @RequestParam(required = false) Integer inactiveDays,
                @RequestParam(required = false) UUID branchId,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredFrom,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredTo,
                HttpServletResponse response) throws java.io.IOException {
        exporter.authorize(principal);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"customers.csv\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        exporter.write(principal, new CustomerDirectory.Filter(q, language, status, inactiveDays, branchId,
                registeredFrom, registeredTo, Sort.REGISTERED_AT, true), response.getOutputStream());
    }
}
