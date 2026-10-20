package az.tapstamp.campaign;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.campaign.repository.CampaignMapper;
import az.tapstamp.campaign.repository.CampaignMapper.Campaign;
import az.tapstamp.company.service.CompanySettings;
import az.tapstamp.loyalty.stamp.StampLedger;
import az.tapstamp.notification.sms.SmsDeliveryException;
import az.tapstamp.notification.sms.SmsLog;
import az.tapstamp.notification.sms.SmsMessage;
import az.tapstamp.notification.sms.SmsProvider;
import az.tapstamp.security.crypto.Aad;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.TenantInfo;
import az.tapstamp.tenancy.registry.TenantDirectory;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Təsdiqlənmiş kampaniyaların göndərilməsi (bax: docs/campaigns.md, "Göndərişin etibarlılığı"). Yalnız şirkətin saat
 * qurşağı ilə 09:00–21:00; hər addımda kampaniya üçün ən çox {@value #BATCH} alıcı.
 *
 * <ul>
 *   <li>Alıcı göndərişdən əvvəl {@code SENDING} kimi götürülür ({@code FOR UPDATE SKIP LOCKED}) — iki backend eyni
 *       anda işləsə də, SMS iki dəfə getmir.</li>
 *   <li>SMS qəbul olundu → {@code SENT}; möhür kampaniyasında möhürlər <b>eyni tranzaksiyada</b> verilir.</li>
 *   <li>Müvəqqəti xəta → 10 dəq, 30 dəq, 2 saat sonra təkrar (ən çox {@value #MAX_ATTEMPTS} cəhd).</li>
 *   <li>Provayder əlçatmazdır → cəhd sayılmır, növbəti addımda davam edir.</li>
 *   <li>Hesab səviyyəli xəta (icazə, balans) → kampaniya {@code PAUSED}; platforma admini "Davam et" basır.</li>
 *   <li>Nömrə rədd edildi → {@code FAILED} (möhür verilmir).</li>
 *   <li>Nəticə məlum deyil (cavab gəlmədi, proses kəsildi) → SMS getmiş sayılır, təkrar göndərilmir, möhür verilir
 *       (möhürsüz "hədiyyə" SMS-i müştərini aldatmış olardı).</li>
 *   <li>Göndəriş başlayandan {@value #DEADLINE_HOURS} saat sonra gedə bilməyənlər son olaraq {@code FAILED}.</li>
 * </ul>
 * Taymer: {@link CampaignJob}; testlər {@link #sendForCurrentTenant()}-i birbaşa çağırır.
 */
@Service
public class CampaignSender {

    static final int BATCH = 100;
    static final int MAX_ATTEMPTS = 4;
    static final int DEADLINE_HOURS = 48;
    static final LocalTime WINDOW_START = LocalTime.of(9, 0);
    static final LocalTime WINDOW_END = LocalTime.of(21, 0);
    /** Təkrar cəhdlər arası: 1-ci xətadan sonra 10 dəq, 2-cidən sonra 30 dəq, 3-cüdən sonra 2 saat. */
    private static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(10), Duration.ofMinutes(30),
            Duration.ofHours(2));
    private static final Duration STALE = Duration.ofMinutes(10);
    private static final String UNKNOWN_NOTE = "Nəticə məlum deyil — göndərilmiş sayılır";
    private static final Logger log = LoggerFactory.getLogger(CampaignSender.class);

    private final TenantDirectory directory;
    private final CampaignMapper campaigns;
    private final CompanySettings company;
    private final EnvelopeCipher cipher;
    private final SmsProvider sms;
    private final SmsLog smsLog;
    private final StampLedger ledger;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CampaignSender(TenantDirectory directory, CampaignMapper campaigns, CompanySettings company,
                          @Qualifier("tenantCipher") EnvelopeCipher cipher, SmsProvider sms, SmsLog smsLog,
                          StampLedger ledger, AuditLogger audit, PlatformTransactionManager transactionManager,
                          Clock clock) {
        this.directory = directory;
        this.campaigns = campaigns;
        this.company = company;
        this.cipher = cipher;
        this.sms = sms;
        this.smsLog = smsLog;
        this.ledger = ledger;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Bütün aktiv şirkətlər üçün bir addım. */
    public void run() {
        for (TenantRecord tenant : directory.findAll()) {
            if (tenant.status().outOfService()) {
                continue;
            }
            try {
                TenantContext.runAs(tenant.toInfo(), this::sendForCurrentTenant);
            } catch (RuntimeException e) {
                log.warn("Campaign sending failed for tenant {}: {}", tenant.slug(), e.getClass().getSimpleName());
            }
        }
    }

    /** Cari şirkətin göndərilməli kampaniyaları üçün bir addım (testlər birbaşa çağırır). */
    public void sendForCurrentTenant() {
        TenantInfo tenant = TenantContext.require();
        Instant now = clock.instant();
        LocalTime local = now.atZone(tenant.timezone()).toLocalTime();
        if (local.isBefore(WINDOW_START) || !local.isBefore(WINDOW_END)) {
            return;
        }
        String companyName = companyName();
        for (UUID campaignId : campaigns.sendable()) {
            campaigns.startSending(campaignId, now.plus(Duration.ofHours(DEADLINE_HOURS)));
            Campaign campaign = campaigns.find(campaignId).orElseThrow();
            if (!"SENDING".equals(campaign.status())) {
                continue;
            }
            campaigns.skipIneligible(campaignId);
            // Proses SMS-in ortasında kəsilib: nəticə məlum deyil — göndərilmiş sayılır
            for (CampaignMapper.Recipient stale : campaigns.staleSending(campaignId, now.minus(STALE))) {
                delivered(tenant, campaign, stale, UNKNOWN_NOTE);
            }
            if (campaign.deadlineAt() != null && !now.isBefore(campaign.deadlineAt())) {
                campaigns.expirePending(campaignId, now);
            } else if (!send(tenant, campaign, companyName, campaigns.claim(campaignId, BATCH, now))) {
                continue;
            }
            if (campaigns.finishIfDone(campaignId, clock.instant()) == 1) {
                log.info("Campaign {} finished", campaignId);
            }
        }
    }

    /** Alıcılara göndəriş; {@code false} — kampaniya dayandırıldı və ya provayder əlçatmazdır (davam etmə). */
    private boolean send(TenantInfo tenant, Campaign campaign, String companyName,
                         List<CampaignMapper.Recipient> batch) {
        for (int i = 0; i < batch.size(); i++) {
            CampaignMapper.Recipient recipient = batch.get(i);
            String text = CampaignTexts.message(companyName, campaign.text(), campaign.personalized(),
                    recipient.name(), campaign.latin());
            String phone;
            try {
                phone = cipher.decryptString(recipient.phoneEnc(),
                        Aad.tenantField(tenant.id(), "customers", "phone_enc", recipient.customerId()));
            } catch (RuntimeException e) {
                campaigns.markFailed(campaign.id(), recipient.customerId(), clock.instant(), "Nömrə oxunmadı");
                continue;
            }
            try {
                sms.send(new SmsMessage(phone, text, tenant.id().toString()));
                delivered(tenant, campaign, recipient, null);
                smsLog.record(tenant.id(), phone, SmsLog.Purpose.CAMPAIGN, text, text, null, null);
            } catch (SmsDeliveryException e) {
                String error = e.kind() + ": " + e.getMessage();
                switch (e.kind()) {
                    case UNKNOWN -> {
                        delivered(tenant, campaign, recipient, UNKNOWN_NOTE);
                        smsLog.record(tenant.id(), phone, SmsLog.Purpose.CAMPAIGN, text, text, null, null);
                    }
                    case RECIPIENT -> {
                        campaigns.markFailed(campaign.id(), recipient.customerId(), clock.instant(), error);
                        smsLog.record(tenant.id(), phone, SmsLog.Purpose.CAMPAIGN, text, text, null, error);
                    }
                    case TRANSIENT -> retryOrFail(tenant, campaign, recipient, phone, text, error);
                    case PROVIDER_DOWN -> {
                        releaseRest(campaign, batch, i);
                        return false;
                    }
                    case ACCOUNT -> {
                        releaseRest(campaign, batch, i);
                        if (campaigns.pause(campaign.id(), e.getMessage()) == 1) {
                            log.warn("Campaign {} paused: {}", campaign.id(), e.getMessage());
                            audit.tenant("CAMPAIGN_PAUSED", AuditLogger.ActorType.SYSTEM, null, "campaign",
                                    campaign.id(), Map.of("reason", e.getMessage()));
                        }
                        return false;
                    }
                }
            } catch (RuntimeException e) {
                // Provayderin növləndirmədiyi xəta — müvəqqəti sayılır
                retryOrFail(tenant, campaign, recipient, phone, text, e.getClass().getSimpleName());
            }
        }
        return true;
    }

    private void retryOrFail(TenantInfo tenant, Campaign campaign, CampaignMapper.Recipient recipient, String phone,
                             String text, String error) {
        int attempt = recipient.attempts() + 1;
        if (attempt >= MAX_ATTEMPTS) {
            campaigns.markFailed(campaign.id(), recipient.customerId(), clock.instant(), error);
            smsLog.record(tenant.id(), phone, SmsLog.Purpose.CAMPAIGN, text, text, null, error);
        } else {
            campaigns.retryLater(campaign.id(), recipient.customerId(),
                    clock.instant().plus(BACKOFF.get(attempt - 1)), error);
        }
    }

    /** Cəhd edilməyən alıcılar (bu daxil) növbəyə qaytarılır — cəhd sayılmır. */
    private void releaseRest(Campaign campaign, List<CampaignMapper.Recipient> batch, int from) {
        for (int i = from; i < batch.size(); i++) {
            campaigns.release(campaign.id(), batch.get(i).customerId());
        }
    }

    /** SMS göndərildi: alıcı SENT olur; möhür kampaniyasında möhürlər də eyni tranzaksiyada verilir (bir dəfə). */
    private void delivered(TenantInfo tenant, Campaign campaign, CampaignMapper.Recipient recipient,
                           @Nullable String note) {
        Instant now = clock.instant();
        tx.executeWithoutResult(status -> {
            campaigns.markSent(campaign.id(), recipient.customerId(), now, note);
            if ("STAMPS".equals(campaign.type()) && campaign.stampCount() != null
                    && campaigns.markStampsGranted(campaign.id(), recipient.customerId(), now) == 1) {
                grantStamps(tenant, campaign, recipient, now);
            }
        });
    }

    private void grantStamps(TenantInfo tenant, Campaign campaign, CampaignMapper.Recipient recipient, Instant now) {
        UUID branchId = campaign.branchId() != null ? campaign.branchId()
                : recipient.lastBranchId() != null ? recipient.lastBranchId()
                : campaigns.defaultBranch().orElseThrow(() -> new IllegalStateException("No active branch"));
        LocalDate day = now.atZone(tenant.timezone()).toLocalDate();
        ledger.lockAccount(recipient.customerId(), now);
        for (int i = 0; i < campaign.stampCount(); i++) {
            UUID stampId = ledger.insertCampaignStamp(branchId, recipient.customerId(), campaign.id(), day, now);
            ledger.applyConfirmed(recipient.customerId(), stampId, now);
        }
        audit.tenant("CAMPAIGN_STAMPS_GRANTED", AuditLogger.ActorType.SYSTEM, null, "customer",
                recipient.customerId(), Map.of("campaignId", campaign.id().toString(), "stamps", campaign.stampCount()));
    }

    private String companyName() {
        CompanySettings.Settings settings = company.current();
        return settings.smsSenderName() != null ? settings.smsSenderName() : settings.name().resolve(Language.DEFAULT);
    }
}
