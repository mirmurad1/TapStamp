package az.tapstamp.loyalty.settings;

import static az.tapstamp.shared.persistence.SqlTime.ts;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.loyalty.reward.GiftCardService;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminAuthService;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.persistence.LocalizedTextConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
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

    private static final LocalizedTextConverter TEXT = new LocalizedTextConverter();

    private final JdbcClient jdbc;
    private final GiftCardService giftCards;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final AdminAuthService adminAuth;

    public LoyaltySettingsService(@Qualifier("tenantJdbc") JdbcClient jdbc, GiftCardService giftCards,
                                  AuditLogger audit, PlatformTransactionManager transactionManager, Clock clock,
                                  AdminAuthService adminAuth) {
        this.jdbc = jdbc;
        this.giftCards = giftCards;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.adminAuth = adminAuth;
    }

    public StampRules stampRules(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        return jdbc.sql("""
                        SELECT min_interval_minutes, max_stamps_per_day, require_staff_approval, pending_timeout_minutes
                        FROM stamp_policies WHERE branch_id IS NULL""")
                .query((rs, n) -> new StampRules(rs.getInt("min_interval_minutes"), rs.getInt("max_stamps_per_day"),
                        rs.getBoolean("require_staff_approval"), rs.getInt("pending_timeout_minutes")))
                .single();
    }

    public StampRules updateStampRules(AdminPrincipal principal, StampRules rules) {
        AccessPolicy.requireCompanyWide(principal);
        StampRules before = stampRules(principal);
        jdbc.sql("""
                        UPDATE stamp_policies
                           SET min_interval_minutes = :interval, max_stamps_per_day = :maxPerDay,
                               require_staff_approval = :approval, pending_timeout_minutes = :timeout,
                               updated_by = :staffId, updated_at = :now, version = version + 1
                         WHERE branch_id IS NULL""")
                .param("interval", rules.minIntervalMinutes())
                .param("maxPerDay", rules.maxStampsPerDay())
                .param("approval", rules.requireStaffApproval())
                .param("timeout", rules.pendingTimeoutMinutes())
                .param("staffId", principal.userId())
                .param("now", ts(clock.instant()))
                .update();
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
            Map<String, Object> previous = jdbc.sql(
                            "SELECT id, rule_version, stamps_required FROM reward_rules WHERE active FOR UPDATE")
                    .query().listOfRows().stream().findFirst().orElse(null);
            int previousVersion = previous == null ? 0 : ((Number) previous.get("rule_version")).intValue();
            boolean thresholdChanged = previous != null
                    && ((Number) previous.get("stamps_required")).intValue() != update.stampsRequired();
            boolean applyNow = update.existingCards() == ExistingCards.APPLY_NOW;
            if (thresholdChanged && !applyNow) {
                // Yarımçıq kartlar (hələ qaydaya bağlanmayıbsa) köhnə qaydada qalır və onunla bitir
                jdbc.sql("""
                                UPDATE loyalty_accounts SET reward_rule_id = :ruleId
                                WHERE current_stamps > 0 AND reward_rule_id IS NULL""")
                        .param("ruleId", previous.get("id"))
                        .update();
            }
            int nextVersion = jdbc.sql("SELECT COALESCE(max(rule_version), 0) + 1 FROM reward_rules")
                    .query(Integer.class).single();
            jdbc.sql("UPDATE reward_rules SET active = false WHERE active").update();
            UUID id = Ids.newId();
            jdbc.sql("""
                            INSERT INTO reward_rules (id, rule_version, stamps_required, reward_name, reward_description,
                                                      validity_days, active, created_by, created_at)
                            VALUES (:id, :version, :stamps, CAST(:name AS jsonb), CAST(:description AS jsonb), :validity,
                                    true, :staffId, :now)""")
                    .param("id", id)
                    .param("version", nextVersion)
                    .param("stamps", update.stampsRequired())
                    .param("name", TEXT.convertToDatabaseColumn(clean(update.name())))
                    .param("description", TEXT.convertToDatabaseColumn(clean(update.description())))
                    .param("validity", update.validityDays())
                    .param("staffId", principal.userId())
                    .param("now", ts(now))
                    .update();
            int issued = 0;
            if (applyNow) {
                // Bütün kartlar yeni qaydaya keçir (köhnə qaydaya bağlananlar da)
                jdbc.sql("UPDATE loyalty_accounts SET reward_rule_id = NULL WHERE reward_rule_id IS NOT NULL").update();
                issued = issueForReachedThreshold(update.stampsRequired(), now);
            }
            int keptOnOldRule = jdbc.sql("SELECT count(*) FROM loyalty_accounts WHERE reward_rule_id IS NOT NULL")
                    .query(Integer.class).single();
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
        return jdbc.sql("""
                        SELECT count(*) FILTER (WHERE la.current_stamps > 0) AS in_progress,
                               count(*) FILTER (WHERE la.current_stamps >= :required) AS reaching,
                               COALESCE(sum(la.current_stamps / :required), 0) AS gift_cards
                        FROM loyalty_accounts la JOIN customers c ON c.id = la.customer_id
                        WHERE c.status = 'ACTIVE'""")
                .param("required", stampsRequired)
                .query((rs, n) -> new RewardImpact(current == null ? null : current.stampsRequired(),
                        rs.getInt("in_progress"), rs.getInt("reaching"), rs.getInt("gift_cards")))
                .single();
    }

    /** Qayda hamıya dərhal tətbiq olunanda: kartı artıq dolmuş müştərilərə hədiyyə kartı verilir (hesablar kilidlənir). */
    private int issueForReachedThreshold(int required, Instant now) {
        GiftCardService.ActiveRule rule = giftCards.activeRule().orElseThrow();
        List<UUID> customers = jdbc.sql("""
                        SELECT la.customer_id FROM loyalty_accounts la JOIN customers c ON c.id = la.customer_id
                        WHERE la.current_stamps >= :required AND c.status = 'ACTIVE'
                        ORDER BY la.customer_id FOR UPDATE OF la""")
                .param("required", required)
                .query(UUID.class)
                .list();
        int issued = 0;
        for (UUID customerId : customers) {
            int current = jdbc.sql("SELECT current_stamps FROM loyalty_accounts WHERE customer_id = :id")
                    .param("id", customerId).query(Integer.class).single();
            int cards = current / required;
            for (int i = 0; i < cards; i++) {
                giftCards.issue(customerId, rule, now);
            }
            jdbc.sql("""
                            UPDATE loyalty_accounts SET current_stamps = current_stamps - :used,
                                                        rewards_earned = rewards_earned + :cards, updated_at = :now
                            WHERE customer_id = :id""")
                    .param("used", cards * required)
                    .param("cards", cards)
                    .param("now", ts(now))
                    .param("id", customerId)
                    .update();
            issued += cards;
        }
        return issued;
    }

    private RewardSettings current() {
        return jdbc.sql("""
                        SELECT rule_version, stamps_required, reward_name::text AS name,
                               reward_description::text AS description, validity_days
                        FROM reward_rules WHERE active""")
                .query((rs, n) -> new RewardSettings(rs.getInt("rule_version"), rs.getInt("stamps_required"),
                        TEXT.convertToEntityAttribute(rs.getString("name")),
                        TEXT.convertToEntityAttribute(rs.getString("description")),
                        rs.getObject("validity_days", Integer.class)))
                .optional()
                .orElse(null);
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
