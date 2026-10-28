package az.tapstamp.bonus;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Başlayan bonus qaydalarının bildirişi: hər 15 dəqiqədən bir. Testlərdə söndürülür ({@code app.jobs.enabled=false}). */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class BonusRuleJob {

    private final BonusRuleService bonusRules;

    public BonusRuleJob(BonusRuleService bonusRules) {
        this.bonusRules = bonusRules;
    }

    @Scheduled(fixedDelayString = "${app.jobs.bonus-rule-interval:15m}", initialDelayString = "120s")
    public void run() {
        bonusRules.notifyStarted();
    }
}
