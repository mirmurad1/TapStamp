package az.tapstamp.plan;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import az.tapstamp.plan.repository.SubscriptionMapper;
import az.tapstamp.plan.repository.SubscriptionMapper.SubscriptionRow;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.security.admin.AdminRealms;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.registry.TenantDirectory;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Şirkətin paketi: imkanların və limitlərin yoxlanması, sınaq müddəti, platforma panelindən dəyişmə.
 * Paket hər sorğuda lazım olduğu üçün qısa müddət keşlənir; dəyişəndə keş təmizlənir.
 * Sınaq müddəti bitibsə, heç bir imkan açıq deyil (şirkətin xidməti dayandırılır).
 */
@Service
public class PlanService {

    /** Platforma paneli üçün: paket, sınağın bitib-bitmədiyi və istifadə olunmuş sınaqlar. */
    public record PlanView(Subscription subscription, boolean expired, List<SubscriptionMapper.TrialRow> trials) {
    }

    private final SubscriptionMapper subscriptions;
    private final TenantDirectory directory;
    private final AdminRealms realms;
    private final Clock clock;
    private final Cache<UUID, Subscription> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    public PlanService(SubscriptionMapper subscriptions, TenantDirectory directory, AdminRealms realms, Clock clock) {
        this.subscriptions = subscriptions;
        this.directory = directory;
        this.realms = realms;
        this.clock = clock;
    }

    public Subscription subscription(UUID tenantId) {
        return cache.get(tenantId, id -> subscriptions.find(id).map(PlanService::toSubscription)
                .orElse(Subscription.LEGACY));
    }

    /** Cari şirkətin (sorğunun tenant-ı) paketi. */
    public Subscription current() {
        return subscription(TenantContext.require().id());
    }

    /** Sınaq müddəti bitib və ödəniş qeyd olunmayıb — şirkətin xidməti dayandırılır. */
    public boolean expired(UUID tenantId) {
        return subscription(tenantId).expired(clock.instant());
    }

    /** Cari şirkətin paketində bu imkan varmı (sınaq bitibsə — yoxdur). */
    public boolean has(Feature feature) {
        Subscription subscription = current();
        return !subscription.expired(clock.instant()) && subscription.plan().has(feature);
    }

    public void require(Feature feature) {
        if (!has(feature)) {
            throw new ApiException(ErrorCode.PLAN_FEATURE_UNAVAILABLE);
        }
    }

    /** Yeni (və ya yenidən açılan) filial: aktiv filialların sayı paketin limitindən az olmalıdır. */
    public void requireBranchCapacity(long activeBranches) {
        if (activeBranches >= current().branchLimit()) {
            throw new ApiException(ErrorCode.PLAN_LIMIT_REACHED);
        }
    }

    /** Yeni işçi dəvəti: sahiblərdən başqa işçilər və gözləyən dəvətlər birlikdə sayılır. */
    public void requireStaffCapacity(long staff) {
        Integer limit = current().plan().staffLimit();
        if (limit != null && staff >= limit) {
            throw new ApiException(ErrorCode.PLAN_LIMIT_REACHED);
        }
    }

    /** Paket bazada birbaşa dəyişəndə (testlər). */
    public void evictCaches() {
        cache.invalidateAll();
    }

    // --- Platforma paneli ---

    public Map<UUID, Subscription> all() {
        return subscriptions.findAll().stream()
                .collect(Collectors.toMap(SubscriptionRow::tenantId, PlanService::toSubscription));
    }

    public PlanView view(UUID tenantId) {
        requireTenant(tenantId);
        Subscription subscription = subscription(tenantId);
        return new PlanView(subscription, subscription.expired(clock.instant()), subscriptions.trials(tenantId));
    }

    /** Yeni şirkət seçilmiş paketin 14 günlük sınağı ilə başlayır. */
    public void startTrial(UUID tenantId, Plan plan, @Nullable UUID by) {
        Instant now = clock.instant();
        Instant ends = now.plus(Duration.ofDays(Plan.TRIAL_DAYS));
        if (subscriptions.insertTrial(tenantId, plan.name(), now, ends, by) == 0) {
            throw new ApiException(ErrorCode.PLAN_TRIAL_ALREADY_USED);
        }
        subscriptions.upsert(tenantId, plan.name(), Subscription.Status.TRIAL.name(), ends, 0, by, now);
        cache.invalidate(tenantId);
    }

    /**
     * Paketin dəyişməsi. {@code TRIAL} — həmin paketin sınağı (hər paket üçün şirkətə bir dəfə; eyni paketin davam
     * edən sınağında yalnız əlavə filiallar dəyişir), {@code ACTIVE} — ödəniş qeyd olunub. Əlavə filial yalnız
     * Premium-da. Platforma auditinə yazılır.
     */
    public PlanView change(AdminPrincipal principal, UUID tenantId, Plan plan, Subscription.Status status,
                           int extraBranches) {
        requireTenant(tenantId);
        if (extraBranches < 0 || extraBranches > 100 || (plan != Plan.PREMIUM && extraBranches != 0)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        Instant now = clock.instant();
        Subscription before = subscription(tenantId);
        if (status == Subscription.Status.ACTIVE) {
            subscriptions.upsert(tenantId, plan.name(), status.name(), null, extraBranches, principal.userId(), now);
        } else if (before.status() == Subscription.Status.TRIAL && before.plan() == plan && !before.expired(now)) {
            subscriptions.upsert(tenantId, plan.name(), status.name(), before.trialEndsAt(), extraBranches,
                    principal.userId(), now);
        } else {
            Instant ends = now.plus(Duration.ofDays(Plan.TRIAL_DAYS));
            if (subscriptions.insertTrial(tenantId, plan.name(), now, ends, principal.userId()) == 0) {
                throw new ApiException(ErrorCode.PLAN_TRIAL_ALREADY_USED);
            }
            subscriptions.upsert(tenantId, plan.name(), status.name(), ends, extraBranches, principal.userId(), now);
        }
        cache.invalidate(tenantId);
        Subscription after = subscription(tenantId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("before", describe(before));
        details.put("after", describe(after));
        realms.audit(AdminRealm.PLATFORM, "TENANT_PLAN_CHANGED", principal.userId(), "tenant", tenantId, details);
        return view(tenantId);
    }

    private void requireTenant(UUID tenantId) {
        directory.findById(tenantId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private static String describe(Subscription subscription) {
        return subscription.plan() + "/" + subscription.status()
                + (subscription.extraBranches() > 0 ? "+" + subscription.extraBranches() : "");
    }

    private static Subscription toSubscription(SubscriptionRow row) {
        return new Subscription(Plan.valueOf(row.plan()), Subscription.Status.valueOf(row.status()), row.trialEndsAt(),
                row.extraBranches());
    }
}
