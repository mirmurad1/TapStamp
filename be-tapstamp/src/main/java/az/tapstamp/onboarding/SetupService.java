package az.tapstamp.onboarding;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import az.tapstamp.onboarding.repository.SetupMapper;
import az.tapstamp.onboarding.repository.SetupMapper.Signals;
import az.tapstamp.plan.Addon;
import az.tapstamp.plan.Feature;
import az.tapstamp.plan.PlanService;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.tenancy.TenantContext;
import org.springframework.stereotype.Service;

/**
 * Başlanğıc siyahısı (şirkət sahibinin göstəricilər səhifəsi). İki hissə:
 * <ul>
 *   <li><b>məcburi addımlar</b> — bütün paketlərdə, tətbiqin düzgün işləməsi üçün: loqo və brend rəngi, hədiyyə
 *   qaydası, filial və iş saatları, işçi dəvəti, NFC çipi (platformadan tələb olunur). Hamısı tamamlanana qədər
 *   gizlədilmir;</li>
 *   <li><b>müştəri cəlbi tövsiyələri</b> — məcburi deyil, yalnız şirkətin paketində olan imkanlar: rəy sorğusu, Google
 *   rəy linki, dost dəvəti, səviyyələr, xatırlatmalar, bonus kampaniyası, menyu. Sahib gizlədə bilər.</li>
 * </ul>
 * Heç nə əl ilə işarələnmir — vəziyyət mövcud məlumatdan hesablanır. İstisna: loqo bir dəfə yüklənibsə, sonradan
 * silinsə də addım tamamlanmış qalır (standart loqo göstərilir). Qalan addımlar canlıdır: məs. bütün filiallar
 * silinsə, kart yenidən görünür.
 */
@Service
public class SetupService {

    /** {@code done} — tamamlanıb (tövsiyələrdə — açılıb); {@code partial} — başlanıb, amma yarımçıqdır. */
    public record Step(String key, boolean done, boolean partial) {
    }

    /** {@code recommendationsHidden} — sahib tövsiyələri gizlədib (məcburi addımlar yenə göstərilir). */
    public record SetupView(List<Step> required, int requiredDone, boolean requiredComplete, List<Step> recommended,
                            boolean recommendationsHidden) {
    }

    private final SetupMapper setup;
    private final PlanService plans;
    private final Clock clock;

    public SetupService(SetupMapper setup, PlanService plans, Clock clock) {
        this.setup = setup;
        this.plans = plans;
        this.clock = clock;
    }

    public SetupView view(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        return progress();
    }

    /** Sahib tövsiyələri gizlədir və ya yenidən göstərir. */
    public SetupView hideRecommendations(AdminPrincipal principal, boolean hidden) {
        AccessPolicy.requireCompanyWide(principal);
        setup.setRecommendationsHidden(hidden ? clock.instant() : null, principal.userId());
        return progress();
    }

    /** Cari şirkətin vəziyyəti (platforma paneli də şirkətin kontekstində çağırır). */
    public SetupView progress() {
        Instant now = clock.instant();
        LocalDate today = now.atZone(TenantContext.require().timezone()).toLocalDate();
        Signals s = setup.signals(now, today);
        SetupMapper.State state = setup.state();
        // Loqo: bir dəfə yüklənibsə, sonradan silinsə də tamamlanmış sayılır (standart loqo görünür)
        boolean branding = s.branding() || state.brandingDoneAt() != null;
        if (s.branding() && state.brandingDoneAt() == null) {
            setup.markBrandingDone(now);
        }
        List<Step> required = List.of(
                new Step("BRANDING", branding, false),
                new Step("REWARD_RULE", s.rewardRule(), false),
                new Step("BRANCH", s.branch() && s.branchHours(), s.branch() && !s.branchHours()),
                new Step("TEAM", s.team(), false),
                // Çipi platforma proqramlaşdırıb qeydiyyata alır — alınan kimi tamamlanır
                new Step("NFC_TAG", s.nfcTag(), false));
        int done = (int) required.stream().filter(Step::done).count();
        List<Step> recommended = new ArrayList<>();
        if (plans.has(Feature.FEEDBACK)) {
            recommended.add(new Step("FEEDBACK", s.feedback(), false));
        }
        recommended.add(new Step("GOOGLE_REVIEW", s.googleReview(), false));
        if (plans.has(Feature.REFERRALS)) {
            recommended.add(new Step("REFERRALS", s.referrals(), false));
        }
        if (plans.has(Feature.TIERS)) {
            recommended.add(new Step("TIERS", s.tiers(), false));
        }
        if (plans.has(Feature.REMINDERS)) {
            recommended.add(new Step("REMINDERS", s.reminders(), false));
        }
        if (plans.has(Feature.BONUS_RULES)) {
            recommended.add(new Step("BONUS_RULES", s.bonusRules(), false));
        }
        if (plans.has(Addon.CATALOG)) {
            recommended.add(new Step("CATALOG", s.catalog(), false));
        }
        return new SetupView(required, done, done == required.size(), List.copyOf(recommended),
                state.recommendationsHiddenAt() != null);
    }
}
