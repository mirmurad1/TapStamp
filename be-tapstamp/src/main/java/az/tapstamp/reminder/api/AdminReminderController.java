package az.tapstamp.reminder.api;

import az.tapstamp.plan.Feature;
import az.tapstamp.plan.RequiresFeature;
import az.tapstamp.reminder.ReminderService;
import az.tapstamp.reminder.repository.ReminderMapper.ReminderSettings;
import az.tapstamp.security.admin.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Avtomatik xatırlatmalar — şirkət sahibi üçün parametrlər və statistika. */
@RestController
@RequiresFeature(Feature.REMINDERS)
@RequestMapping("/api/v1/admin/reminders")
@PreAuthorize("hasRole('COMPANY_OWNER')")
@Tag(name = "Admin: xatırlatmalar")
public class AdminReminderController {

    private final ReminderService reminders;

    public AdminReminderController(ReminderService reminders) {
        this.reminders = reminders;
    }

    public record ReminderSettingsRequest(@NotNull Boolean nearRewardEnabled, @NotNull Boolean winbackEnabled,
                                          @NotNull Integer winbackDays, @NotNull Boolean unusedGiftEnabled) {
    }

    @GetMapping("/settings")
    @Operation(summary = "Xatırlatmaların parametrləri")
    ReminderSettings settings(@AuthenticationPrincipal AdminPrincipal principal) {
        return reminders.settings(principal);
    }

    @PutMapping("/settings")
    @Operation(summary = "Parametrləri dəyişmək", description = """
            Hər xatırlatma ayrıca açılır: hədiyyəyə 1 möhür qalıb, uzun müddətdir gəlməyib (`winbackDays`: 30, 60, 90), \
            istifadə olunmamış hədiyyə. Yalnız web push ilə, 10:00–20:00, müştəriyə həftədə ən çox bir. Auditə yazılır.""")
    ReminderSettings updateSettings(@AuthenticationPrincipal AdminPrincipal principal,
                                    @Valid @RequestBody ReminderSettingsRequest request) {
        return reminders.updateSettings(principal, new ReminderSettings(request.nearRewardEnabled(),
                request.winbackEnabled(), request.winbackDays(), request.unusedGiftEnabled()));
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistika", description = """
            Son 30 gün: növ üzrə göndərilən xatırlatmalar və onlardan sonra 7 gün ərzində gələn (NFC möhürü alan) \
            müştərilər; bildirişləri açıq olan müştərilərin sayı (xatırlatmalar yalnız onlara gedir).""")
    ReminderService.ReminderStats stats(@AuthenticationPrincipal AdminPrincipal principal) {
        return reminders.stats(principal);
    }
}
