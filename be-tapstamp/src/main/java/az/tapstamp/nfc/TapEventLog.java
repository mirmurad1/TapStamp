package az.tapstamp.nfc;

import static az.tapstamp.shared.persistence.SqlTime.ts;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.id.Ids;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Toxunuş jurnalı ({@code tap_events}): hər cəhd — qəbul olunan və rədd edilən — dəqiq səbəbi ilə. Cədvəl yalnız
 * əlavə olunandır (tətbiq rolunun UPDATE/DELETE hüququ yoxdur).
 */
@Component
public class TapEventLog {

    public enum Stage {
        VERIFY,
        CLAIM
    }

    /** Sorğunun mənbəyi: IP, brauzer identifikatorunun və User-Agent-in hash-ləri. */
    public record Source(String ip, byte[] browserIdHash, byte[] userAgentHash) {
    }

    /** {@code reason == null} — qəbul olunub, əks halda rədd edilib (dəqiq səbəb). */
    public record Event(Stage stage, UUID branchId, UUID tagId, String reason, Integer counter, Integer counterGap,
                        UUID customerId, UUID tapTokenId, UUID stampId, Source source) {

        public static Event of(Stage stage, UUID branchId, UUID tagId, String reason, Source source) {
            return new Event(stage, branchId, tagId, reason, null, null, null, null, null, source);
        }

        public Event withCounter(Integer value, Integer gap) {
            return new Event(stage, branchId, tagId, reason, value, gap, customerId, tapTokenId, stampId, source);
        }

        public Event withClaim(UUID customer, UUID token, UUID stamp) {
            return new Event(stage, branchId, tagId, reason, counter, counterGap, customer, token, stamp, source);
        }
    }

    private final JdbcClient jdbc;

    public TapEventLog(@Qualifier("tenantJdbc") JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID record(Event event, Instant at) {
        UUID id = Ids.newId();
        Source source = event.source();
        jdbc.sql("""
                        INSERT INTO tap_events (id, branch_id, tag_id, stage, outcome, reason, counter, counter_gap,
                                                customer_id, tap_token_id, stamp_id, ip, browser_id_hash,
                                                user_agent_hash, created_at)
                        VALUES (:id, :branchId, :tagId, :stage, :outcome, :reason, :counter, :counterGap, :customerId,
                                :tapTokenId, :stampId, CAST(:ip AS inet), :browserIdHash, :userAgentHash, :createdAt)""")
                .param("id", id)
                .param("branchId", event.branchId())
                .param("tagId", event.tagId())
                .param("stage", event.stage().name())
                .param("outcome", event.reason() == null ? "ACCEPTED" : "REJECTED")
                .param("reason", event.reason())
                .param("counter", event.counter())
                .param("counterGap", event.counterGap())
                .param("customerId", event.customerId())
                .param("tapTokenId", event.tapTokenId())
                .param("stampId", event.stampId())
                .param("ip", source == null ? null : source.ip())
                .param("browserIdHash", source == null ? null : source.browserIdHash())
                .param("userAgentHash", source == null ? null : source.userAgentHash())
                .param("createdAt", ts(at))
                .update();
        return id;
    }

    /** Tokenin bu müştəri üçün son rədd səbəbi (səhifə yenilənəndə eyni xəta göstərilsin). */
    public Optional<String> lastClaimRejection(UUID tapTokenId, UUID customerId) {
        return jdbc.sql("""
                        SELECT reason FROM tap_events
                        WHERE tap_token_id = :tokenId AND customer_id = :customerId AND stage = 'CLAIM'
                          AND outcome = 'REJECTED'
                        ORDER BY created_at DESC LIMIT 1""")
                .param("tokenId", tapTokenId)
                .param("customerId", customerId)
                .query(String.class)
                .optional();
    }
}
