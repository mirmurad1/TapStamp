package az.cafeloyalty.security.admin;

import static az.cafeloyalty.security.admin.AdminAccountStore.instant;
import static az.cafeloyalty.security.admin.AdminAccountStore.ts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.cafeloyalty.shared.id.Ids;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Admin sessiyaları: token yalnız SHA-256 hash kimi saxlanılır, ləğvetmə dərhal qüvvəyə minir. */
public class AdminSessionStore {

    private final AdminRealm realm;
    private final JdbcClient jdbc;
    private final String table;

    AdminSessionStore(AdminRealm realm, JdbcClient jdbc) {
        this.realm = realm;
        this.jdbc = jdbc;
        this.table = realm.sessionsTable();
    }

    public UUID create(UUID userId, byte[] tokenHash, AdminSession.State state, Instant now, Instant absoluteExpiresAt,
                       Instant mfaVerifiedAt, String ip, String userAgent) {
        UUID id = Ids.newId();
        jdbc.sql("INSERT INTO " + table + " (id, " + realm.userIdColumn() + ", token_hash, state, created_at,"
                        + " last_activity_at, absolute_expires_at, mfa_verified_at, ip, user_agent)"
                        + " VALUES (:id, :userId, :hash, :state, :now, :now, :expires, :mfa, CAST(:ip AS inet), :ua)")
                .param("id", id)
                .param("userId", userId)
                .param("hash", tokenHash)
                .param("state", state.name())
                .param("now", ts(now))
                .param("expires", ts(absoluteExpiresAt))
                .param("mfa", ts(mfaVerifiedAt))
                .param("ip", ip)
                .param("ua", truncate(userAgent))
                .update();
        return id;
    }

    public Optional<AdminSession> findActiveByTokenHash(byte[] tokenHash) {
        return jdbc.sql("SELECT * FROM " + table + " WHERE token_hash = :hash AND revoked_at IS NULL")
                .param("hash", tokenHash)
                .query(this::map)
                .optional();
    }

    /** Fəaliyyət vaxtını yeniləyir (hər sorğuda bazaya yazmamaq üçün ən çox dəqiqədə bir dəfə). */
    public void touch(UUID sessionId, Instant now) {
        jdbc.sql("UPDATE " + table + " SET last_activity_at = :now WHERE id = :id AND last_activity_at < :threshold")
                .param("now", ts(now))
                .param("threshold", ts(now.minusSeconds(60)))
                .param("id", sessionId)
                .update();
    }

    /** Vəziyyət dəyişəndə token yenilənir (session fixation-ın qarşısı). */
    public void rotate(UUID sessionId, byte[] newTokenHash, AdminSession.State state, Instant absoluteExpiresAt,
                       Instant mfaVerifiedAt, Instant now) {
        jdbc.sql("UPDATE " + table + """
                         SET token_hash = :hash, state = :state, absolute_expires_at = :expires,
                             mfa_verified_at = COALESCE(:mfa, mfa_verified_at), mfa_failed_attempts = 0,
                             last_activity_at = :now
                        WHERE id = :id""")
                .param("hash", newTokenHash)
                .param("state", state.name())
                .param("expires", ts(absoluteExpiresAt))
                .param("mfa", ts(mfaVerifiedAt))
                .param("now", ts(now))
                .param("id", sessionId)
                .update();
    }

    public int recordMfaFailure(UUID sessionId) {
        return jdbc.sql("UPDATE " + table + " SET mfa_failed_attempts = mfa_failed_attempts + 1 WHERE id = :id"
                        + " RETURNING mfa_failed_attempts")
                .param("id", sessionId)
                .query(Integer.class)
                .single();
    }

    public void markStepUp(UUID sessionId, Instant now) {
        jdbc.sql("UPDATE " + table + " SET step_up_at = :now WHERE id = :id")
                .param("now", ts(now)).param("id", sessionId).update();
    }

    public void revoke(UUID sessionId, String reason, Instant now) {
        jdbc.sql("UPDATE " + table + " SET revoked_at = :now, revoke_reason = :reason WHERE id = :id AND revoked_at IS NULL")
                .param("now", ts(now)).param("reason", reason).param("id", sessionId).update();
    }

    /** İstifadəçinin bütün sessiyalarını ləğv edir (məs. şifrə dəyişəndə), istəyə görə cari sessiyadan başqa. */
    public int revokeAll(UUID userId, String reason, Instant now, UUID exceptSessionId) {
        return jdbc.sql("UPDATE " + table + " SET revoked_at = :now, revoke_reason = :reason WHERE "
                        + realm.userIdColumn() + " = :userId AND revoked_at IS NULL"
                        + " AND (CAST(:except AS uuid) IS NULL OR id <> CAST(:except AS uuid))")
                .param("now", ts(now))
                .param("reason", reason)
                .param("userId", userId)
                .param("except", exceptSessionId)
                .update();
    }

    public List<AdminSession> listActive(UUID userId, Instant now) {
        return jdbc.sql("SELECT * FROM " + table + " WHERE " + realm.userIdColumn() + " = :userId"
                        + " AND revoked_at IS NULL AND absolute_expires_at > :now ORDER BY last_activity_at DESC")
                .param("userId", userId)
                .param("now", ts(now))
                .query(this::map)
                .list();
    }

    private AdminSession map(ResultSet rs, int rowNum) throws SQLException {
        return new AdminSession(
                rs.getObject("id", UUID.class),
                rs.getObject(realm.userIdColumn(), UUID.class),
                AdminSession.State.valueOf(rs.getString("state")),
                instant(rs, "created_at"),
                instant(rs, "last_activity_at"),
                instant(rs, "absolute_expires_at"),
                instant(rs, "mfa_verified_at"),
                instant(rs, "step_up_at"),
                rs.getInt("mfa_failed_attempts"),
                rs.getString("ip"),
                rs.getString("user_agent"));
    }

    private static String truncate(String userAgent) {
        return userAgent == null || userAgent.length() <= 512 ? userAgent : userAgent.substring(0, 512);
    }
}
