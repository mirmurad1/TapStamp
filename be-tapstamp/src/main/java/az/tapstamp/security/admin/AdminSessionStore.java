package az.tapstamp.security.admin;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.security.admin.repository.AdminSessionMapper;
import az.tapstamp.shared.id.Ids;

/**
 * Admin sessiyaları (realm-ə görə şirkət və ya platforma bazasında): token yalnız SHA-256 hash kimi saxlanılır,
 * ləğvetmə dərhal qüvvəyə minir. SQL {@link AdminSessionMapper}-dədir.
 */
public class AdminSessionStore {

    private final AdminSessionMapper sessions;

    AdminSessionStore(AdminSessionMapper sessions) {
        this.sessions = sessions;
    }

    public void create(UUID userId, byte[] tokenHash, AdminSession.State state, Instant now, Instant absoluteExpiresAt,
                       Instant mfaVerifiedAt, String ip, String userAgent) {
        sessions.insert(Ids.newId(), userId, tokenHash, state, now, absoluteExpiresAt, mfaVerifiedAt, ip,
                truncate(userAgent));
    }

    public Optional<AdminSession> findActiveByTokenHash(byte[] tokenHash) {
        return sessions.findActiveByTokenHash(tokenHash);
    }

    /** Fəaliyyət vaxtını yeniləyir (hər sorğuda bazaya yazmamaq üçün ən çox dəqiqədə bir dəfə). */
    public void touch(UUID sessionId, Instant now) {
        sessions.touch(sessionId, now, now.minusSeconds(60));
    }

    /** Vəziyyət dəyişəndə token yenilənir (session fixation-ın qarşısı). */
    public void rotate(UUID sessionId, byte[] newTokenHash, AdminSession.State state, Instant absoluteExpiresAt,
                       Instant mfaVerifiedAt, Instant now) {
        sessions.rotate(sessionId, newTokenHash, state, absoluteExpiresAt, mfaVerifiedAt, now);
    }

    public int recordMfaFailure(UUID sessionId) {
        return sessions.recordMfaFailure(sessionId);
    }

    public void markStepUp(UUID sessionId, Instant now) {
        sessions.markStepUp(sessionId, now);
    }

    public void revoke(UUID sessionId, String reason, Instant now) {
        sessions.revoke(sessionId, reason, now);
    }

    /** İstifadəçinin bütün sessiyalarını ləğv edir (məs. şifrə dəyişəndə), istəyə görə cari sessiyadan başqa. */
    public void revokeAll(UUID userId, String reason, Instant now, UUID exceptSessionId) {
        sessions.revokeAll(userId, reason, now, exceptSessionId);
    }

    public List<AdminSession> listActive(UUID userId, Instant now) {
        return sessions.listActive(userId, now);
    }

    private static String truncate(String userAgent) {
        return userAgent == null || userAgent.length() <= 512 ? userAgent : userAgent.substring(0, 512);
    }
}
