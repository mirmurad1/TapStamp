package az.tapstamp.catalog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Endirim bildirişlərinin taymeri: hər 5 dəqiqədən bir yoxlayır, şirkətin seçdiyi saat çatanda gündə bir dəfə göndərir.
 * Testlərdə söndürülür ({@code app.jobs.enabled=false}).
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class DealNotificationJob {

    private final DealNotificationService deals;

    public DealNotificationJob(DealNotificationService deals) {
        this.deals = deals;
    }

    @Scheduled(fixedDelayString = "${app.jobs.deal-notification-interval:5m}", initialDelayString = "120s")
    public void run() {
        deals.run();
    }
}
