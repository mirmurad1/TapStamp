package az.cafeloyalty.customer.auth;

import static az.cafeloyalty.customer.auth.OtpChallengeStore.instant;
import static az.cafeloyalty.customer.auth.OtpChallengeStore.ts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.cafeloyalty.shared.i18n.Language;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Müştəri sessiyaları və refresh tokenlərin rotasiya tarixçəsi. Tokenlər yalnız SHA-256 hash şəklində saxlanılır.
 */
@Component
public class CustomerSessionStore {

    /** Access token ilə tapılan sessiya (müştərinin statusu ilə birlikdə). */
    public record ActiveSession(UUID sessionId, UUID customerId, Instant accessExpiresAt, Instant revokedAt,
                                Instant stepUpAt, Instant lastSeenAt, String customerStatus, Language language) {
    }

    /** Kilidlənmiş refresh token və onun sessiyası. */
    public record RefreshRow(UUID tokenId, UUID sessionId, UUID customerId, Instant expiresAt, Instant consumedAt,
                             Instant sessionRevokedAt, Instant sessionRefreshExpiresAt, String customerStatus) {
    }

    public record SessionView(UUID id, String deviceLabel, Instant createdAt, Instant lastSeenAt) {
    }

    private final JdbcClient jdbc;

    public CustomerSessionStore(@Qualifier("tenantJdbc") JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertSession(UUID id, UUID customerId, byte[] accessHash, Instant accessExpiresAt,
                              Instant refreshExpiresAt, String deviceLabel, String userAgent, String ip, Instant now) {
        jdbc.sql("""
                        INSERT INTO customer_sessions (id, customer_id, access_token_hash, access_expires_at,
                                                       refresh_expires_at, device_label, user_agent, created_ip, last_ip,
                                                       created_at, last_seen_at)
                        VALUES (:id, :customerId, :accessHash, :accessExpires, :refreshExpires, :deviceLabel, :ua,
                                CAST(:ip AS inet), CAST(:ip AS inet), :now, :now)""")
                .param("id", id)
                .param("customerId", customerId)
                .param("accessHash", accessHash)
                .param("accessExpires", ts(accessExpiresAt))
                .param("refreshExpires", ts(refreshExpiresAt))
                .param("deviceLabel", deviceLabel)
                .param("ua", truncate(userAgent))
                .param("ip", ip)
                .param("now", ts(now))
                .update();
    }

    public void insertRefreshToken(UUID id, UUID sessionId, byte[] tokenHash, Instant issuedAt, Instant expiresAt) {
        jdbc.sql("""
                        INSERT INTO customer_refresh_tokens (id, session_id, token_hash, issued_at, expires_at)
                        VALUES (:id, :sessionId, :hash, :issuedAt, :expiresAt)""")
                .param("id", id)
                .param("sessionId", sessionId)
                .param("hash", tokenHash)
                .param("issuedAt", ts(issuedAt))
                .param("expiresAt", ts(expiresAt))
                .update();
    }

    public Optional<ActiveSession> findByAccessHash(byte[] accessHash) {
        return jdbc.sql("""
                        SELECT s.id, s.customer_id, s.access_expires_at, s.revoked_at, s.step_up_at, s.last_seen_at,
                               c.status, c.preferred_language
                        FROM customer_sessions s JOIN customers c ON c.id = s.customer_id
                        WHERE s.access_token_hash = :hash""")
                .param("hash", accessHash)
                .query((rs, n) -> new ActiveSession(
                        rs.getObject("id", UUID.class),
                        rs.getObject("customer_id", UUID.class),
                        instant(rs, "access_expires_at"),
                        instant(rs, "revoked_at"),
                        instant(rs, "step_up_at"),
                        instant(rs, "last_seen_at"),
                        rs.getString("status"),
                        Language.fromTag(rs.getString("preferred_language")).orElse(Language.DEFAULT)))
                .optional();
    }

    /** Refresh tokeni sətir kilidi ilə oxuyur: eyni tokenlə paralel iki yeniləmə ardıcıl işlənir. */
    public Optional<RefreshRow> lockRefreshToken(byte[] tokenHash) {
        return jdbc.sql("""
                        SELECT rt.id, rt.session_id, s.customer_id, rt.expires_at, rt.consumed_at, s.revoked_at,
                               s.refresh_expires_at, c.status
                        FROM customer_refresh_tokens rt
                        JOIN customer_sessions s ON s.id = rt.session_id
                        JOIN customers c ON c.id = s.customer_id
                        WHERE rt.token_hash = :hash
                        FOR UPDATE OF rt, s""")
                .param("hash", tokenHash)
                .query((rs, n) -> new RefreshRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("session_id", UUID.class),
                        rs.getObject("customer_id", UUID.class),
                        instant(rs, "expires_at"),
                        instant(rs, "consumed_at"),
                        instant(rs, "revoked_at"),
                        instant(rs, "refresh_expires_at"),
                        rs.getString("status")))
                .optional();
    }

    public void consumeRefreshToken(UUID tokenId, Instant now) {
        jdbc.sql("UPDATE customer_refresh_tokens SET consumed_at = :now WHERE id = :id")
                .param("now", ts(now)).param("id", tokenId).update();
    }

    /** Yeni access token; refresh müddəti sürüşür (hər yeniləmədə 180 gün). */
    public void rotateAccess(UUID sessionId, byte[] accessHash, Instant accessExpiresAt, Instant refreshExpiresAt,
                             String ip, Instant now) {
        jdbc.sql("""
                        UPDATE customer_sessions
                           SET access_token_hash = :hash, access_expires_at = :accessExpires,
                               refresh_expires_at = :refreshExpires, last_seen_at = :now, last_ip = CAST(:ip AS inet)
                         WHERE id = :id""")
                .param("hash", accessHash)
                .param("accessExpires", ts(accessExpiresAt))
                .param("refreshExpires", ts(refreshExpiresAt))
                .param("now", ts(now))
                .param("ip", ip)
                .param("id", sessionId)
                .update();
    }

    /** Son aktivlik (cihazlar siyahısı üçün): bazaya ən çox dəqiqədə bir dəfə yazılır. */
    public void touch(UUID sessionId, String ip, Instant now) {
        jdbc.sql("""
                        UPDATE customer_sessions SET last_seen_at = :now, last_ip = CAST(:ip AS inet)
                        WHERE id = :id AND last_seen_at < :threshold""")
                .param("now", ts(now))
                .param("ip", ip)
                .param("threshold", ts(now.minusSeconds(60)))
                .param("id", sessionId)
                .update();
    }

    public void markStepUp(UUID sessionId, Instant now) {
        jdbc.sql("UPDATE customer_sessions SET step_up_at = :now WHERE id = :id")
                .param("now", ts(now)).param("id", sessionId).update();
    }

    public boolean revoke(UUID sessionId, String reason, Instant now) {
        return jdbc.sql("""
                        UPDATE customer_sessions SET revoked_at = :now, revoke_reason = :reason
                        WHERE id = :id AND revoked_at IS NULL""")
                .param("now", ts(now)).param("reason", reason).param("id", sessionId).update() == 1;
    }

    /** Müştərinin bütün sessiyaları (istəyə görə cari sessiyadan başqa). Ləğv olunan sessiyaların ID-ləri qaytarılır. */
    public List<UUID> revokeAll(UUID customerId, String reason, Instant now, UUID exceptSessionId) {
        return jdbc.sql("""
                        UPDATE customer_sessions SET revoked_at = :now, revoke_reason = :reason
                        WHERE customer_id = :customerId AND revoked_at IS NULL
                          AND (CAST(:except AS uuid) IS NULL OR id <> CAST(:except AS uuid))
                        RETURNING id""")
                .param("now", ts(now))
                .param("reason", reason)
                .param("customerId", customerId)
                .param("except", exceptSessionId)
                .query(UUID.class)
                .list();
    }

    public List<SessionView> listActive(UUID customerId, Instant now) {
        return jdbc.sql("""
                        SELECT id, device_label, created_at, last_seen_at FROM customer_sessions
                        WHERE customer_id = :customerId AND revoked_at IS NULL AND refresh_expires_at > :now
                        ORDER BY last_seen_at DESC""")
                .param("customerId", customerId)
                .param("now", ts(now))
                .query((rs, n) -> new SessionView(rs.getObject("id", UUID.class), rs.getString("device_label"),
                        instant(rs, "created_at"), instant(rs, "last_seen_at")))
                .list();
    }

    private static String truncate(String userAgent) {
        return userAgent == null || userAgent.length() <= 512 ? userAgent : userAgent.substring(0, 512);
    }
}
