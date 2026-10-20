package az.tapstamp.reminder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.notification.push.PushService;
import az.tapstamp.reminder.repository.ReminderMapper;
import az.tapstamp.reminder.repository.ReminderMapper.Candidate;
import az.tapstamp.reminder.repository.ReminderMapper.KindStats;
import az.tapstamp.reminder.repository.ReminderMapper.ReminderSettings;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.TenantInfo;
import az.tapstamp.tenancy.registry.TenantDirectory;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Avtomatik xatırlatmalar (qərar #55) — yalnız web push ilə, pulsuz. Sahib hər növü ayrıca açır (default söndürülü):
 * <ul>
 *   <li><b>NEAR_REWARD</b> — kartında "hədd − 1" möhür var və 7 gündür gəlməyib;</li>
 *   <li><b>UNUSED_GIFT</b> — istifadə olunmamış hədiyyəsi var və 7 gündür gəlməyib;</li>
 *   <li><b>WINBACK</b> — 30/60/90 gündür gəlməyib.</li>
 * </ul>
 * Qaydalar: yalnız 10:00–20:00 (şirkətin saatı ilə); müştəriyə həftədə ən çox bir xatırlatma (prioritet: hədiyyə,
 * hədiyyəyə az qalıb, uzun fasilə); hər növ bir dövrdə bir dəfə — dövr müştərinin növbəti gəlişi ilə yenilənir.
 */
@Service
public class ReminderService {

    public static final List<String> KINDS = List.of("NEAR_REWARD", "UNUSED_GIFT", "WINBACK");
    static final LocalTime WINDOW_START = LocalTime.of(10, 0);
    static final LocalTime WINDOW_END = LocalTime.of(20, 0);
    private static final Duration WEEK = Duration.ofDays(7);
    private static final int BATCH = 200;
    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    public record ReminderStats(long customersWithPush, List<KindStats> kinds) {
    }

    private final ReminderMapper reminders;
    private final PushService push;
    private final TenantDirectory directory;
    private final AuditLogger audit;
    private final Clock clock;

    public ReminderService(ReminderMapper reminders, PushService push, TenantDirectory directory, AuditLogger audit,
                           Clock clock) {
        this.reminders = reminders;
        this.push = push;
        this.directory = directory;
        this.audit = audit;
        this.clock = clock;
    }

    /** Bütün aktiv şirkətlər üçün bir addım (taymer çağırır). */
    public void run() {
        for (TenantRecord tenant : directory.findAll()) {
            if (tenant.status().outOfService()) {
                continue;
            }
            try {
                TenantContext.runAs(tenant.toInfo(), this::runForCurrentTenant);
            } catch (RuntimeException e) {
                log.warn("Reminders failed for tenant {}: {}", tenant.slug(), e.getClass().getSimpleName());
            }
        }
    }

    /** Cari şirkət üçün bir addım (testlər birbaşa çağırır); göndərilən xatırlatmaların sayı. */
    public int runForCurrentTenant() {
        TenantInfo tenant = TenantContext.require();
        Instant now = clock.instant();
        LocalTime local = now.atZone(tenant.timezone()).toLocalTime();
        if (local.isBefore(WINDOW_START) || !local.isBefore(WINDOW_END) || !push.config().enabled()) {
            return 0;
        }
        ReminderSettings settings = reminders.settings();
        Instant weekAgo = now.minus(WEEK);
        // Bir addımda da müştəriyə yalnız bir xatırlatma
        Set<UUID> notified = new HashSet<>();
        int sent = 0;
        if (settings.unusedGiftEnabled()) {
            for (Candidate candidate : reminders.unusedGift(weekAgo, weekAgo, now, BATCH)) {
                sent += send(candidate, "UNUSED_GIFT", PushService.Kind.REMINDER_UNUSED_GIFT, notified, now);
            }
        }
        if (settings.nearRewardEnabled()) {
            for (Candidate candidate : reminders.nearReward(weekAgo, weekAgo, BATCH)) {
                sent += send(candidate, "NEAR_REWARD", PushService.Kind.REMINDER_NEAR_REWARD, notified, now);
            }
        }
        if (settings.winbackEnabled()) {
            Instant visitedBefore = now.minus(Duration.ofDays(settings.winbackDays()));
            for (Candidate candidate : reminders.winback(visitedBefore, weekAgo, BATCH)) {
                PushService.Kind kind = candidate.current() > 0 && candidate.required() > 0
                        ? PushService.Kind.REMINDER_WINBACK : PushService.Kind.REMINDER_WINBACK_EMPTY;
                sent += send(candidate, "WINBACK", kind, notified, now);
            }
        }
        return sent;
    }

    private int send(Candidate candidate, String kind, PushService.Kind pushKind, Set<UUID> notified, Instant now) {
        if (notified.contains(candidate.customerId())
                || reminders.record(Ids.newId(), candidate.customerId(), kind, candidate.cycleKey(), now) != 1) {
            return 0;
        }
        notified.add(candidate.customerId());
        push.notify(candidate.customerId(), pushKind, candidate.current(), candidate.required());
        return 1;
    }

    // ---------------------------------------------------------------- admin (şirkət sahibi)

    public ReminderSettings settings(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        return reminders.settings();
    }

    public ReminderSettings updateSettings(AdminPrincipal principal, ReminderSettings next) {
        AccessPolicy.requireCompanyWide(principal);
        if (!List.of(30, 60, 90).contains(next.winbackDays())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        ReminderSettings before = reminders.settings();
        reminders.updateSettings(next.nearRewardEnabled(), next.winbackEnabled(), next.winbackDays(),
                next.unusedGiftEnabled(), principal.userId(), clock.instant());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("before", before);
        details.put("after", next);
        audit.tenant("REMINDER_SETTINGS_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "reminder_settings",
                "company", details);
        return reminders.settings();
    }

    /** Son 30 gün: növ üzrə göndərilən və 7 gün ərzində gələn müştərilər; push-u açıq olan müştərilərin sayı. */
    public ReminderStats stats(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        Map<String, KindStats> byKind = new LinkedHashMap<>();
        for (String kind : KINDS) {
            byKind.put(kind, new KindStats(kind, 0, 0));
        }
        for (KindStats row : reminders.stats(clock.instant().minus(Duration.ofDays(30)))) {
            byKind.put(row.kind(), row);
        }
        return new ReminderStats(push.customersWithPush(), List.copyOf(byKind.values()));
    }
}
