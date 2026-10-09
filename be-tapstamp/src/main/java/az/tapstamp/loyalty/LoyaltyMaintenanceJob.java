package az.tapstamp.loyalty;

import java.time.Clock;
import java.time.Instant;

import az.tapstamp.loyalty.repository.GiftCardMapper;
import az.tapstamp.loyalty.stamp.StampApprovalService;
import az.tapstamp.nfc.repository.TapTokenMapper;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.registry.TenantDirectory;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hər dəqiqə, hər aktiv şirkət üçün: vaxtı keçmiş toxunuş tokenləri EXPIRED, təsdiqlənməyən möhürlər
 * {@code PENDING_TIMEOUT} ilə rədd, müddəti bitmiş hədiyyə kartları EXPIRED olur. Testlərdə söndürülür
 * ({@code app.jobs.enabled=false}) — testlər metodları birbaşa çağırır.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class LoyaltyMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyMaintenanceJob.class);

    private final TenantDirectory directory;
    private final TapTokenMapper tokens;
    private final StampApprovalService approvals;
    private final GiftCardMapper giftCards;
    private final Clock clock;

    public LoyaltyMaintenanceJob(TenantDirectory directory, TapTokenMapper tokens, StampApprovalService approvals,
                                 GiftCardMapper giftCards, Clock clock) {
        this.directory = directory;
        this.tokens = tokens;
        this.approvals = approvals;
        this.giftCards = giftCards;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.jobs.loyalty-maintenance-interval:60s}", initialDelayString = "30s")
    public void run() {
        for (TenantRecord tenant : directory.findAll()) {
            if (!tenant.status().servesRequests()) {
                continue;
            }
            try {
                TenantContext.runAs(tenant.toInfo(), this::runForCurrentTenant);
            } catch (RuntimeException e) {
                log.warn("Loyalty maintenance failed for tenant {}: {}", tenant.slug(), e.getClass().getSimpleName());
            }
        }
    }

    public void runForCurrentTenant() {
        Instant now = clock.instant();
        int expiredTokens = tokens.expireStale(now);
        int timedOut = approvals.rejectTimedOut();
        int expiredCards = giftCards.expireOverdue(now);
        if (timedOut + expiredCards > 0) {
            log.info("Loyalty maintenance: {} tokens expired, {} pending stamps timed out, {} gift cards expired",
                    expiredTokens, timedOut, expiredCards);
        }
    }
}
