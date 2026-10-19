package az.tapstamp.campaign;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.campaign.repository.CampaignMapper;
import az.tapstamp.campaign.repository.CampaignMapper.Campaign;
import az.tapstamp.company.service.CompanySettings;
import az.tapstamp.notification.sms.SmsSegments;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.id.Ids;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Kampaniya SMS-ləri (cari şirkət daxilində). Axın: sahib yaradır (alıcılar və SMS sayı həmin an müəyyənləşir) →
 * PENDING_APPROVAL → platforma admini ödənişdən sonra təsdiqləyir (APPROVED) və ya rədd edir → {@link CampaignSender}
 * göndərir (SENDING → SENT). Sahib təsdiqə qədər ləğv edə bilər.
 */
@Service
public class CampaignService {

    public enum Audience { ALL, INACTIVE, NEAR_REWARD, UNUSED_GIFT_CARD }

    /**
     * Kampaniyanın parametrləri. {@code inactiveDays} yalnız INACTIVE üçün (7–365); {@code personalized} — mətn
     * "Hörmətli &lt;ad&gt;," ilə başlayır (adı yoxdursa "Hörmətli istifadəçi,").
     */
    public record Draft(String text, Audience audience, @Nullable Integer inactiveDays, @Nullable UUID branchId,
                        boolean personalized, boolean latin) {
    }

    /**
     * Önizləmə ({@code latin} — mesaj latın hərfləri ilə gedəcək, nümunələr və saylar da buna görə): nümunə mətn (fərdi müraciətdə auditoriyadan adı olan müştəri ilə; {@code sampleWithoutName} — adı
     * olmayan üçün), hər müştəriyə ən az/ən çox SMS və cəmi (hər müştərinin adı ilə hesablanır); həmçinin latın
     * hərfləri ilə variant (Azərbaycan hərfləri bir SMS-i 160 yox, 70 simvol edir).
     */
    public record Preview(long recipients, boolean personalized, boolean latin, String fullText, @Nullable String sampleWithoutName,
                          String encoding, int characters, int segments, int minSegments, long totalSms,
                          String latinText, int latinSegments, long latinTotalSms, boolean latinCheaper) {
    }

    private static final int LIST_LIMIT = 100;

    private final CampaignMapper campaigns;
    private final CompanySettings company;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CampaignService(CampaignMapper campaigns, CompanySettings company, AuditLogger audit,
                           PlatformTransactionManager transactionManager, Clock clock) {
        this.campaigns = campaigns;
        this.company = company;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Preview preview(AdminPrincipal principal, Draft draft) {
        AccessPolicy.requireCompanyWide(principal);
        return preview(draft);
    }

    public Campaign create(AdminPrincipal principal, Draft draft) {
        AccessPolicy.requireCompanyWide(principal);
        Preview preview = preview(draft);
        UUID id = Ids.newId();
        Instant now = clock.instant();
        Campaign created = Objects.requireNonNull(tx.execute(status -> {
            CampaignMapper.Audience audience = audience(draft, now);
            Campaign row = new Campaign(id, draft.text().strip(), draft.personalized(), draft.latin(),
                    draft.audience().name(),
                    draft.inactiveDays(), draft.branchId(), 0, preview.segments(), 0, "PENDING_APPROVAL", null,
                    principal.userId(), now, null, null, 0, 0, null);
            campaigns.insert(row);
            int recipients = campaigns.insertRecipients(id, audience);
            if (recipients == 0) {
                throw new ApiException(ErrorCode.CAMPAIGN_NO_RECIPIENTS);
            }
            // Dəqiq SMS sayı: alıcıların özləri (fərdi müraciətdə adları) ilə
            CampaignTexts.Totals totals = CampaignTexts.totals(companyName(), draft.text(), draft.personalized(),
                    campaigns.recipientNames(id), draft.latin());
            campaigns.updateCounts(id, recipients, totals.maxSegments(), Math.toIntExact(totals.total()));
            return campaigns.find(id).orElseThrow();
        }));
        audit.tenant("CAMPAIGN_CREATED", AuditLogger.ActorType.STAFF, principal.userId(), "campaign", id,
                Map.of("audience", draft.audience().name(), "recipients", created.recipients(),
                        "totalSms", created.totalSms()));
        return created;
    }

    public List<Campaign> list(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        return campaigns.list(LIST_LIMIT);
    }

    public Campaign get(AdminPrincipal principal, UUID id) {
        AccessPolicy.requireCompanyWide(principal);
        return campaigns.find(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /**
     * Təsdiq gözləyən kampaniyanı dəyişmək: mətn, seçimlər və auditoriya; alıcılar və SMS sayı yenidən hesablanır.
     * Təsdiqlənib, rədd və ya ləğv edilibsə — {@code CAMPAIGN_STATE_CHANGED}.
     */
    public Campaign update(AdminPrincipal principal, UUID id, Draft draft) {
        AccessPolicy.requireCompanyWide(principal);
        campaigns.find(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        validate(draft);
        Instant now = clock.instant();
        Campaign updated = Objects.requireNonNull(tx.execute(status -> {
            if (campaigns.updateDraft(id, draft.text().strip(), draft.personalized(), draft.latin(),
                    draft.audience().name(), draft.inactiveDays(), draft.branchId(), now) == 0) {
                throw new ApiException(ErrorCode.CAMPAIGN_STATE_CHANGED);
            }
            campaigns.deleteRecipients(id);
            int recipients = campaigns.insertRecipients(id, audience(draft, now));
            if (recipients == 0) {
                throw new ApiException(ErrorCode.CAMPAIGN_NO_RECIPIENTS);
            }
            CampaignTexts.Totals totals = CampaignTexts.totals(companyName(), draft.text(), draft.personalized(),
                    campaigns.recipientNames(id), draft.latin());
            campaigns.updateCounts(id, recipients, totals.maxSegments(), Math.toIntExact(totals.total()));
            return campaigns.find(id).orElseThrow();
        }));
        audit.tenant("CAMPAIGN_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "campaign", id,
                Map.of("audience", draft.audience().name(), "recipients", updated.recipients(),
                        "totalSms", updated.totalSms()));
        return updated;
    }

    /** Sahib təsdiqə qədər ləğv edə bilər (tarixçədə "ləğv edilib" kimi qalır). */
    public Campaign cancel(AdminPrincipal principal, UUID id) {
        AccessPolicy.requireCompanyWide(principal);
        change(id, "PENDING_APPROVAL", "CANCELLED", null, null);
        audit.tenant("CAMPAIGN_CANCELLED", AuditLogger.ActorType.STAFF, principal.userId(), "campaign", id, Map.of());
        return campaigns.find(id).orElseThrow();
    }

    // ---------------------------------------------------------------- platforma (çağıran tenant kontekstini qurur)

    public List<Campaign> byStatus(List<String> statuses) {
        return campaigns.listByStatus(statuses, LIST_LIMIT);
    }

    public Campaign find(UUID id) {
        return campaigns.find(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** Platforma admininin qərarı: təsdiq (göndəriş başlayır) və ya rədd (səbəb ilə). */
    public Campaign decide(UUID platformAdminId, UUID id, boolean approve, @Nullable String reason) {
        change(id, "PENDING_APPROVAL", approve ? "APPROVED" : "REJECTED", platformAdminId,
                approve ? null : Objects.requireNonNullElse(reason, "").strip());
        audit.tenant(approve ? "CAMPAIGN_APPROVED" : "CAMPAIGN_REJECTED", AuditLogger.ActorType.PLATFORM,
                platformAdminId, "campaign", id, approve ? Map.of() : Map.of("reason", Objects.requireNonNullElse(reason, "")));
        return campaigns.find(id).orElseThrow();
    }


    // ---------------------------------------------------------------- köməkçilər

    private void change(UUID id, String from, String to, @Nullable UUID decidedBy, @Nullable String reason) {
        campaigns.find(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Instant decidedAt = decidedBy == null ? null : clock.instant();
        if (campaigns.changeStatus(id, from, to, decidedAt, decidedBy, reason) == 0) {
            throw new ApiException(ErrorCode.CAMPAIGN_STATE_CHANGED);
        }
    }

    private Preview preview(Draft draft) {
        validate(draft);
        String company = companyName();
        CampaignMapper.Audience audience = audience(draft, clock.instant());
        List<@Nullable String> names = draft.personalized() ? campaigns.audienceNames(audience) : null;
        long recipients = names != null ? names.size() : campaigns.countAudience(audience);
        // Nümunə: auditoriyadan adı olan ilk müştəri; adsız variant ayrıca göstərilir
        String sampleName = names == null ? null
                : names.stream().filter(n -> n != null && !n.isBlank()).findFirst().orElse(null);
        String full = CampaignTexts.message(company, draft.text(), draft.personalized(), sampleName, draft.latin());
        String withoutName = draft.personalized() && sampleName != null
                ? CampaignTexts.message(company, draft.text(), true, null, draft.latin()) : null;
        String latin = CampaignTexts.latin(full);
        SmsSegments.Info info = CampaignTexts.analyze(full);
        List<@Nullable String> forTotals = names != null ? names : java.util.Collections.nCopies((int) recipients, null);
        CampaignTexts.Totals totals = CampaignTexts.totals(company, draft.text(), draft.personalized(), forTotals,
                draft.latin());
        CampaignTexts.Totals latinTotals = CampaignTexts.totals(company, draft.text(), draft.personalized(), forTotals,
                true);
        return new Preview(recipients, draft.personalized(), draft.latin(), full, withoutName, info.encoding().name(),
                info.units(), totals.maxSegments(), totals.minSegments(), totals.total(), latin,
                latinTotals.maxSegments(), latinTotals.total(), latinTotals.total() < totals.total());
    }

    private void validate(Draft draft) {
        if (draft.text() == null || draft.text().isBlank() || draft.text().length() > 1000
                || (draft.audience() == Audience.INACTIVE
                    && (draft.inactiveDays() == null || draft.inactiveDays() < 7 || draft.inactiveDays() > 365))
                || (draft.branchId() != null && !campaigns.branchExists(draft.branchId()))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private static CampaignMapper.Audience audience(Draft draft, Instant now) {
        Instant inactiveSince = draft.audience() == Audience.INACTIVE
                ? now.minus(Duration.ofDays(draft.inactiveDays())) : null;
        return new CampaignMapper.Audience(draft.audience().name(), inactiveSince, draft.branchId());
    }

    /** SMS-də şirkətin adı: "SMS-də görünən ad", yoxdursa şirkətin adı (OTP ilə eyni qayda). */
    private String companyName() {
        CompanySettings.Settings settings = company.current();
        return settings.smsSenderName() != null ? settings.smsSenderName() : settings.name().resolve(Language.DEFAULT);
    }
}
