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

    /** Kampaniyanın parametrləri. {@code inactiveDays} yalnız INACTIVE üçün (7–365). */
    public record Draft(String text, Audience audience, @Nullable Integer inactiveDays, @Nullable UUID branchId) {
    }

    /**
     * Önizləmə: göndəriləcək tam mətn və onun SMS sayı, həmçinin latın hərfləri ilə variantı (Azərbaycan hərfləri
     * bir SMS-i 160 yox, 70 simvol edir).
     */
    public record Preview(long recipients, String fullText, String encoding, int characters, int segments, long totalSms,
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
            Campaign row = new Campaign(id, draft.text().strip(), draft.audience().name(), draft.inactiveDays(),
                    draft.branchId(), 0, preview.segments(), 0, "PENDING_APPROVAL", null, principal.userId(), now,
                    null, 0, 0, null);
            campaigns.insert(row);
            int recipients = campaigns.insertRecipients(id, audience);
            if (recipients == 0) {
                throw new ApiException(ErrorCode.CAMPAIGN_NO_RECIPIENTS);
            }
            campaigns.updateCounts(id, recipients, recipients * preview.segments());
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

    /** Sahib təsdiqə qədər ləğv edə bilər. */
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

    // ---------------------------------------------------------------- müştəri

    public boolean marketingEnabled(UUID customerId) {
        return !campaigns.isMarketingOptedOut(customerId);
    }

    public void setMarketingEnabled(UUID customerId, boolean enabled) {
        campaigns.setMarketingOptOut(customerId, enabled ? null : clock.instant());
        audit.tenant(enabled ? "CUSTOMER_MARKETING_ENABLED" : "CUSTOMER_MARKETING_DISABLED",
                AuditLogger.ActorType.CUSTOMER, customerId, "customer", customerId, Map.of());
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
        String full = CampaignTexts.compose(companyName(), draft.text());
        String latin = CampaignTexts.latin(full);
        SmsSegments.Info info = CampaignTexts.analyze(full);
        SmsSegments.Info latinInfo = CampaignTexts.analyze(latin);
        long recipients = campaigns.countAudience(audience(draft, clock.instant()));
        return new Preview(recipients, full, info.encoding().name(), info.units(), info.segments(),
                recipients * info.segments(), latin, latinInfo.segments(), recipients * latinInfo.segments(),
                latinInfo.segments() < info.segments());
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
