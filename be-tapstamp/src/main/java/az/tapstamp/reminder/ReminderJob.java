package az.tapstamp.reminder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Xatırlatmaların taymeri: hər 15 dəqiqədən bir. Testlərdə söndürülür ({@code app.jobs.enabled=false}). */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderJob {

    private final ReminderService reminders;

    public ReminderJob(ReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(fixedDelayString = "${app.jobs.reminder-interval:15m}", initialDelayString = "90s")
    public void run() {
        reminders.run();
    }
}
