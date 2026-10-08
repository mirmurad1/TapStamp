package az.tapstamp.loyalty;

import static az.tapstamp.shared.persistence.SqlTime.ts;

import java.time.Clock;
import java.time.Instant;

import az.tapstamp.loyalty.stamp.StampApprovalService;
import az.tapstamp.nfc.TapTokenStore;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.registry.TenantDirectory;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
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
    private final TapTokenStore tokens;
    private final StampApprovalService approvals;
    private final JdbcClient jdbc;
    private final Clock clock;

    public LoyaltyMaintenanceJob(TenantDirectory directory, TapTokenStore tokens, StampApprovalService approvals,
                                 @Qualifier("tenantJdbc") JdbcClient jdbc, Clock clock) {
        this.directory = directory;
        this.tokens = tokens;
        this.approvals = approvals;
        this.jdbc = jdbc;
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
        int expiredCards = jdbc.sql("""
                        UPDATE rewards SET status = 'EXPIRED'
                        WHERE status = 'AVAILABLE' AND expires_at IS NOT NULL AND expires_at <= :now""")
                .param("now", ts(now))
                .update();
        if (timedOut + expiredCards > 0) {
            log.info("Loyalty maintenance: {} tokens expired, {} pending stamps timed out, {} gift cards expired",
                    expiredTokens, timedOut, expiredCards);
        }
    }
}
