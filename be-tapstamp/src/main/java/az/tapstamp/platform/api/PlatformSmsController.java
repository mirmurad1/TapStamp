package az.tapstamp.platform.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import az.tapstamp.notification.sms.SmsLog;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SMS hesabatı: şirkətlərin seçilmiş dövrdə nə qədər SMS istifadə etdiyi (platforma bazasındakı SMS jurnalından). */
@RestController
@RequestMapping("/api/v1/platform/sms-usage")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Platforma: SMS hesabatı")
public class PlatformSmsController {

    /** Ən uzun dövr: iki il. */
    private static final int MAX_DAYS = 731;
    private static final int PAGE_SIZE = 20;

    private final SmsLog smsLog;

    public PlatformSmsController(SmsLog smsLog) {
        this.smsLog = smsLog;
    }

    @Schema(description = "Şirkətin dövr ərzində uğurla göndərilmiş SMS-lərinin sayı")
    public record SmsUsageRow(UUID tenantId, String slug, LocalizedText displayName, long messages) {
    }

    @Schema(description = "Dövr (hər iki gün daxil, Bakı vaxtı), şirkətlər üzrə sətirlər və cəm (yalnız uğurlu SMS-lər)")
    public record SmsUsageReport(LocalDate from, LocalDate to, List<SmsUsageRow> tenants, long messages) {
    }

    @GetMapping
    @Operation(summary = "Şirkətlər üzrə SMS istifadəsi", description = """
            Yalnız uğurla göndərilmiş SMS-lər sayılır. `from` və `to` daxil olmaqla (YYYY-MM-DD, Bakı vaxtı), ən çox \
            2 il. SMS göndərmiş şirkətlər, ən çoxdan aza sıralanır.""")
    SmsUsageReport usage(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireRange(from, to);
        List<SmsUsageRow> rows = smsLog.usage(from, to).stream()
                .map(u -> new SmsUsageRow(u.tenantId(), u.slug(), u.displayName(), u.messages()))
                .toList();
        return new SmsUsageReport(from, to, rows, rows.stream().mapToLong(SmsUsageRow::messages).sum());
    }

    @Schema(description = "Göndərilə bilməyən SMS: şirkət, nömrə, mətn (kod gizlədilmiş) və səbəb")
    public record SmsFailureRow(UUID id, Instant createdAt, UUID tenantId, String slug, LocalizedText displayName,
                                String phone, String text, String error) {
    }

    @Schema(description = "Uğursuz SMS-lərin bir səhifəsi; total — dövrdəki ümumi say")
    public record SmsFailurePage(List<SmsFailureRow> items, int page, int size, long total) {
    }

    @GetMapping("/failures")
    @Operation(summary = "Uğursuz SMS-lər və səbəbləri", description = """
            `from` və `to` daxil olmaqla (Bakı vaxtı), ən çox 2 il; yenidən köhnəyə, 20-lik səhifələr (`page` 0-dan).""")
    SmsFailurePage failures(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(defaultValue = "0") @Min(0) @Max(100_000) int page) {
        requireRange(from, to);
        SmsLog.FailurePage result = smsLog.failures(from, to, page, PAGE_SIZE);
        return new SmsFailurePage(result.items().stream()
                .map(f -> new SmsFailureRow(f.id(), f.createdAt(), f.tenantId(), f.slug(), f.displayName(), f.phone(),
                        f.text(), f.error()))
                .toList(), result.page(), result.size(), result.total());
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
