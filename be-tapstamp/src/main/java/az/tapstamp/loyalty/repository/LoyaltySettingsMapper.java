package az.tapstamp.loyalty.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.loyalty.settings.LoyaltySettingsService.RewardSettings;
import az.tapstamp.loyalty.settings.LoyaltySettingsService.StampRules;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.TenantMapper;

/**
 * Sahibin loyallıq parametrləri: şirkətin möhür qaydası ({@code stamp_policies}, {@code branch_id IS NULL}) və hədiyyə
 * qaydasının versiyaları ({@code reward_rules}). SQL: {@code mapper/tenant/loyalty/LoyaltySettingsMapper.xml}.
 */
@TenantMapper
public interface LoyaltySettingsMapper {

    /** Aktiv qaydanın açarı (dəyişiklik zamanı kilidlənir). */
    record ActiveRuleKey(UUID id, int ruleVersion, int stampsRequired) {
    }

    /** Hədd dəyişikliyinin təsiri (yalnız aktiv müştərilər). */
    record ImpactRow(int inProgress, int reaching, int giftCards) {
    }

    /** Hesab: hədiyyə kartı veriləcək müştəri və onun cari möhürləri. */
    record AccountStamps(UUID customerId, int currentStamps) {
    }

    StampRules findCompanyStampRules();

    void updateCompanyStampRules(StampRules rules, UUID staffId, Instant now);

    Optional<RewardSettings> findActiveReward();

    /** Paralel iki dəyişiklik eyni versiya nömrəsini almasın — aktiv qayda sətri kilidlənir. */
    Optional<ActiveRuleKey> lockActiveRule();

    int nextRuleVersion();

    void deactivateRules();

    void insertRule(UUID id, int version, int stampsRequired, LocalizedText name, LocalizedText description,
                    Integer validityDays, UUID staffId, Instant now);

    /** Yarımçıq kartlar (hələ qaydaya bağlanmayıbsa) verilmiş qaydaya bağlanır. */
    void pinCardsInProgress(UUID ruleId);

    /** Bütün kartlar aktiv qaydaya keçir. */
    void unpinAllCards();

    int countPinnedCards();

    ImpactRow impact(int stampsRequired);

    /** Kartında ən azı {@code required} möhür olan aktiv müştərilər (hesablar kilidlənir). */
    List<AccountStamps> lockAccountsReaching(int required);

    void deductRewards(UUID customerId, int usedStamps, int cards, Instant now);
}
