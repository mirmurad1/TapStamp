package az.tapstamp.platform.api;

import java.util.UUID;

import az.tapstamp.platform.service.PlatformSmsLimitService;
import az.tapstamp.platform.service.PlatformSmsLimitService.PlatformSmsBudget;
import az.tapstamp.platform.service.PlatformSmsLimitService.TenantSmsLimit;
import az.tapstamp.security.admin.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** SMS limitləri (platforma admini): şirkətin gündəlik limiti və platformanın gündəlik büdcəsi. */
@RestController
@Tag(name = "Platforma: SMS limitləri")
public class PlatformSmsLimitController {

    private final PlatformSmsLimitService limits;

    public PlatformSmsLimitController(PlatformSmsLimitService limits) {
        this.limits = limits;
    }

    public record SmsLimitRequest(
            @Schema(description = "Gündə yeni nömrələrə ən çox neçə giriş kodu (10–100000); null — standart dəyər")
            @Min(10) @Max(100_000) Integer limit) {
    }

    @GetMapping("/api/v1/platform/tenants/{tenantId}/sms-limit")
    @Operation(summary = "Şirkətin gündəlik SMS limiti", description = """
            Limit yalnız sistemdə olmayan (yeni) nömrələrə göndərilən giriş kodlarını sayır; qeydiyyatlı müştərilərin \
            kodları limitə düşmür. `usedToday` — bu gün (şirkətin saatı ilə) yeni nömrələrə göndərilən kodlar.""")
    TenantSmsLimit tenantLimit(@PathVariable UUID tenantId) {
        return limits.tenantLimit(tenantId);
    }

    @PutMapping("/api/v1/platform/tenants/{tenantId}/sms-limit")
    @Operation(summary = "Şirkətin gündəlik SMS limitini dəyişmək", description = "Məsələn, açılış günü üçün artırmaq. Auditə yazılır.")
    TenantSmsLimit updateTenantLimit(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable UUID tenantId,
                                     @Valid @RequestBody SmsLimitRequest request) {
        return limits.updateTenantLimit(principal, tenantId, request.limit());
    }

    @GetMapping("/api/v1/platform/sms-usage/budget")
    @Operation(summary = "Platformanın gündəlik SMS büdcəsi", description = """
            Bu gün (Bakı vaxtı ilə) bütün şirkətlər üzrə göndərilən giriş kodları və gündəlik büdcə \
            (`APP_SMS_PLATFORM_DAILY`). Büdcə dolanda yeni kodlar göndərilmir.""")
    PlatformSmsBudget budget() {
        return limits.platformBudget();
    }
}
