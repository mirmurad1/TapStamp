package az.tapstamp.campaign;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Kampaniya göndərişinin taymeri: hər 30 saniyədən bir. Testlərdə söndürülür ({@code app.jobs.enabled=false}). */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class CampaignJob {

    private final CampaignSender sender;

    public CampaignJob(CampaignSender sender) {
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${app.jobs.campaign-interval:30s}", initialDelayString = "45s")
    public void run() {
        sender.run();
    }
}
