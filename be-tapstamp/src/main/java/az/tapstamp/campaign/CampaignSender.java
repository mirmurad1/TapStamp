package az.tapstamp.campaign;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import az.tapstamp.campaign.repository.CampaignMapper;
import az.tapstamp.campaign.repository.CampaignMapper.Campaign;
import az.tapstamp.company.service.CompanySettings;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Təsdiqlənmiş kampaniyaların göndərilməsi: hər 30 saniyədən bir, hər şirkətdə hər kampaniya üçün ən çox
 * {@value #BATCH} SMS. Yalnız şirkətin saat qurşağı ilə 09:00–21:00 arası (gecə SMS getmir).
 *
 * <p>Alıcı göndərişdən əvvəl {@code SENDING} kimi götürülür ({@code FOR UPDATE SKIP LOCKED}): deploy zamanı iki backend
 * eyni anda işləsə də, bir müştəriyə SMS iki dəfə getmir. Proses SMS-in ortasında kəsilsə, alıcı 10 dəqiqə sonra
 * FAILED olur (təkrar göndərmək riskinə girmirik). Taymer: {@link CampaignJob} (testlərdə söndürülür, testlər
 * {@link #sendForCurrentTenant()}-i birbaşa çağırır).
 */
@Service
public class CampaignSender {

    static final int BATCH = 100;
    static final LocalTime WINDOW_START = LocalTime.of(9, 0);
    static final LocalTime WINDOW_END = LocalTime.of(21, 0);
    private static final Duration STALE = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(CampaignSender.class);

    private final TenantDirectory directory;
    private final CampaignMapper campaigns;
    private final CompanySettings company;
    private final EnvelopeCipher cipher;
    private final SmsProvider sms;
    private final SmsLog smsLog;
    private final Clock clock;

    public CampaignSender(TenantDirectory directory, CampaignMapper campaigns, CompanySettings company,
                          @Qualifier("tenantCipher") EnvelopeCipher cipher, SmsProvider sms, SmsLog smsLog, Clock clock) {
        this.directory = directory;
        this.campaigns = campaigns;
        this.company = company;
        this.cipher = cipher;
        this.sms = sms;
        this.smsLog = smsLog;
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
        for (UUID campaignId : campaigns.sendable()) {
            Campaign campaign = campaigns.find(campaignId).orElseThrow();
            if ("APPROVED".equals(campaign.status())) {
                campaigns.changeStatus(campaignId, "APPROVED", "SENDING", null, null, null);
            }
            campaigns.skipIneligible(campaignId);
            campaigns.failStale(campaignId, now.minus(STALE), now);
            send(tenant, campaign, campaigns.claim(campaignId, BATCH, now));
            if (campaigns.finishIfDone(campaignId, clock.instant()) == 1) {
                log.info("Campaign {} finished", campaignId);
            }
        }
    }

    private void send(TenantInfo tenant, Campaign campaign, List<CampaignMapper.Recipient> recipients) {
        if (recipients.isEmpty()) {
            return;
        }
        String text = CampaignTexts.compose(companyName(), campaign.text());
        for (CampaignMapper.Recipient recipient : recipients) {
            String error = null;
            String phone = null;
            try {
                phone = cipher.decryptString(recipient.phoneEnc(),
                        Aad.tenantField(tenant.id(), "customers", "phone_enc", recipient.customerId()));
                sms.send(new SmsMessage(phone, text, tenant.id().toString()));
            } catch (RuntimeException e) {
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            campaigns.markRecipient(campaign.id(), recipient.customerId(), error == null ? "SENT" : "FAILED",
                    clock.instant(), error);
            if (phone != null) {
                smsLog.record(tenant.id(), phone, SmsLog.Purpose.CAMPAIGN, text, text, null, error);
            }
        }
    }

    private String companyName() {
        CompanySettings.Settings settings = company.current();
        return settings.smsSenderName() != null ? settings.smsSenderName() : settings.name().resolve(Language.DEFAULT);
    }
}
