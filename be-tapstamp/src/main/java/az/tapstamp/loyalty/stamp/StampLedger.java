package az.tapstamp.loyalty.stamp;

import static az.tapstamp.shared.persistence.SqlTime.instant;
import static az.tapstamp.shared.persistence.SqlTime.ts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.loyalty.reward.GiftCardService;
import az.tapstamp.shared.id.Ids;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Möhürlər, loyallıq hesabı və sui-istifadə qaydalarının oxunması. Möhürün karta yazılması ({@link #applyConfirmed})
 * hesab sətri kilidlənmiş halda aparılır: eyni müştərinin paralel sorğuları ardıcıl icra olunur.
 */
@Component
public class StampLedger {

    public enum StampStatus {
        PENDING,
        CONFIRMED,
        REJECTED,
        REVOKED
    }

    public record Stamp(UUID id, UUID branchId, UUID customerId, StampStatus status, UUID rewardId, Instant createdAt) {
    }

    /** Kartın vəziyyəti: cari möhürlər, qayda üzrə hədd (qayda yoxdursa 0) və bu möhürlə qazanılan hədiyyə kartı. */
    public record CardState(int currentStamps, int stampsRequired, UUID giftCardId) {
    }

    private final JdbcClient jdbc;
    private final GiftCardService giftCards;

    public StampLedger(@Qualifier("tenantJdbc") JdbcClient jdbc, GiftCardService giftCards) {
        this.jdbc = jdbc;
        this.giftCards = giftCards;
    }

    public StampPolicy policy(UUID branchId) {
        return jdbc.sql("""
                        SELECT min_interval_minutes, max_stamps_per_day, require_staff_approval, pending_timeout_minutes,
                               enforce_business_hours
                        FROM stamp_policies WHERE branch_id = :branchId OR branch_id IS NULL
                        ORDER BY branch_id NULLS LAST LIMIT 1""")
                .param("branchId", branchId)
                .query((rs, n) -> new StampPolicy(Duration.ofMinutes(rs.getInt("min_interval_minutes")),
                        rs.getInt("max_stamps_per_day"), rs.getBoolean("require_staff_approval"),
                        Duration.ofMinutes(rs.getInt("pending_timeout_minutes")),
                        rs.getBoolean("enforce_business_hours")))
                .optional()
                .orElse(StampPolicy.DEFAULT);
    }

    public Optional<String> businessHours(UUID branchId) {
        return jdbc.sql("SELECT business_hours::text FROM branches WHERE id = :id")
                .param("id", branchId)
                .query(String.class)
                .optional();
    }

    /** Hesab sətrini kilidləyir (yoxdursa yaradır — köhnə məlumat üçün). */
    public void lockAccount(UUID customerId, Instant now) {
        jdbc.sql("""
                        INSERT INTO loyalty_accounts (id, customer_id, created_at, updated_at)
                        VALUES (:id, :customerId, :now, :now) ON CONFLICT (customer_id) DO NOTHING""")
                .param("id", Ids.newId())
                .param("customerId", customerId)
                .param("now", ts(now))
                .update();
        jdbc.sql("SELECT id FROM loyalty_accounts WHERE customer_id = :customerId FOR UPDATE")
                .param("customerId", customerId)
                .query(UUID.class)
                .single();
    }

    /** NFC ilə alınmış son aktiv (PENDING və ya CONFIRMED) möhürün vaxtı — cooldown üçün. */
    public Optional<Instant> lastTapStampAt(UUID customerId) {
        return jdbc.sql("""
                        SELECT created_at FROM stamps
                        WHERE customer_id = :customerId AND source = 'NFC_TAP' AND status IN ('PENDING', 'CONFIRMED')
                        ORDER BY created_at DESC LIMIT 1""")
                .param("customerId", customerId)
                .query((rs, n) -> instant(rs, "created_at"))
                .optional();
    }

    public int tapStampsOn(UUID customerId, LocalDate localDate) {
        return jdbc.sql("""
                        SELECT count(*) FROM stamps
                        WHERE customer_id = :customerId AND local_date = :day AND source = 'NFC_TAP'
                          AND status IN ('PENDING', 'CONFIRMED')""")
                .param("customerId", customerId)
                .param("day", localDate)
                .query(Integer.class)
                .single();
    }

    public UUID insertTapStamp(UUID branchId, UUID customerId, UUID tagId, UUID tapTokenId, StampStatus status,
                               LocalDate localDate, Instant at) {
        UUID id = Ids.newId();
        jdbc.sql("""
                        INSERT INTO stamps (id, branch_id, customer_id, tag_id, tap_token_id, source, status, local_date,
                                            confirmed_at, created_at)
                        VALUES (:id, :branchId, :customerId, :tagId, :tokenId, 'NFC_TAP', :status, :day, :confirmedAt,
                                :at)""")
                .param("id", id)
                .param("branchId", branchId)
                .param("customerId", customerId)
                .param("tagId", tagId)
                .param("tokenId", tapTokenId)
                .param("status", status.name())
                .param("day", localDate)
                .param("confirmedAt", status == StampStatus.CONFIRMED ? ts(at) : null)
                .param("at", ts(at))
                .update();
        return id;
    }

    /** Admin tərəfindən əl ilə əlavə olunan təsdiqlənmiş möhür (səbəb və icraçı məcburidir). */
    public UUID insertManualStamp(UUID branchId, UUID customerId, UUID staffId, String reason, LocalDate localDate,
                                  Instant at) {
        UUID id = Ids.newId();
        jdbc.sql("""
                        INSERT INTO stamps (id, branch_id, customer_id, source, status, local_date, reason,
                                            created_by_staff_id, confirmed_at, confirmed_by, created_at)
                        VALUES (:id, :branchId, :customerId, 'MANUAL', 'CONFIRMED', :day, :reason, :staff, :at, :staff, :at)""")
                .param("id", id)
                .param("branchId", branchId)
                .param("customerId", customerId)
                .param("day", localDate)
                .param("reason", reason)
                .param("staff", staffId)
                .param("at", ts(at))
                .update();
        return id;
    }

    public Optional<Stamp> findByTapToken(UUID tapTokenId) {
        return jdbc.sql("SELECT id, branch_id, customer_id, status, reward_id, created_at FROM stamps WHERE tap_token_id = :id")
                .param("id", tapTokenId)
                .query((rs, n) -> mapStamp(rs))
                .optional();
    }

    public Optional<Stamp> lockStamp(UUID stampId) {
        return jdbc.sql("""
                        SELECT id, branch_id, customer_id, status, reward_id, created_at FROM stamps
                        WHERE id = :id FOR UPDATE""")
                .param("id", stampId)
                .query((rs, n) -> mapStamp(rs))
                .optional();
    }

    /**
     * Təsdiqlənmiş möhürü karta yazır (hesab artıq kilidlənib): sayğaclar artır; hədd dolanda hədiyyə kartı yaranır,
     * kart sıfırlanır və möhür həmin hədiyyəyə bağlanır.
     */
    public CardState applyConfirmed(UUID customerId, UUID stampId, Instant at) {
        Optional<GiftCardService.ActiveRule> rule = giftCards.activeRule();
        int current = jdbc.sql("""
                        UPDATE loyalty_accounts SET current_stamps = current_stamps + 1, total_stamps = total_stamps + 1,
                                                    last_stamp_at = :at, updated_at = :at, version = version + 1
                        WHERE customer_id = :customerId RETURNING current_stamps""")
                .param("at", ts(at))
                .param("customerId", customerId)
                .query(Integer.class)
                .single();
        if (rule.isEmpty()) {
            return new CardState(current, 0, null);
        }
        int required = rule.get().stampsRequired();
        if (current < required) {
            return new CardState(current, required, null);
        }
        UUID giftCardId = giftCards.issue(customerId, rule.get(), at);
        jdbc.sql("""
                        UPDATE loyalty_accounts SET current_stamps = current_stamps - :required,
                                                    rewards_earned = rewards_earned + 1
                        WHERE customer_id = :customerId""")
                .param("required", required)
                .param("customerId", customerId)
                .update();
        jdbc.sql("UPDATE stamps SET reward_id = :rewardId WHERE id = :id")
                .param("rewardId", giftCardId)
                .param("id", stampId)
                .update();
        return new CardState(current - required, required, giftCardId);
    }

    public CardState card(UUID customerId) {
        int required = giftCards.activeRule().map(GiftCardService.ActiveRule::stampsRequired).orElse(0);
        int current = jdbc.sql("SELECT current_stamps FROM loyalty_accounts WHERE customer_id = :customerId")
                .param("customerId", customerId)
                .query(Integer.class)
                .optional()
                .orElse(0);
        return new CardState(current, required, null);
    }

    public void touchCustomerVisit(UUID customerId, UUID branchId, Instant at) {
        jdbc.sql("""
                        UPDATE customers SET last_visit_at = GREATEST(COALESCE(last_visit_at, :at), :at),
                                             last_branch_id = :branchId
                        WHERE id = :id""")
                .param("at", ts(at))
                .param("branchId", branchId)
                .param("id", customerId)
                .update();
    }

    private static Stamp mapStamp(ResultSet rs) throws SQLException {
        return new Stamp(rs.getObject("id", UUID.class), rs.getObject("branch_id", UUID.class),
                rs.getObject("customer_id", UUID.class), StampStatus.valueOf(rs.getString("status")),
                rs.getObject("reward_id", UUID.class), instant(rs, "created_at"));
    }
}
