package az.tapstamp.bonus.api;

import java.util.List;

import az.tapstamp.bonus.BonusRuleService;
import az.tapstamp.bonus.BonusRuleService.PublicRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bonus qaydaları — müştəri tətbiqi üçün (kartın üstündəki banner). */
@RestController
@Tag(name = "Müştəri: bonus qaydaları")
public class PublicBonusRuleController {

    private final BonusRuleService bonusRules;

    public PublicBonusRuleController(BonusRuleService bonusRules) {
        this.bonusRules = bonusRules;
    }

    @GetMapping("/api/v1/public/bonus-rules")
    @Operation(summary = "Bu günün bonus qaydaları (giriş tələb etmir)", description = """
            Bu gün tarix aralığında olan aktiv qaydalar: cədvəl (günlər, saat aralığı), bitmə tarixi, filial və bu an \
            qüvvədədirmi (`runningNow`). Paketdə imkan yoxdursa — boş siyahı.""")
    List<PublicRule> current() {
        return bonusRules.current();
    }
}
