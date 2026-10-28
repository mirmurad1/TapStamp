package az.tapstamp.bonus;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.bonus.repository.BonusRuleMapper;
import az.tapstamp.bonus.repository.BonusRuleMapper.BonusRule;
import az.tapstamp.bonus.repository.BonusRuleMapper.DeliveryCounts;
import az.tapstamp.bonus.repository.BonusRuleMapper.DeliveryRow;
import az.tapstamp.bonus.repository.BonusRuleMapper.RuleStats;
import az.tapstamp.company.service.CompanySettings;
import az.tapstamp.loyalty.stamp.StampLedger;
import az.tapstamp.loyalty.stamp.StampLedger.CardState;
import az.tapstamp.notification.push.PushService;
import az.tapstamp.plan.Feature;
import az.tapstamp.plan.PlanService;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.TenantInfo;
import az.tapstamp.tenancy.registry.TenantDirectory;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bonus qaydaları (qərar #59): xoşbəxt saatlar və tarixli kampaniyalar. Şərt (həftənin günü, saat aralığı, tarix
 * aralığı, filial) ödənəndə təsdiqlənmiş hər NFC möhürünə əlavə bonus möhür verilir ({@code stamps.source = BONUS}).
 * Bir neçə qayda uyğun gəlirsə, yalnız ən böyük bonuslu qayda tətbiq olunur (toplanmır). Şərt möhürün alındığı (NFC
 * toxunuşu) anına görə yoxlanılır — işçi sonradan təsdiqləsə də.
 */
@Service
public class BonusRuleService {

    /** Bildirişlər yalnız bu saatlarda göndərilir (xatırlatmalarla eyni). */
    static final LocalTime PUSH_WINDOW_START = LocalTime.of(10, 0);
    static final LocalTime PUSH_WINDOW_END = LocalTime.of(20, 0);
    private static final int PUSH_BATCH = 500;
    private static final Logger log = LoggerFactory.getLogger(BonusRuleService.class);

    /** Qaydanın yaradılması və dəyişdirilməsi üçün parametrlər. */
    public record RuleInput(LocalizedText name, int bonusStamps, List<Integer> days, @Nullable LocalTime startTime,
                            @Nullable LocalTime endTime, @Nullable LocalDate startsOn, @Nullable LocalDate endsOn,
                            @Nullable UUID branchId, boolean active, boolean notifyOnStart) {
    }

    /**
     * Admin üçün qayda: {@code days} — ISO günləri (1 = bazar ertəsi), {@code status} — ACTIVE (indi qüvvədə olmasa da
     * tarix aralığındadır), SCHEDULED (hələ başlamayıb), ENDED (bitib), OFF (söndürülüb).
     */
    public record RuleView(UUID id, LocalizedText name, int bonusStamps, List<Integer> days,
                           @Nullable LocalTime startTime, @Nullable LocalTime endTime, @Nullable LocalDate startsOn,
                           @Nullable LocalDate endsOn, @Nullable UUID branchId, boolean active, boolean notifyOnStart,
                           @Nullable Instant notifiedAt, String status, boolean runningNow, long bonusStamps30d,
                           long customers30d, long notifyTotal, long notifyDelivered, long notifyFailed) {
    }

    /** Müştəri üçün qayda (kartın üstündəki banner): {@code runningNow} — bu an möhür alsa bonus qazanır. */
    public record PublicRule(UUID id, LocalizedText name, int bonusStamps, List<Integer> days,
                             @Nullable LocalTime startTime, @Nullable LocalTime endTime, @Nullable LocalDate endsOn,
                             @Nullable UUID branchId, boolean runningNow) {
    }

    private final BonusRuleMapper rules;
    private final StampLedger ledger;
    private final CompanySettings company;
    private final PlanService plans;
    private final PushService push;
    private final TenantDirectory directory;
    private final AuditLogger audit;
    private final Clock clock;

    public BonusRuleService(BonusRuleMapper rules, StampLedger ledger, CompanySettings company, PlanService plans,
                            PushService push, TenantDirectory directory, AuditLogger audit, Clock clock) {
        this.rules = rules;
        this.ledger = ledger;
        this.company = company;
        this.plans = plans;
        this.push = push;
        this.directory = directory;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- möhür

    /**
     * NFC möhürü təsdiqlənəndə çağırılır (hesab artıq kilidlənib). Uyğun qayda varsa bonus möhürləri verir və kartın
     * yeni vəziyyətini qaytarır; yoxdursa {@code null}.
     */
    public @Nullable CardState onTapStampConfirmed(UUID customerId, UUID branchId, Instant tapAt, Instant now) {
        if (!plans.has(Feature.BONUS_RULES)) {
            return null;
        }
        ZonedDateTime at = tapAt.atZone(company.current().timezone());
        Optional<BonusRule> rule = rules.findActiveOn(at.toLocalDate()).stream()
                .filter(candidate -> appliesAt(candidate, branchId, at))
                .findFirst();
        if (rule.isEmpty()) {
            return null;
        }
        BonusRule applied = rule.get();
        CardState card = null;
        UUID giftCardId = null;
        for (int i = 0; i < applied.bonusStamps(); i++) {
            UUID stampId = ledger.insertBonusStamp(branchId, customerId, applied.id(), applied.name().az(),
                    at.toLocalDate(), now);
            card = ledger.applyConfirmed(customerId, stampId, now);
            if (giftCardId == null) {
                giftCardId = card.giftCardId();
            }
        }
        return new CardState(card.currentStamps(), card.stampsRequired(), giftCardId);
    }

    /** Tarix aralığı sorğuda yoxlanılıb; burada filial, həftənin günü və saat. */
    static boolean appliesAt(BonusRule rule, @Nullable UUID branchId, ZonedDateTime at) {
        if (rule.branchId() != null && !rule.branchId().equals(branchId)) {
            return false;
        }
        if ((rule.daysMask() & dayBit(at.getDayOfWeek())) == 0) {
            return false;
        }
        if (rule.startTime() == null || rule.endTime() == null) {
            return true;
        }
        LocalTime time = at.toLocalTime();
        return !time.isBefore(rule.startTime()) && time.isBefore(rule.endTime());
    }

    static int dayBit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }

    static int toMask(List<Integer> days) {
        int mask = 0;
        for (Integer day : days) {
            if (day == null || day < 1 || day > 7) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED);
            }
            mask |= 1 << (day - 1);
        }
        return mask;
    }

    static List<Integer> fromMask(int mask) {
        List<Integer> days = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            if ((mask & (1 << (day - 1))) != 0) {
                days.add(day);
            }
        }
        return days;
    }

    // ---------------------------------------------------------------- müştəri

    /** Bu gün tarix aralığında olan aktiv qaydalar (paket imkanı yoxdursa — boş). */
    public List<PublicRule> current() {
        if (!plans.has(Feature.BONUS_RULES)) {
            return List.of();
        }
        ZonedDateTime now = clock.instant().atZone(company.current().timezone());
        return rules.findActiveOn(now.toLocalDate()).stream()
                .map(rule -> new PublicRule(rule.id(), rule.name(), rule.bonusStamps(), fromMask(rule.daysMask()),
                        rule.startTime(), rule.endTime(), rule.endsOn(), rule.branchId(),
                        appliesAt(rule, rule.branchId(), now)))
                .toList();
    }

    // ---------------------------------------------------------------- admin (şirkət sahibi)

    public List<RuleView> list(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        Instant now = clock.instant();
        ZonedDateTime local = now.atZone(company.current().timezone());
        Map<UUID, RuleStats> stats = new HashMap<>();
        for (RuleStats row : rules.stats(now.minus(Duration.ofDays(30)))) {
            stats.put(row.ruleId(), row);
        }
        Map<UUID, DeliveryCounts> deliveries = new HashMap<>();
        for (DeliveryCounts row : rules.deliveryCounts()) {
            deliveries.put(row.ruleId(), row);
        }
        return rules.findAll().stream()
                .map(rule -> view(rule, local, stats.get(rule.id()), deliveries.get(rule.id())))
                .toList();
    }

    @Transactional
    public RuleView create(AdminPrincipal principal, RuleInput input) {
        AccessPolicy.requireCompanyWide(principal);
        int mask = validate(input);
        UUID id = Ids.newId();
        rules.insert(id, input.name(), input.bonusStamps(), mask, input.startTime(), input.endTime(), input.startsOn(),
                input.endsOn(), input.branchId(), input.active(), input.notifyOnStart(), principal.userId(),
                clock.instant());
        audit.tenant("BONUS_RULE_CREATED", AuditLogger.ActorType.STAFF, principal.userId(), "bonus_rule", id,
                Map.of("after", input));
        return find(id);
    }

    @Transactional
    public RuleView update(AdminPrincipal principal, UUID id, RuleInput input) {
        AccessPolicy.requireCompanyWide(principal);
        int mask = validate(input);
        BonusRule before = rules.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        rules.update(id, input.name(), input.bonusStamps(), mask, input.startTime(), input.endTime(), input.startsOn(),
                input.endsOn(), input.branchId(), input.active(), input.notifyOnStart(), principal.userId(),
                clock.instant());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("before", before);
        details.put("after", input);
        audit.tenant("BONUS_RULE_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "bonus_rule", id, details);
        return find(id);
    }

    /**
     * Qayda silinir; artıq bonus möhür veribsə (tarixçə ona istinad edir), silinmir — söndürülür. {@code true} —
     * silindi.
     */
    @Transactional
    public boolean delete(AdminPrincipal principal, UUID id) {
        AccessPolicy.requireCompanyWide(principal);
        BonusRule rule = rules.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        boolean deleted = rules.deleteUnused(id) == 1;
        if (!deleted) {
            rules.update(id, rule.name(), rule.bonusStamps(), rule.daysMask(), rule.startTime(), rule.endTime(),
                    rule.startsOn(), rule.endsOn(), rule.branchId(), false, rule.notifyOnStart(), principal.userId(),
                    clock.instant());
        }
        audit.tenant(deleted ? "BONUS_RULE_DELETED" : "BONUS_RULE_DISABLED", AuditLogger.ActorType.STAFF,
                principal.userId(), "bonus_rule", id, Map.of("name", rule.name().az()));
        return deleted;
    }

    public static final List<String> DELIVERY_STATUSES = List.of("PENDING", "DELIVERED", "FAILED");

    /** Başlanğıc bildirişini alan müştərilər nəticə ilə, səhifə-səhifə; {@code status} boşdursa — hamısı. */
    public PageResult<DeliveryRow> deliveries(AdminPrincipal principal, UUID id, @Nullable String status, PageQuery page) {
        AccessPolicy.requireCompanyWide(principal);
        if (status != null && !DELIVERY_STATUSES.contains(status)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        rules.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return page.result(rules.deliveries(id, status, page.limit(), page.offset()), rules.countDeliveries(id, status));
    }

    private RuleView find(UUID id) {
        BonusRule rule = rules.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return view(rule, clock.instant().atZone(company.current().timezone()), null, null);
    }

    private int validate(RuleInput input) {
        int mask = toMask(input.days());
        boolean timesPaired = (input.startTime() == null) == (input.endTime() == null);
        boolean timesOrdered = !timesPaired || input.startTime() == null
                || input.startTime().isBefore(input.endTime());
        boolean datesOrdered = input.startsOn() == null || input.endsOn() == null
                || !input.startsOn().isAfter(input.endsOn());
        if (mask == 0 || input.bonusStamps() < 1 || input.bonusStamps() > 5 || !timesPaired || !timesOrdered
                || !datesOrdered) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (input.branchId() != null && !rules.branchExists(input.branchId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return mask;
    }

    private static RuleView view(BonusRule rule, ZonedDateTime now, @Nullable RuleStats stats,
                                 @Nullable DeliveryCounts deliveries) {
        LocalDate today = now.toLocalDate();
        String status;
        if (!rule.active()) {
            status = "OFF";
        } else if (rule.endsOn() != null && rule.endsOn().isBefore(today)) {
            status = "ENDED";
        } else if (rule.startsOn() != null && rule.startsOn().isAfter(today)) {
            status = "SCHEDULED";
        } else {
            status = "ACTIVE";
        }
        boolean running = "ACTIVE".equals(status) && appliesAt(rule, rule.branchId(), now);
        return new RuleView(rule.id(), rule.name(), rule.bonusStamps(), fromMask(rule.daysMask()), rule.startTime(),
                rule.endTime(), rule.startsOn(), rule.endsOn(), rule.branchId(), rule.active(), rule.notifyOnStart(),
                rule.notifiedAt(), status, running, stats == null ? 0 : stats.bonusStamps(),
                stats == null ? 0 : stats.customers(), deliveries == null ? 0 : deliveries.total(),
                deliveries == null ? 0 : deliveries.delivered(), deliveries == null ? 0 : deliveries.failed());
    }

    // ---------------------------------------------------------------- başlanğıc bildirişi

    /** Bütün aktiv şirkətlər üçün bir addım (taymer çağırır). */
    public void notifyStarted() {
        for (TenantRecord tenant : directory.findAll()) {
            if (tenant.status().outOfService() || plans.expired(tenant.id())) {
                continue;
            }
            try {
                TenantContext.runAs(tenant.toInfo(), this::notifyStartedForCurrentTenant);
            } catch (RuntimeException e) {
                log.warn("Bonus rule notifications failed for tenant {}: {}", tenant.slug(),
                        e.getClass().getSimpleName());
            }
        }
    }

    /**
     * Cari şirkət: bu gün başlamış (tarix aralığında olan), bildirişi açıq və hələ elan olunmamış qaydalar haqqında
     * bildirişləri açıq müştərilərə bir dəfə push göndərilir. Göndərilən qaydaların sayı.
     */
    public int notifyStartedForCurrentTenant() {
        TenantInfo tenant = TenantContext.require();
        Instant now = clock.instant();
        ZonedDateTime local = now.atZone(tenant.timezone());
        LocalTime time = local.toLocalTime();
        if (time.isBefore(PUSH_WINDOW_START) || !time.isBefore(PUSH_WINDOW_END) || !plans.has(Feature.BONUS_RULES)
                || !push.config().enabled()) {
            return 0;
        }
        int announced = 0;
        for (BonusRule rule : rules.findActiveOn(local.toLocalDate())) {
            if (!rule.notifyOnStart() || rule.notifiedAt() != null || rules.markNotified(rule.id(), now) != 1) {
                continue;
            }
            announced++;
            for (long offset = 0; ; offset += PUSH_BATCH) {
                List<UUID> audience = rules.pushAudience(PUSH_BATCH, offset);
                for (UUID customerId : audience) {
                    // Kimə göndərildiyi və nəticə admin üçün saxlanılır
                    rules.insertDelivery(rule.id(), customerId, now);
                    push.notifyTracked(customerId, PushService.Kind.BONUS_RULE_STARTED,
                            delivered -> rules.markDelivery(rule.id(), customerId, delivered ? "DELIVERED" : "FAILED",
                                    clock.instant()),
                            rule.name(), rule.bonusStamps());
                }
                if (audience.size() < PUSH_BATCH) {
                    break;
                }
            }
            audit.tenant("BONUS_RULE_ANNOUNCED", AuditLogger.ActorType.SYSTEM, null, "bonus_rule", rule.id(),
                    Map.of("name", rule.name().az()));
        }
        return announced;
    }
}
