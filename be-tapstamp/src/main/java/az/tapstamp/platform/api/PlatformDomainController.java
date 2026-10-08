package az.tapstamp.platform.api;

import java.util.List;
import java.util.UUID;

import az.tapstamp.platform.service.PlatformDomainService;
import az.tapstamp.platform.service.PlatformDomainService.Domain;
import az.tapstamp.security.admin.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Şirkətin domenləri — yalnız platforma admini. */
@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}/domains")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Platforma: domenlər")
public class PlatformDomainController {

    private final PlatformDomainService domains;

    public PlatformDomainController(PlatformDomainService domains) {
        this.domains = domains;
    }

    public record DomainRequest(@NotBlank @Size(max = 253) String hostname) {
    }

    @GetMapping
    @Operation(summary = "Şirkətin domenləri", description = "Əsas domen birinci; TLS sertifikatının vəziyyəti ilə.")
    List<Domain> list(@PathVariable UUID tenantId) {
        return domains.list(tenantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Domen əlavə etmək", description = "Məs. gloriyajeans.mockcoffe.com. Sertifikat avtomatik alınır (PENDING → ISSUED).")
    Domain add(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId,
               @Valid @RequestBody DomainRequest request) {
        return domains.add(principal, tenantId, request.hostname());
    }

    @PostMapping("/{domainId}/primary")
    @Operation(summary = "Əsas domen etmək", description = """
            Step-up tələb edir. Dəvət və bərpa linkləri, yeni çiplərin URL-i bu domenə yazılır; köhnə domen işləməyə \
            davam edir (artıq proqramlaşdırılmış çiplər üçün).""")
    Domain makePrimary(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId,
                       @PathVariable UUID domainId) {
        return domains.makePrimary(principal, tenantId, domainId);
    }

    @DeleteMapping("/{domainId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Domeni silmək", description = "Step-up tələb edir; əsas domen silinə bilməz (DOMAIN_IS_PRIMARY).")
    void remove(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId,
                @PathVariable UUID domainId) {
        domains.remove(principal, tenantId, domainId);
    }
}
