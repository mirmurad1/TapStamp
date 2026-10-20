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
import az.tapstamp.security.crypto.Aad;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import az.tapstamp.tenancy.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Kampaniyalar (cari şirkət daxilində): SMS kampaniyası və möhür kampaniyası (kartı boş olan müştərilərə SMS + möhür).
 * Axın: sahib yaradır (alıcılar və SMS sayı həmin an müəyyənləşir) → PENDING_APPROVAL (sahib dəyişə və ya ləğv edə
 * bilər) → platforma admini ödənişdən sonra təsdiqləyir (APPROVED) və ya ləğv edir → {@link CampaignSender} göndərir
 * (SENDING → SENT; hesab xətasında PAUSED → platforma "Davam et"). Möhürlər SMS göndəriləndə verilir.
 */
@Service
public class CampaignService {

    public enum Type { SMS, STAMPS }

    /** EMPTY_CARD — kartı boş olan aktiv müştərilər (yalnız möhür kampaniyası). */
    public enum Audience { ALL, INACTIVE, NEAR_REWARD, UNUSED_GIFT_CARD, EMPTY_CARD }

    /**
     * Kampaniyanın parametrləri. {@code inactiveDays} yalnız INACTIVE üçün (7–365); {@code personalized} — mətn
     * "Hörmətli &lt;ad&gt;," ilə başlayır (adı yoxdursa "Hörmətli istifadəçi,"); {@code stampCount} — yalnız möhür
     * kampaniyası (1 … hədd − 1).
     */
    public record Draft(Type type, @Nullable Integer stampCount, String text, Audience audience,
                        @Nullable Integer inactiveDays, @Nullable UUID branchId, boolean personalized, boolean latin) {
    }

    /** SMS-i göndərilməyən alıcı (sahib üçün: ad, tam nömrə, səbəb). */
    public record FailedRecipient(UUID customerId, @Nullable String name, @Nullable String phone,
                                  @Nullable String error, @Nullable Instant at) {
    }

    /**
     * Önizləmə ({@code latin} — mesaj latın hərfləri ilə gedəcək, saylar da buna görə): {@code fullText} — nümunə
     * (fərdi müraciətdə auditoriyadan adı olan müştəri ilə), {@code template} — ad yerinə {@link #NAME_TOKEN} olan
     * şablon (müştəri panelində "Hörmətli &lt;müştərinin adı&gt;" kimi göstərilir), hər müştəriyə ən az/ən çox SMS və
     * cəmi (hər müştərinin adı ilə hesablanır); həmçinin latın hərfləri ilə variant (ASCII olmayan simvol bir SMS-i
     * 160 yox, 70 simvol edir).
     */
    public record Preview(long recipients, boolean personalized, boolean latin, String fullText, String template,
                          String encoding, int characters, int segments, int minSegments, long totalSms,
                          String latinText, int latinSegments, long latinTotalSms, boolean latinCheaper,
                          @Nullable Integer stampCount, long totalStamps, @Nullable Integer maxStamps) {
    }


    /** Önizləmə şablonunda müştəri adının yeri. */
    public static final String NAME_TOKEN = "{name}";

    private final CampaignMapper campaigns;
    private final CompanySettings company;
    private final AuditLogger audit;
    private final EnvelopeCipher cipher;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CampaignService(CampaignMapper campaigns, CompanySettings company, AuditLogger audit,
                           @Qualifier("tenantCipher") EnvelopeCipher cipher,
                           PlatformTransactionManager transactionManager, Clock clock) {
        this.campaigns = campaigns;
        this.company = company;
        this.audit = audit;
        this.cipher = cipher;
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
            Campaign row = new Campaign(id, draft.type().name(), draft.stampCount(), draft.text().strip(),
                    draft.personalized(), draft.latin(), draft.audience().name(), draft.inactiveDays(),
                    draft.branchId(), 0, preview.segments(), 0, "PENDING_APPROVAL", null, null, principal.userId(),
                    now, null, null, null, null, 0, 0, 0, 0, 0);
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
                Map.of("type", draft.type().name(), "audience", draft.audience().name(),
                        "recipients", created.recipients(), "totalSms", created.totalSms()));
        return created;
    }

    public PageResult<Campaign> list(AdminPrincipal principal, Type type, PageQuery page) {
        AccessPolicy.requireCompanyWide(principal);
        return page.result(campaigns.list(type.name(), page.limit(), page.offset()), campaigns.countByType(type.name()));
    }

    /** SMS-i göndərilməyən alıcılar və səbəbləri (sahib tam nömrəni görür). */
    public PageResult<FailedRecipient> failures(AdminPrincipal principal, UUID id, PageQuery page) {
        AccessPolicy.requireCompanyWide(principal);
        campaigns.find(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        UUID tenantId = TenantContext.require().id();
        // Yalnız göstərilən səhifənin nömrələri deşifrə olunur
        return page.result(campaigns.failedRecipients(id, page.limit(), page.offset()), campaigns.countFailed(id))
                .map(f -> new FailedRecipient(f.customerId(), f.name(), f.phoneEnc() == null ? null
                        : cipher.decryptString(f.phoneEnc(),
                                Aad.tenantField(tenantId, "customers", "phone_enc", f.customerId())),
                        f.error(), f.at()));
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
        Campaign existing = campaigns.find(id).orElseThrow();
        if (!existing.type().equals(draft.type().name())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        Campaign updated = Objects.requireNonNull(tx.execute(status -> {
            if (campaigns.updateDraft(id, draft.stampCount(), draft.text().strip(), draft.personalized(),
                    draft.latin(), draft.audience().name(), draft.inactiveDays(), draft.branchId(), now) == 0) {
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

    /** Platforma üçün: ən yeni {@code limit} kampaniya (şirkətlər arası birləşdirmə üçün). */
    public List<Campaign> byStatus(List<String> statuses, int limit) {
        return campaigns.listByStatus(statuses, limit);
    }

    public long countByStatus(List<String> statuses) {
        return campaigns.countByStatus(statuses);
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

    /** Hesab xətasına görə dayandırılmış kampaniyanı davam etdirmək (provayder problemi həll olunandan sonra). */
    public Campaign resume(UUID platformAdminId, UUID id) {
        campaigns.find(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        // Dayandırılma müddəti son müddətə sayılmır: 48 saat yenidən başlayır (əks halda gözləyənlər dərhal FAILED olardı)
        Instant deadline = clock.instant().plus(Duration.ofHours(CampaignSender.DEADLINE_HOURS));
        if (campaigns.resume(id, deadline) == 0) {
            throw new ApiException(ErrorCode.CAMPAIGN_STATE_CHANGED);
        }
        audit.tenant("CAMPAIGN_RESUMED", AuditLogger.ActorType.PLATFORM, platformAdminId, "campaign", id, Map.of());
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
        String template = draft.personalized()
                ? CampaignTexts.message(company, draft.text(), true, NAME_TOKEN, draft.latin()) : full;
        String latin = CampaignTexts.latin(full);
        SmsSegments.Info info = CampaignTexts.analyze(full);
        List<@Nullable String> forTotals = names != null ? names : java.util.Collections.nCopies((int) recipients, null);
        CampaignTexts.Totals totals = CampaignTexts.totals(company, draft.text(), draft.personalized(), forTotals,
                draft.latin());
        CampaignTexts.Totals latinTotals = CampaignTexts.totals(company, draft.text(), draft.personalized(), forTotals,
                true);
        Integer stamps = draft.type() == Type.STAMPS ? draft.stampCount() : null;
        return new Preview(recipients, draft.personalized(), draft.latin(), full, template, info.encoding().name(),
                info.units(), totals.maxSegments(), totals.minSegments(), totals.total(), latin,
                latinTotals.maxSegments(), latinTotals.total(), latinTotals.total() < totals.total(),
                stamps, stamps == null ? 0 : recipients * stamps, draft.type() == Type.STAMPS ? maxStamps() : null);
    }

    /** Möhür kampaniyasında ən çox "hədd − 1" möhür: müştəri hədiyyə üçün yenə ən azı bir dəfə gəlməlidir. */
    private int maxStamps() {
        return campaigns.activeStampsRequired().map(required -> required - 1).orElse(0);
    }

    private void validate(Draft draft) {
        boolean stampsValid = draft.type() == Type.STAMPS
                ? draft.audience() == Audience.EMPTY_CARD && draft.stampCount() != null && draft.stampCount() >= 1
                        && draft.stampCount() <= maxStamps()
                : draft.audience() != Audience.EMPTY_CARD && draft.stampCount() == null;
        if (!stampsValid || draft.text() == null || draft.text().isBlank() || draft.text().length() > 1000
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
