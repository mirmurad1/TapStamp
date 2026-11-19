package az.tapstamp.platform.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import az.tapstamp.notification.sms.SmsLog;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.pii.PhoneMasker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    private final SmsLog smsLog;

    public PlatformSmsController(SmsLog smsLog) {
        this.smsLog = smsLog;
    }

    @Schema(description = "Şirkətin dövr ərzində uğurla göndərilmiş SMS-lərinin sayı")
    public record SmsUsageRow(UUID tenantId, String slug, LocalizedText displayName, long messages, long otp,
                              long campaign) {
    }

    @Schema(description = "Dövr (hər iki gün daxil, Bakı vaxtı), şirkətlər üzrə sətirlər və cəm (yalnız uğurlu SMS-lər)")
    public record SmsUsageReport(LocalDate from, LocalDate to, List<SmsUsageRow> tenants, long messages, long otp,
                                 long campaign) {
    }

    @GetMapping
    @Operation(summary = "Şirkətlər üzrə SMS istifadəsi", description = """
            Yalnız uğurla göndərilmiş SMS-lər sayılır. `from` və `to` daxil olmaqla (YYYY-MM-DD, Bakı vaxtı), ən çox \
            2 il. SMS göndərmiş şirkətlər, ən çoxdan aza sıralanır.""")
    SmsUsageReport usage(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireRange(from, to);
        List<SmsUsageRow> rows = smsLog.usage(from, to).stream()
                .map(u -> new SmsUsageRow(u.tenantId(), u.slug(), u.displayName(), u.messages(), u.otp(), u.campaign()))
                .toList();
        return new SmsUsageReport(from, to, rows, rows.stream().mapToLong(SmsUsageRow::messages).sum(),
                rows.stream().mapToLong(SmsUsageRow::otp).sum(), rows.stream().mapToLong(SmsUsageRow::campaign).sum());
    }

    @Schema(description = "Jurnaldakı SMS: şirkət, nömrə, mətn (OTP kodu gizlədilmiş), nəticə və xəta (uğursuzdursa)")
    public record SmsMessageRow(UUID id, Instant createdAt, UUID tenantId, String slug, LocalizedText displayName,
                                String phone, String text,
                                @Schema(allowableValues = {"OTP", "CAMPAIGN"}) String purpose,
                                @Schema(allowableValues = {"SENT", "FAILED"}) String status, String error) {
    }

    @Schema(description = "SMS-lərin bir səhifəsi; total — filtrə uyğun ümumi say")
    public record SmsMessagePage(List<SmsMessageRow> items, int page, int size, long total) {
    }

    @GetMapping("/messages")
    @Operation(summary = "SMS axtarışı", description = """
            Hansı nömrəyə hansı mesaj getdiyi. `from` və `to` daxil olmaqla (Bakı vaxtı), ən çox 2 il; `status` — \
            SENT və ya FAILED (boşdursa hamısı); `phone` — nömrə və ya onun hissəsi (+994…, 050…, 50 123). \
            Yenidən köhnəyə; səhifə `page` (0-dan), `size` (5, 10, 20, 50).""")
    SmsMessagePage messages(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(required = false) @Pattern(regexp = "SENT|FAILED") String status,
                            @RequestParam(required = false) @Size(max = 32) String phone,
                            @RequestParam(defaultValue = "0") @Min(0) @Max(100_000) int page,
                            @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE_PARAM) int size) {
        requireRange(from, to);
        PageQuery.of(page, size); // ölçü yalnız 5, 10, 20, 50
        SmsLog.MessagePage result = smsLog.messages(from, to, status, phone, page, size);
        return new SmsMessagePage(result.items().stream()
                .map(m -> new SmsMessageRow(m.id(), m.createdAt(), m.tenantId(), m.slug(), m.displayName(), PhoneMasker.mask(m.phone()),
                        m.text(), m.purpose(), m.status(), m.error()))
                .toList(), result.page(), result.size(), result.total());
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
