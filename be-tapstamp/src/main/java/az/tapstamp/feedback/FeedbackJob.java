package az.tapstamp.feedback;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Vaxtı çatmış rəy sorğularının göndərilməsi: hər dəqiqə. Testlərdə söndürülür ({@code app.jobs.enabled=false}). */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class FeedbackJob {

    private final FeedbackService feedback;

    public FeedbackJob(FeedbackService feedback) {
        this.feedback = feedback;
    }

    @Scheduled(fixedDelayString = "${app.jobs.feedback-interval:1m}", initialDelayString = "60s")
    public void run() {
        feedback.sendDue();
    }
}
