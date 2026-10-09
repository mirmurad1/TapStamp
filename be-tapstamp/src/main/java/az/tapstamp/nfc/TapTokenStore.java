package az.tapstamp.nfc;

import static az.tapstamp.shared.persistence.SqlTime.instant;
import static az.tapstamp.shared.persistence.SqlTime.ts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * {@code tap_tokens}: uğurlu toxunuşdan sonra verilən birdəfəlik token (60 saniyə, brauzerə bağlı). Bazada yalnız
 * tokenin SHA-256 hash-i saxlanılır.
 */
@Component
public class TapTokenStore {

    public enum Status {
        ACTIVE,
        RESERVED,
        CONSUMED,
        REJECTED,
        EXPIRED
    }

    public record TapToken(UUID id, UUID branchId, UUID tagId, byte[] bindingHash, Status status, Instant expiresAt,
                           UUID reservedChallengeId, Instant reservedUntil, UUID consumedBy, Instant createdAt) {

        /** Vaxtı keçmiş ACTIVE/RESERVED token fon işi onu yeniləməmişdən əvvəl də EXPIRED sayılır. */
        public Status effectiveStatus(Instant now) {
            if (status == Status.ACTIVE && !expiresAt.isAfter(now)) {
                return Status.EXPIRED;
            }
            if (status == Status.RESERVED && (reservedUntil == null || !reservedUntil.isAfter(now))) {
                return Status.EXPIRED;
            }
            return status;
        }

        @Override
        public @NonNull String toString() {
            return "TapToken[id=" + id + ", status=" + status + "]";
        }
    }

    private static final String COLUMNS = """
            id, branch_id, tag_id, binding_hash, status, expires_at, reserved_challenge_id, reserved_until,
            consumed_by_customer_id, created_at""";

    private final JdbcClient jdbc;

    public TapTokenStore(@Qualifier("tenantJdbc") JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, byte[] tokenHash, UUID branchId, UUID tagId, UUID tapEventId, byte[] bindingHash,
                       Instant expiresAt, Instant now) {
        jdbc.sql("""
                        INSERT INTO tap_tokens (id, token_hash, branch_id, tag_id, tap_event_id, binding_hash, status,
                                                expires_at, created_at)
                        VALUES (:id, :hash, :branchId, :tagId, :eventId, :binding, 'ACTIVE', :expiresAt, :now)""")
                .param("id", id)
                .param("hash", tokenHash)
                .param("branchId", branchId)
                .param("tagId", tagId)
                .param("eventId", tapEventId)
                .param("binding", bindingHash)
                .param("expiresAt", ts(expiresAt))
                .param("now", ts(now))
                .update();
    }

    public Optional<TapToken> find(byte[] tokenHash) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM tap_tokens WHERE token_hash = :hash")
                .param("hash", tokenHash)
                .query(TapTokenStore::map)
                .optional();
    }

    /** Sətir kilidi: eyni tokenin paralel claim sorğularından yalnız biri möhür yaradır. */
    public Optional<TapToken> lock(byte[] tokenHash) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM tap_tokens WHERE token_hash = :hash FOR UPDATE")
                .param("hash", tokenHash)
                .query(TapTokenStore::map)
                .optional();
    }

    /**
     * Daxil olmamış istifadəçi kod istəyəndə token həmin OTP sorğusuna rezerv olunur (SMS-in gəlməsi 60 saniyədən uzun
     * çəkə bilər). Yalnız eyni brauzerdən, aktiv (və ya artıq rezerv olunmuş — kod təkrar istənilib) token üçün.
     */
    public Optional<UUID> reserve(byte[] tokenHash, byte[] bindingHash, UUID challengeId, Instant until, Instant now) {
        return jdbc.sql("""
                        UPDATE tap_tokens SET status = 'RESERVED', reserved_challenge_id = :challengeId,
                                              reserved_until = :until
                        WHERE token_hash = :hash AND binding_hash = :binding
                          AND ((status = 'ACTIVE' AND expires_at > :now) OR (status = 'RESERVED' AND reserved_until > :now))
                        RETURNING id""")
                .param("challengeId", challengeId)
                .param("until", ts(until))
                .param("hash", tokenHash)
                .param("binding", bindingHash)
                .param("now", ts(now))
                .query(UUID.class)
                .optional();
    }

    public void markConsumed(UUID id, UUID customerId, Instant now) {
        jdbc.sql("""
                        UPDATE tap_tokens SET status = 'CONSUMED', consumed_at = :now, consumed_by_customer_id = :customerId
                        WHERE id = :id""")
                .param("now", ts(now))
                .param("customerId", customerId)
                .param("id", id)
                .update();
    }

    public void markRejected(UUID id) {
        jdbc.sql("UPDATE tap_tokens SET status = 'REJECTED' WHERE id = :id").param("id", id).update();
    }

    /** Fon işi: vaxtı keçmiş tokenlər EXPIRED olur. */
    public int expireStale(Instant now) {
        return jdbc.sql("""
                        UPDATE tap_tokens SET status = 'EXPIRED'
                        WHERE (status = 'ACTIVE' AND expires_at <= :now)
                           OR (status = 'RESERVED' AND (reserved_until IS NULL OR reserved_until <= :now))""")
                .param("now", ts(now))
                .update();
    }

    private static TapToken map(ResultSet rs, int ignoredRowNum) throws SQLException {
        return new TapToken(
                rs.getObject("id", UUID.class),
                rs.getObject("branch_id", UUID.class),
                rs.getObject("tag_id", UUID.class),
                rs.getBytes("binding_hash"),
                Status.valueOf(rs.getString("status")),
                instant(rs, "expires_at"),
                rs.getObject("reserved_challenge_id", UUID.class),
                instant(rs, "reserved_until"),
                rs.getObject("consumed_by_customer_id", UUID.class),
                instant(rs, "created_at"));
    }
}
