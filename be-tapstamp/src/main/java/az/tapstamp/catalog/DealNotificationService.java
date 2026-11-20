package az.tapstamp.catalog;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.catalog.repository.CatalogMapper;
import az.tapstamp.catalog.repository.DealNotificationMapper;
import az.tapstamp.catalog.repository.DealNotificationMapper.DealAnnouncement;
import az.tapstamp.catalog.repository.DealNotificationMapper.BranchHours;
import az.tapstamp.catalog.repository.DealNotificationMapper.DealItem;
import az.tapstamp.catalog.repository.DealNotificationMapper.Recipient;
import az.tapstamp.catalog.repository.DealNotificationMapper.Settings;
import az.tapstamp.catalog.repository.DealNotificationMapper.WaitingPush;
import az.tapstamp.loyalty.stamp.BusinessHours;
import az.tapstamp.notification.push.PushService;
import az.tapstamp.plan.Addon;
import az.tapstamp.plan.PlanService;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.TenantInfo;
import az.tapstamp.tenancy.registry.TenantDirectory;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Endirim bildirişləri (yalnız web push, pulsuz). Yeni endirimlər tək-tək yox, <b>bir bildirişdə</b> elan olunur:
 * <ul>
 *   <li>avtomatik — şirkətin seçdiyi saatda, gündə bir yoxlama;</li>
 *   <li>əl ilə — sahib "İndi göndər" düyməsi ilə.</li>
 * </ul>
 * Bildiriş müştəriyə onun filialının (sonuncu gəldiyi filial; yoxdursa — istənilən filial) <b>iş saatında</b> çatır:
 * filial bağlıdırsa, növbədə qalır və filial açılanda gedir; 24 saat ərzində açılmasa, ləğv olunur.
 * Qaydalar: şirkət gündə ən çox bir elan göndərir, müştəri gündə ən çox bir endirim bildirişi alır; hər endirim bir
 * dəfə elan olunur (faiz dəyişəndə təkrar getmir), başlanğıcı sonrakı tarixdə olan endirim başladığı gün elan olunur;
 * yalnız bir filialda olan endirim həmin filiala gələn müştərilərə gedir; "hazırda yoxdur" məhsul elan olunmur.
 * Həmin gün yeni endirim yoxdursa, son günü olan endirimlər üçün bir dəfə "Son gün" xatırlatması gedir. Endirim
 * bildirişləri ayrıca söndürülmür (push-u açıq olan hər müştəri alır); bildirişə toxunub menyunu açanlar sayılır.
 */
@Service
public class DealNotificationService {

    /** Filial bu müddətdə açılmasa, növbədəki bildiriş ləğv olunur. */
    static final Duration WAIT_LIMIT = Duration.ofHours(24);
    /** Bildirişdə adı çəkilən endirimlərin sayı; qalanları "və daha N təklif". */
    static final int LISTED = 2;
    private static final int BATCH = 500;
    private static final int RECENT = 5;
    private static final Logger log = LoggerFactory.getLogger(DealNotificationService.class);

    public enum SendStatus {
        SENT,
        /** Elan olunmamış endirim yoxdur. */
        NOTHING_TO_SEND,
        /** Bu gün artıq elan göndərilib (gündə bir). */
        ALREADY_SENT_TODAY,
        /** Push qurulmayıb və ya paketdə yoxdur, ya da menyu söndürülüb. */
        UNAVAILABLE
    }

    public record DealNotificationSettings(boolean autoEnabled, String sendTime, boolean lastDayEnabled) {
    }

    public record PendingDeal(UUID itemId, LocalizedText name, int percent) {
    }

    /** Müştərinin görəcəyi bildirişin mətni (sahibin dilində). */
    public record DealPreview(String title, String body) {
    }

    /**
     * Menyu səhifəsindəki "Endirim bildirişləri" kartı: parametrlər, elan gözləyən endirimlər və önizləmə, bu gün
     * göndərmək olarmı, bildirişləri açıq olan müştərilər və son elanların nəticəsi.
     */
    public record DealNotificationOverview(DealNotificationSettings settings, List<PendingDeal> pending, @Nullable DealPreview preview,
                           boolean available, boolean sentToday, long customersWithPush,
                           List<DealAnnouncement> recent) {
    }

    /** {@code recipients} — dərhal göndərildi; {@code waiting} — filialı bağlıdır, açılanda gedəcək. */
    public record DealSendResult(SendStatus status, int recipients, int waiting) {

        static DealSendResult of(SendStatus status) {
            return new DealSendResult(status, 0, 0);
        }
    }

    private final DealNotificationMapper deals;
    private final CatalogMapper catalog;
    private final PushService push;
    private final PlanService plans;
    private final TenantDirectory directory;
    private final MessageSource messages;
    private final AuditLogger audit;
    private final Clock clock;

    public DealNotificationService(DealNotificationMapper deals, CatalogMapper catalog, PushService push,
                                   PlanService plans, TenantDirectory directory,
                                   @Qualifier(AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME) MessageSource messages,
                                   AuditLogger audit, Clock clock) {
        this.deals = deals;
        this.catalog = catalog;
        this.push = push;
        this.plans = plans;
        this.directory = directory;
        this.messages = messages;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- avtomatik (taymer)

    /** Bütün aktiv şirkətlər üçün bir addım (taymer çağırır). */
    public void run() {
        for (TenantRecord tenant : directory.findAll()) {
            if (tenant.status().outOfService() || plans.expired(tenant.id())) {
                continue;
            }
            try {
                TenantContext.runAs(tenant.toInfo(), this::runForCurrentTenant);
            } catch (RuntimeException e) {
                log.warn("Deal notifications failed for tenant {}: {}", tenant.slug(), e.getClass().getSimpleName());
            }
        }
    }

    /**
     * Cari şirkət: növbədəki bildirişlər (filialı indi açıq olan müştərilərə) və avtomatik elan — göndəriş açıqdırsa,
     * seçilmiş saat çatıbsa və bu gün hələ yoxlanmayıbsa. Gün yoxlamadan əvvəl "tutulur" (bir neçə server eyni anda
     * göndərməsin). Testlər birbaşa çağırır.
     */
    public DealSendResult runForCurrentTenant() {
        deliverWaiting();
        Settings settings = deals.settings();
        ZonedDateTime local = now();
        LocalDate today = local.toLocalDate();
        if (!settings.autoEnabled() || local.toLocalTime().isBefore(settings.sendTime())
                || today.equals(settings.lastAutoRunOn()) || !available()) {
            return DealSendResult.of(SendStatus.NOTHING_TO_SEND);
        }
        if (deals.claimAutoRun(today) != 1) {
            return DealSendResult.of(SendStatus.NOTHING_TO_SEND);
        }
        return announce("AUTO", null, settings.lastDayEnabled(), today);
    }

    // ---------------------------------------------------------------- admin (şirkət sahibi)

    public DealNotificationOverview overview(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        ZonedDateTime local = now();
        LocalDate today = local.toLocalDate();
        List<DealItem> pending = deals.newDeals(today);
        DealPreview preview = pending.isEmpty() ? null
                : new DealPreview(text("NEW", "title", pending.size(), pending, principal.language()),
                        text("NEW", "body", pending.size(), pending, principal.language()));
        return new DealNotificationOverview(settingsView(deals.settings()),
                pending.stream().map(item -> new PendingDeal(item.id(), item.name(), percent(item))).toList(),
                preview, available(), deals.announcedOn(today), push.customersWithPush(), deals.recent(RECENT));
    }

    public DealNotificationOverview updateSettings(AdminPrincipal principal, boolean autoEnabled, LocalTime sendTime,
                                   boolean lastDayEnabled) {
        AccessPolicy.requireCompanyWide(principal);
        if (sendTime.getSecond() != 0 || sendTime.getNano() != 0 || sendTime.getMinute() % 30 != 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        DealNotificationSettings before = settingsView(deals.settings());
        deals.updateSettings(autoEnabled, sendTime, lastDayEnabled, principal.userId(), clock.instant());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("before", before);
        details.put("after", new DealNotificationSettings(autoEnabled, sendTime.toString(), lastDayEnabled));
        audit.tenant("DEAL_NOTIFICATION_SETTINGS_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(),
                "deal_notification_settings", "company", details);
        return overview(principal);
    }

    /** "İndi göndər": elan gözləyən yeni endirimlər bir bildirişdə (son gün xatırlatması yalnız avtomatikdir). */
    public DealSendResult sendNow(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        if (!available()) {
            return DealSendResult.of(SendStatus.UNAVAILABLE);
        }
        DealSendResult result = announce("MANUAL", principal.userId(), false, now().toLocalDate());
        if (result.status() == SendStatus.SENT) {
            audit.tenant("DEAL_NOTIFICATION_SENT", AuditLogger.ActorType.STAFF, principal.userId(), "catalog", null,
                    Map.of("recipients", result.recipients(), "waiting", result.waiting()));
        }
        return result;
    }

    // ---------------------------------------------------------------- müştəri

    /** Bildirişə toxunub menyunu açdı: elanın nəticəsində sayılır (bir dəfə). */
    public void opened(UUID customerId, UUID announcementId) {
        deals.markOpened(announcementId, customerId, clock.instant());
    }

    // ---------------------------------------------------------------- elan

    private DealSendResult announce(String trigger, @Nullable UUID staffId, boolean lastDayAllowed, LocalDate today) {
        if (deals.announcedOn(today)) {
            return DealSendResult.of(SendStatus.ALREADY_SENT_TODAY);
        }
        String kind = "NEW";
        List<DealItem> items = deals.newDeals(today);
        if (items.isEmpty() && lastDayAllowed) {
            kind = "LAST_DAY";
            items = deals.lastDayDeals(today);
        }
        if (items.isEmpty()) {
            return DealSendResult.of(SendStatus.NOTHING_TO_SEND);
        }
        UUID announcementId = Ids.newId();
        Instant now = clock.instant();
        if (deals.insertAnnouncement(announcementId, kind, trigger, today, items.size(), staffId, now) != 1) {
            return DealSendResult.of(SendStatus.ALREADY_SENT_TODAY);
        }
        List<UUID> ids = items.stream().map(DealItem::id).toList();
        if (kind.equals("NEW")) {
            deals.markAnnounced(ids, now);
        } else {
            deals.markLastDayAnnounced(ids, now);
        }
        int queued = 0;
        UUID after = null;
        List<Recipient> batch;
        do {
            batch = deals.recipients(today, today.atStartOfDay(TenantContext.require().timezone()).toInstant(), after,
                    BATCH);
            for (Recipient recipient : batch) {
                after = recipient.customerId();
                // Yalnız bir filialda olan endirim — həmin filiala sonuncu dəfə gələn müştəriyə
                List<DealItem> relevant = items.stream()
                        .filter(item -> item.branchId() == null || item.branchId().equals(recipient.lastBranchId()))
                        .toList();
                if (!relevant.isEmpty() && deals.queuePush(announcementId, recipient.customerId(), today,
                        recipient.lastBranchId(), relevant.size(), list(relevant), now) == 1) {
                    queued++;
                }
            }
        } while (batch.size() == BATCH);
        // Filialı indi açıq olanlara dərhal; qalanlarına filial açılanda (taymer)
        int sent = deliverWaiting();
        log.info("Deal announcement {} ({}, {} items): {} customers, {} sent now", kind, trigger, items.size(), queued,
                sent);
        return new DealSendResult(SendStatus.SENT, sent, Math.max(queued - sent, 0));
    }

    /**
     * Növbədəki bildirişlər: müştərinin filialı (yoxdursa və ya bağlanıbsa — istənilən aktiv filial) indi açıqdırsa
     * göndərilir; 24 saatdan çox gözləyənlər ləğv olunur. Göndərilənlərin sayı.
     */
    int deliverWaiting() {
        if (!push.config().enabled()) {
            return 0;
        }
        Instant now = clock.instant();
        deals.dropStale(now.minus(WAIT_LIMIT), now);
        ZonedDateTime local = now();
        Map<UUID, Boolean> open = new HashMap<>();
        for (BranchHours branch : deals.branchHours()) {
            open.put(branch.id(), BusinessHours.isOpen(branch.businessHours(), local));
        }
        // Filial qrafiki yoxdursa — açıq; heç bir aktiv filial yoxdursa da göndərilir
        boolean anyOpen = open.isEmpty() || open.containsValue(true);
        int sent = 0;
        WaitingPush after = null;
        List<WaitingPush> batch;
        do {
            batch = deals.waiting(after, BATCH);
            for (WaitingPush waiting : batch) {
                after = waiting;
                Boolean branchOpen = waiting.branchId() == null ? null : open.get(waiting.branchId());
                if (!(branchOpen == null ? anyOpen : branchOpen)
                        || deals.markSent(waiting.announcementId(), waiting.customerId(), now) != 1) {
                    continue;
                }
                sent++;
                PushService.Kind pushKind = "LAST_DAY".equals(waiting.kind())
                        ? PushService.Kind.DEALS_LAST_DAY : PushService.Kind.DEALS_NEW;
                UUID announcementId = waiting.announcementId();
                push.notifyTracked(waiting.customerId(), pushKind, "/menu?deal=" + announcementId, delivered -> {
                    if (delivered) {
                        deals.incrementDelivered(announcementId);
                    }
                }, waiting.itemCount(), waiting.itemsText());
            }
        } while (batch.size() == BATCH);
        return sent;
    }

    /** Siyahı hər dildə: "Kapuçino −20%, Çizkeyk −15%" və çoxdursa "… və daha 2 təklif". */
    private LocalizedText list(List<DealItem> items) {
        return new LocalizedText(list(items, Language.AZ), list(items, Language.EN), list(items, Language.RU));
    }

    private String list(List<DealItem> items, Language language) {
        List<String> names = new ArrayList<>();
        for (DealItem item : items.subList(0, Math.min(LISTED, items.size()))) {
            names.add(item.name().resolve(language) + " −" + percent(item) + "%");
        }
        String joined = String.join(", ", names);
        return items.size() > LISTED
                ? messages.getMessage("deals.more", new Object[] {joined, items.size() - LISTED}, language.locale())
                : joined;
    }

    private String text(String kind, String part, int count, List<DealItem> items, Language language) {
        String key = "push.deals-" + kind.toLowerCase(Locale.ROOT).replace('_', '-') + "." + part;
        return messages.getMessage(key, new Object[] {count, list(items, language)}, language.locale());
    }

    private static int percent(DealItem item) {
        return CatalogService.discountPercent(item.discountKind(), item.discountValue(), item.price());
    }

    private boolean available() {
        return push.config().enabled() && plans.has(Addon.CATALOG) && catalog.settings().enabled();
    }

    private ZonedDateTime now() {
        TenantInfo tenant = TenantContext.require();
        return clock.instant().atZone(tenant.timezone());
    }

    private static DealNotificationSettings settingsView(Settings settings) {
        return new DealNotificationSettings(settings.autoEnabled(), settings.sendTime().toString(), settings.lastDayEnabled());
    }
}
