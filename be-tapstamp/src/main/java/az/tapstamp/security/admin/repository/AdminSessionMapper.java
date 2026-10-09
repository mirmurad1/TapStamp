package az.tapstamp.security.admin.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.security.admin.AdminSession;

/**
 * Admin sessiyaları (hər iki realm): token yalnız SHA-256 hash kimi saxlanılır. Cədvəl adları fabrikin
 * dəyişənlərindəndir (bax: {@link AdminAccountMapper}). SQL: {@code mapper/admin/AdminSessionMapper.xml}.
 */
public interface AdminSessionMapper {

    void insert(UUID id, UUID userId, byte[] tokenHash, AdminSession.State state, Instant now,
                Instant absoluteExpiresAt, Instant mfaVerifiedAt, String ip, String userAgent);

    Optional<AdminSession> findActiveByTokenHash(byte[] tokenHash);

    void touch(UUID sessionId, Instant now, Instant threshold);

    void rotate(UUID sessionId, byte[] tokenHash, AdminSession.State state, Instant absoluteExpiresAt,
                Instant mfaVerifiedAt, Instant now);

    int recordMfaFailure(UUID sessionId);

    void markStepUp(UUID sessionId, Instant now);

    void revoke(UUID sessionId, String reason, Instant now);

    int revokeAll(UUID userId, String reason, Instant now, UUID exceptSessionId);

    List<AdminSession> listActive(UUID userId, Instant now);
}
