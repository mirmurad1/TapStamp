package az.tapstamp.feedback;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.feedback.repository.FeedbackMapper;
import az.tapstamp.feedback.repository.FeedbackMapper.FeedbackSettings;
import az.tapstamp.feedback.repository.FeedbackMapper.SummaryRow;
import az.tapstamp.notification.push.PushService;
import az.tapstamp.plan.Feature;
import az.tapstamp.plan.PlanService;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.crypto.Aad;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.registry.TenantDirectory;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Ziyarətdən sonra rəy (qərar #61). Təsdiqlənmiş NFC möhüründən sorğu yaranır (sahibin seçdiyi tezliklə — müştəriyə
 * ən çox N gündə bir), 15 dəqiqə sonra push ilə göndərilir; saat məhdudiyyəti yoxdur (ziyarət filialın iş saatında
 * olur, sorğu ondan 15 dəqiqə sonra gedir). Müştəri 3 gün ərzində tətbiqdə cavab verə bilər. Pis rəylər admin
 * panelində "həll olunmamış" kimi görünür; email göndərilmir.
 */
@Service
public class FeedbackService {

    static final Duration DELAY = Duration.ofMinutes(15);
    static final Duration ANSWER_WINDOW = Duration.ofDays(3);
    public static final List<Integer> FREQUENCIES = List.of(1, 7, 14, 30);
    /** Xülasənin ən uzun dövrü — göstəricilər panelindəki həddlə eyni. */
    static final int MAX_PERIOD_DAYS = 366;
    public static final Set<String> RATINGS = Set.of("GOOD", "OK", "BAD");
    public static final Set<String> REASONS = Set.of("SERVICE", "WAIT", "QUALITY", "CLEANLINESS", "PRICE", "OTHER");
    private static final int BATCH = 500;
    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    /** {@code reviewUrl} — filialın Google rəy linki: rəydən sonra müştəriyə "Google-da da yaz" təklif olunur. */
    public record FeedbackPrompt(UUID id, UUID branchId, LocalizedText branchName, Instant visitAt,
                                 @Nullable String reviewUrl) {
    }

    /** Admin üçün rəy: müştərinin adı və tam telefonu. */
    public record FeedbackView(UUID id, UUID customerId, @Nullable String customerName, String phone, UUID branchId,
                               LocalizedText branchName, String rating, List<String> reasons,
                               @Nullable String comment, Instant createdAt, @Nullable Instant resolvedAt,
                               @Nullable String resolutionNote) {
    }

    /**
     * Son 30 gün: qiymətlər, məmnuniyyət (GOOD payı), cavab payı, Google rəy səhifəsinə keçənlər; həll olunmamış pis
     * rəylər — hamısı.
     */
    public record FeedbackSummary(long good, long ok, long bad, long answered, long sent, @Nullable Double satisfaction,
                              @Nullable Double responseRate, long unresolvedBad, long reviewClicks) {
    }

    private final FeedbackMapper feedback;
    private final PlanService plans;
    private final PushService push;
    private final TenantDirectory directory;
    private final EnvelopeCipher cipher;
    private final AuditLogger audit;
    private final Clock clock;

    public FeedbackService(FeedbackMapper feedback, PlanService plans, PushService push, TenantDirectory directory,
                           @Qualifier("tenantCipher") EnvelopeCipher cipher, AuditLogger audit, Clock clock) {
        this.feedback = feedback;
        this.plans = plans;
        this.push = push;
        this.directory = directory;
        this.cipher = cipher;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- ziyarət

    /** Təsdiqlənmiş NFC möhürü: proqram açıqdırsa və tezlik imkan verirsə, 15 dəqiqə sonrakı sorğunu planlaşdırır. */
    public void onVisit(UUID customerId, UUID branchId, Instant visitAt) {
        if (!plans.has(Feature.FEEDBACK)) {
            return;
        }
        FeedbackSettings settings = feedback.settings();
        if (!settings.enabled()
                || feedback.requestedSince(customerId, visitAt.minus(Duration.ofDays(settings.frequencyDays())))) {
            return;
        }
        Instant due = visitAt.plus(DELAY);
        feedback.insertRequest(Ids.newId(), customerId, branchId, visitAt, due, due.plus(ANSWER_WINDOW),
                clock.instant());
    }

    /** Bütün aktiv şirkətlər üçün: vaxtı çatmış sorğuları göndərir (taymer). */
    public void sendDue() {
        for (TenantRecord tenant : directory.findAll()) {
            if (tenant.status().outOfService() || plans.expired(tenant.id())) {
                continue;
            }
            try {
                TenantContext.runAs(tenant.toInfo(), this::sendDueForCurrentTenant);
            } catch (RuntimeException e) {
                log.warn("Feedback requests failed for tenant {}: {}", tenant.slug(), e.getClass().getSimpleName());
            }
        }
    }

    /** Cari şirkət: vaxtı çatmış sorğular göndərilmiş sayılır (tətbiqdə görünür), bildirişi açıq olanlara push. */
    public int sendDueForCurrentTenant() {
        if (!plans.has(Feature.FEEDBACK)) {
            return 0;
        }
        List<UUID> customers = feedback.markDueSent(clock.instant(), BATCH);
        for (UUID customerId : customers) {
            push.notify(customerId, PushService.Kind.FEEDBACK_REQUEST);
        }
        return customers.size();
    }

    // ---------------------------------------------------------------- müştəri

    public @Nullable FeedbackPrompt openRequest(UUID customerId) {
        if (!plans.has(Feature.FEEDBACK)) {
            return null;
        }
        return feedback.openRequest(customerId, clock.instant())
                .map(r -> new FeedbackPrompt(r.id(), r.branchId(), r.branchName(), r.visitAt(), r.reviewUrl()))
                .orElse(null);
    }

    public void answer(UUID customerId, UUID requestId, String rating, List<String> reasons,
                       @Nullable String comment) {
        if (!RATINGS.contains(rating) || !REASONS.containsAll(reasons) || reasons.size() > REASONS.size()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        String reasonText = "GOOD".equals(rating) || reasons.isEmpty() ? null : String.join(",", reasons);
        String text = comment == null || comment.isBlank() ? null : comment.strip();
        if (feedback.answer(Ids.newId(), requestId, customerId, rating, reasonText, text, clock.instant()) != 1) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    /**
     * Müştəri rəydən sonra Google rəy səhifəsini açdı — yalnız sayılır (sahib nəticəni görsün). Rəy tapılmırsa da
     * səssizdir: keçid onsuz da açılıb, müştəriyə xəta göstərməyə səbəb yoxdur.
     */
    public void reviewClicked(UUID customerId, UUID requestId) {
        feedback.markReviewClicked(requestId, customerId, clock.instant());
    }

    // ---------------------------------------------------------------- admin (şirkət sahibi)

    public FeedbackSettings settings(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        return feedback.settings();
    }

    public FeedbackSettings updateSettings(AdminPrincipal principal, FeedbackSettings next) {
        AccessPolicy.requireCompanyWide(principal);
        if (!FREQUENCIES.contains(next.frequencyDays())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        FeedbackSettings before = feedback.settings();
        feedback.updateSettings(next.enabled(), next.frequencyDays(), principal.userId(), clock.instant());
        audit.tenant("FEEDBACK_SETTINGS_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "feedback_settings",
                "company", Map.of("before", before, "after", next));
        return feedback.settings();
    }

    public PageResult<FeedbackView> list(AdminPrincipal principal, @Nullable String rating, boolean unresolvedOnly,
                                         @Nullable UUID branchId, PageQuery page) {
        AccessPolicy.requireCompanyWide(principal);
        if (rating != null && !RATINGS.contains(rating)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        UUID tenantId = TenantContext.require().id();
        return page.result(feedback.list(rating, unresolvedOnly, branchId, page.limit(), page.offset()),
                        feedback.count(rating, unresolvedOnly, branchId))
                .map(row -> new FeedbackView(row.id(), row.customerId(), row.customerName(),
                        cipher.decryptString(row.phoneEnc(),
                                Aad.tenantField(tenantId, "customers", "phone_enc", row.customerId())),
                        row.branchId(), row.branchName(), row.rating(),
                        row.reasons() == null ? List.of() : List.of(row.reasons().split(",")), row.comment(),
                        row.createdAt(), row.resolvedAt(), row.resolutionNote()));
    }

    /**
     * Dövr verilməyibsə — son 30 gün (indiyədək). Verilibsə — şirkətin saat qurşağında {@code from} gününün
     * başlanğıcından {@code to} gününün sonunadək (göstəricilər panelinin dövrü ilə eyni, ən çox 366 gün).
     */
    public FeedbackSummary summary(AdminPrincipal principal, @Nullable UUID branchId, @Nullable LocalDate from,
                                   @Nullable LocalDate to) {
        AccessPolicy.requireCompanyWide(principal);
        Instant now = clock.instant();
        Instant since = now.minus(Duration.ofDays(30));
        Instant until = now;
        if (from != null || to != null) {
            ZoneId zone = TenantContext.require().timezone();
            LocalDate end = to == null ? LocalDate.now(clock.withZone(zone)) : to;
            LocalDate start = from == null ? end.minusDays(29) : from;
            if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= MAX_PERIOD_DAYS) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED);
            }
            since = start.atStartOfDay(zone).toInstant();
            until = end.plusDays(1).atStartOfDay(zone).toInstant();
        }
        SummaryRow s = feedback.summary(since, until, branchId);
        long answered = s.good() + s.ok() + s.bad();
        return new FeedbackSummary(s.good(), s.ok(), s.bad(), answered, s.sent(),
                answered == 0 ? null : (double) s.good() / answered,
                s.sent() == 0 ? null : Math.min(1.0, (double) answered / s.sent()), s.unresolvedBad(), s.reviewClicks());
    }

    public void resolve(AdminPrincipal principal, UUID id, @Nullable String note) {
        AccessPolicy.requireCompanyWide(principal);
        String text = note == null || note.isBlank() ? null : note.strip();
        if (feedback.resolve(id, text, principal.userId(), clock.instant()) != 1) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        audit.tenant("FEEDBACK_RESOLVED", AuditLogger.ActorType.STAFF, principal.userId(), "feedback", id, Map.of());
    }
}
