package az.tapstamp.loyalty.settings;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.loyalty.repository.LoyaltySettingsMapper.AccountStamps;
import az.tapstamp.loyalty.repository.LoyaltySettingsMapper.ActiveRuleKey;
import az.tapstamp.loyalty.repository.LoyaltySettingsMapper.ImpactRow;
import az.tapstamp.loyalty.repository.LoyaltySettingsMapper;
import az.tapstamp.loyalty.reward.GiftCardService;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminAuthService;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Şirkətin loyallıq parametrləri (yalnız sahib): möhür qaydası (cooldown, gündəlik limit, işçi təsdiqi) və hədiyyə
 * (neçə möhürdən sonra, adı 3 dildə, kartın müddəti). Hər dəyişiklik auditə yazılır.
 *
 * <p>Hədiyyə qaydası dəyişəndə yeni versiya yaradılır, köhnəsi saxlanılır — artıq verilmiş hədiyyə kartları öz adını və
 * möhür sayını (snapshot) saxlayır. Möhür həddi dəyişəndə (qərar 45) standart olaraq yarımçıq kartlar köhnə qaydaya bağlanır
 * və onunla bitir ({@link ExistingCards#KEEP_RULE}); sahib istəsə, yeni qayda hamıya dərhal tətbiq olunur və kartı
 * artıq dolmuş müştərilərə hədiyyə kartı həmin tranzaksiyada verilir ({@link ExistingCards#APPLY_NOW}).
 */
@Service
public class LoyaltySettingsService {

    /** Şirkət üzrə standart möhür qaydası (filial override-ları ayrıca idarə olunur). */
    public record StampRules(int minIntervalMinutes, int maxStampsPerDay, boolean requireStaffApproval,
                             int pendingTimeoutMinutes) {
    }

    public record RewardSettings(int version, int stampsRequired, LocalizedText name, LocalizedText description,
                                 Integer validityDays) {
    }

    /** Möhür həddi dəyişəndə yarımçıq kartlarla nə edilir. */
    public enum ExistingCards {
        /** Yarımçıq kartlar köhnə qayda ilə bitir; yeni qayda növbəti kartdan (standart). */
        KEEP_RULE,
        /** Yeni qayda hamıya dərhal; kartı artıq dolmuş müştərilərə hədiyyə kartı verilir. */
        APPLY_NOW
    }

    /** Dəyişikliyin təsiri (yadda saxlamazdan əvvəl sahibə göstərilir). */
    public record RewardImpact(Integer currentStampsRequired, int cardsInProgress, int cardsReachingNewThreshold,
                               int giftCardsIfAppliedNow) {
    }

    public record RewardUpdate(int stampsRequired, LocalizedText name, LocalizedText description,
                               Integer validityDays, ExistingCards existingCards) {
    }

    /** Qayda dəyişikliyinin nəticəsi: yeni hədd səbəbindən dərhal verilən hədiyyə kartlarının sayı. */
    public record RewardChange(RewardSettings settings, int giftCardsIssued) {
    }

    private final LoyaltySettingsMapper settings;
    private final GiftCardService giftCards;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final AdminAuthService adminAuth;

    public LoyaltySettingsService(LoyaltySettingsMapper settings, GiftCardService giftCards,
                                  AuditLogger audit, PlatformTransactionManager transactionManager, Clock clock,
                                  AdminAuthService adminAuth) {
        this.settings = settings;
        this.giftCards = giftCards;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.adminAuth = adminAuth;
    }

    public StampRules stampRules(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        return settings.findCompanyStampRules();
    }

    public StampRules updateStampRules(AdminPrincipal principal, StampRules rules) {
        AccessPolicy.requireCompanyWide(principal);
        StampRules before = stampRules(principal);
        settings.updateCompanyStampRules(rules, principal.userId(), clock.instant());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("before", before);
        details.put("after", rules);
        audit.tenant("STAMP_POLICY_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "stamp_policy", "company",
                details);
        return rules;
    }

    public RewardSettings reward(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        return current();
    }

    public RewardChange updateReward(AdminPrincipal principal, RewardUpdate update) {
        AccessPolicy.requireCompanyWide(principal);
        if (update.existingCards() == ExistingCards.APPLY_NOW) {
            // Kütləvi hədiyyə kartı verə bilər — kimliyin təkrar təsdiqi (2FA yoxdursa şifrə ilə)
            adminAuth.requireStepUp(principal);
        }
        Instant now = clock.instant();
        return tx.execute(status -> {
            // Paralel iki dəyişiklik eyni versiya nömrəsini almasın
            ActiveRuleKey previous = settings.lockActiveRule().orElse(null);
            int previousVersion = previous == null ? 0 : previous.ruleVersion();
            boolean thresholdChanged = previous != null && previous.stampsRequired() != update.stampsRequired();
            boolean applyNow = update.existingCards() == ExistingCards.APPLY_NOW;
            if (thresholdChanged && !applyNow) {
                // Yarımçıq kartlar (hələ qaydaya bağlanmayıbsa) köhnə qaydada qalır və onunla bitir
                settings.pinCardsInProgress(previous.id());
            }
            int nextVersion = settings.nextRuleVersion();
            settings.deactivateRules();
            UUID id = Ids.newId();
            settings.insertRule(id, nextVersion, update.stampsRequired(), clean(update.name()),
                    clean(update.description()), update.validityDays(), principal.userId(), now);
            int issued = 0;
            if (applyNow) {
                // Bütün kartlar yeni qaydaya keçir (köhnə qaydaya bağlananlar da)
                settings.unpinAllCards();
                issued = issueForReachedThreshold(update.stampsRequired(), now);
            }
            int keptOnOldRule = settings.countPinnedCards();
            audit.tenant("REWARD_RULE_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "reward_rule", id,
                    Map.of("previousVersion", previousVersion, "version", nextVersion,
                            "stampsRequired", update.stampsRequired(), "existingCards", update.existingCards().name(),
                            "giftCardsIssued", issued, "cardsOnPreviousRule", keptOnOldRule));
            return new RewardChange(current(), issued);
        });
    }

    /**
     * Yeni möhür həddinin təsiri: neçə müştərinin yarımçıq kartı var (köhnə qaydada qalacaq) və qayda hamıya dərhal
     * tətbiq olunsa, neçə müştəriyə neçə hədiyyə kartı veriləcək. Yalnız aktiv müştərilər sayılır.
     */
    public RewardImpact rewardImpact(AdminPrincipal principal, int stampsRequired) {
        AccessPolicy.requireCompanyWide(principal);
        if (stampsRequired < 1 || stampsRequired > 50) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        RewardSettings current = current();
        ImpactRow impact = settings.impact(stampsRequired);
        return new RewardImpact(current == null ? null : current.stampsRequired(), impact.inProgress(),
                impact.reaching(), impact.giftCards());
    }

    /** Qayda hamıya dərhal tətbiq olunanda: kartı artıq dolmuş müştərilərə hədiyyə kartı verilir (hesablar kilidlənir). */
    private int issueForReachedThreshold(int required, Instant now) {
        GiftCardService.ActiveRule rule = giftCards.activeRule().orElseThrow();
        int issued = 0;
        for (AccountStamps account : settings.lockAccountsReaching(required)) {
            int cards = account.currentStamps() / required;
            for (int i = 0; i < cards; i++) {
                giftCards.issue(account.customerId(), rule, now);
            }
            settings.deductRewards(account.customerId(), cards * required, cards, now);
            issued += cards;
        }
        return issued;
    }

    private RewardSettings current() {
        return settings.findActiveReward().orElse(null);
    }

    /** Boş tərcümələr saxlanılmır (müştəri tətbiqi Azərbaycan dilindəki versiyanı göstərir). */
    private static LocalizedText clean(LocalizedText text) {
        if (text == null || text.az() == null || text.az().isBlank()) {
            return null;
        }
        return LocalizedText.of(text.az().strip(), blankToNull(text.en()), blankToNull(text.ru()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
