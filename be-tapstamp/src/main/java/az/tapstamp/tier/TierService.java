package az.tapstamp.tier;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.company.service.CompanySettings;
import az.tapstamp.customer.auth.CustomerPrincipal;
import az.tapstamp.loyalty.stamp.StampLedger;
import az.tapstamp.loyalty.stamp.StampLedger.CardState;
import az.tapstamp.plan.Feature;
import az.tapstamp.plan.PlanService;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.tier.repository.TierMapper;
import az.tapstamp.tier.repository.TierMapper.Tier;
import az.tapstamp.tier.repository.TierMapper.TierCount;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Müştəri səviyyələri (qərar #60): ziyarət sayına (təsdiqlənmiş NFC möhürləri) görə avtomatik. Səviyyə saxlanılmır —
 * hər dəfə hesablanır. İmtiyaz — hər NFC möhürünə əlavə bonus möhür ({@code stamps.source = TIER}); bonus qaydaları
 * ilə toplanır (bunlar fərqli səbəblərdir: biri vaxta, digəri sadiqliyə görə).
 */
@Service
public class TierService {

    public static final int MAX_TIERS = 5;
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    public record TierInput(LocalizedText name, String color, int minVisits, int bonusStamps) {
    }

    /** Admin üçün: səviyyə və ondakı aktiv müştərilərin sayı. */
    public record TierView(UUID id, LocalizedText name, String color, int minVisits, int bonusStamps, long customers) {
    }

    /** {@code enabled} — proqramın açarı; söndürülübsə müştəriyə göstərilmir və bonus verilmir. */
    public record TierOverview(boolean enabled, List<TierView> tiers, long belowAll) {
    }

    /** Müştərinin səviyyəsi: cari (yoxdursa null), növbəti və ona qalan ziyarətlər; {@code tiers} — bütün səviyyələr həddə görə. */
    public record CustomerTier(boolean enabled, int visits, @Nullable Tier current, @Nullable Tier next,
                               int visitsToNext, List<Tier> tiers) {
    }

    private final TierMapper tiers;
    private final StampLedger ledger;
    private final CompanySettings company;
    private final PlanService plans;
    private final AuditLogger audit;
    private final Clock clock;

    public TierService(TierMapper tiers, StampLedger ledger, CompanySettings company, PlanService plans,
                       AuditLogger audit, Clock clock) {
        this.tiers = tiers;
        this.ledger = ledger;
        this.company = company;
        this.plans = plans;
        this.audit = audit;
        this.clock = clock;
    }

    /** Ziyarət sayına uyğun ən yüksək səviyyə ({@code all} həddə görə artan sıradadır). */
    static @Nullable Tier tierFor(List<Tier> all, int visits) {
        Tier result = null;
        for (Tier tier : all) {
            if (tier.minVisits() <= visits) {
                result = tier;
            }
        }
        return result;
    }

    static @Nullable Tier nextAfter(List<Tier> all, int visits) {
        for (Tier tier : all) {
            if (tier.minVisits() > visits) {
                return tier;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- möhür

    /**
     * NFC möhürü təsdiqlənəndə (hesab kilidlənib, möhür artıq sayılır): müştərinin səviyyəsinin bonusu varsa verir və
     * kartın yeni vəziyyətini qaytarır; yoxdursa {@code null}.
     */
    public @Nullable CardState onTapStampConfirmed(UUID customerId, UUID branchId, Instant tapAt, Instant now) {
        if (!active()) {
            return null;
        }
        Tier tier = tierFor(tiers.findAll(), tiers.visits(customerId));
        if (tier == null || tier.bonusStamps() == 0) {
            return null;
        }
        LocalDate day = tapAt.atZone(company.current().timezone()).toLocalDate();
        CardState card = null;
        UUID giftCardId = null;
        for (int i = 0; i < tier.bonusStamps(); i++) {
            UUID stampId = ledger.insertTierStamp(branchId, customerId, tier.id(), tier.name().az(), day, now);
            card = ledger.applyConfirmed(customerId, stampId, now);
            if (giftCardId == null) {
                giftCardId = card.giftCardId();
            }
        }
        return new CardState(card.currentStamps(), card.stampsRequired(), giftCardId);
    }

    /** Paketdə imkan var və proqram açıqdır. */
    private boolean active() {
        return plans.has(Feature.TIERS) && tiers.enabled();
    }

    // ---------------------------------------------------------------- müştəri

    public CustomerTier forCustomer(CustomerPrincipal principal) {
        List<Tier> all = active() ? tiers.findAll() : List.of();
        if (all.isEmpty()) {
            return new CustomerTier(false, 0, null, null, 0, List.of());
        }
        int visits = tiers.visits(principal.customerId());
        Tier next = nextAfter(all, visits);
        return new CustomerTier(true, visits, tierFor(all, visits), next,
                next == null ? 0 : next.minVisits() - visits, all);
    }

    // ---------------------------------------------------------------- admin (şirkət sahibi)

    public TierOverview overview(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        Map<UUID, Long> counts = new HashMap<>();
        for (TierCount row : tiers.distribution()) {
            counts.put(row.tierId(), row.customers());
        }
        List<TierView> views = tiers.findAll().stream()
                .map(tier -> new TierView(tier.id(), tier.name(), tier.color(), tier.minVisits(), tier.bonusStamps(),
                        counts.getOrDefault(tier.id(), 0L)))
                .toList();
        return new TierOverview(tiers.enabled(), views, tiers.belowAll());
    }

    @Transactional
    public TierOverview setEnabled(AdminPrincipal principal, boolean enabled) {
        AccessPolicy.requireCompanyWide(principal);
        boolean before = tiers.enabled();
        tiers.updateEnabled(enabled, principal.userId(), clock.instant());
        audit.tenant("TIER_SETTINGS_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "tier_settings",
                "company", Map.of("before", before, "after", enabled));
        return overview(principal);
    }

    @Transactional
    public TierOverview create(AdminPrincipal principal, TierInput input) {
        AccessPolicy.requireCompanyWide(principal);
        if (tiers.findAll().size() >= MAX_TIERS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        validate(input, null);
        UUID id = Ids.newId();
        tiers.insert(id, input.name(), input.color(), input.minVisits(), input.bonusStamps(), principal.userId(),
                clock.instant());
        audit.tenant("TIER_CREATED", AuditLogger.ActorType.STAFF, principal.userId(), "tier", id,
                Map.of("after", input));
        return overview(principal);
    }

    @Transactional
    public TierOverview update(AdminPrincipal principal, UUID id, TierInput input) {
        AccessPolicy.requireCompanyWide(principal);
        Tier before = tiers.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        validate(input, id);
        tiers.update(id, input.name(), input.color(), input.minVisits(), input.bonusStamps(), principal.userId(),
                clock.instant());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("before", before);
        details.put("after", input);
        audit.tenant("TIER_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "tier", id, details);
        return overview(principal);
    }

    /** Səviyyə silinir; artıq bonus veribsə (tarixçə ona istinad edir) — 409. */
    @Transactional
    public TierOverview delete(AdminPrincipal principal, UUID id) {
        AccessPolicy.requireCompanyWide(principal);
        Tier tier = tiers.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (tiers.deleteUnused(id) != 1) {
            throw new ApiException(ErrorCode.TIER_IN_USE);
        }
        audit.tenant("TIER_DELETED", AuditLogger.ActorType.STAFF, principal.userId(), "tier", id,
                Map.of("name", tier.name().az()));
        return overview(principal);
    }

    private void validate(TierInput input, @Nullable UUID exceptId) {
        if (input.color() == null || !COLOR.matcher(input.color()).matches() || input.minVisits() < 0
                || input.minVisits() > 10_000 || input.bonusStamps() < 0 || input.bonusStamps() > 3) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (tiers.minVisitsTaken(input.minVisits(), exceptId)) {
            throw new ApiException(ErrorCode.TIER_THRESHOLD_TAKEN);
        }
    }
}
