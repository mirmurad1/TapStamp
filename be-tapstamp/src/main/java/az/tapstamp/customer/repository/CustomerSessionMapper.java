package az.tapstamp.customer.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.persistence.TenantMapper;

/**
 * Müştəri sessiyaları və refresh tokenlərin rotasiya tarixçəsi. Tokenlər yalnız SHA-256 hash şəklində saxlanılır.
 * SQL: {@code mapper/tenant/customer/CustomerSessionMapper.xml}.
 */
@TenantMapper
public interface CustomerSessionMapper {

    /** Access token ilə tapılan sessiya (müştərinin statusu ilə birlikdə). */
    record ActiveSession(UUID sessionId, UUID customerId, Instant accessExpiresAt, Instant revokedAt,
                         Instant stepUpAt, Instant lastSeenAt, String customerStatus, Language language) {
    }

    /** Kilidlənmiş refresh token və onun sessiyası. */
    record RefreshRow(UUID tokenId, UUID sessionId, UUID customerId, Instant expiresAt, Instant consumedAt,
                      Instant sessionRevokedAt, Instant sessionRefreshExpiresAt, String customerStatus) {
    }

    record SessionView(UUID id, String deviceLabel, Instant createdAt, Instant lastSeenAt) {
    }

    void insertSessionRow(UUID id, UUID customerId, byte[] accessHash, Instant accessExpiresAt,
                          Instant refreshExpiresAt, String deviceLabel, String userAgent, String ip, Instant now);

    /** User-Agent 512 simvola qədər qısaldılır. */
    default void insertSession(UUID id, UUID customerId, byte[] accessHash, Instant accessExpiresAt,
                               Instant refreshExpiresAt, String deviceLabel, String userAgent, String ip, Instant now) {
        String ua = userAgent == null || userAgent.length() <= 512 ? userAgent : userAgent.substring(0, 512);
        insertSessionRow(id, customerId, accessHash, accessExpiresAt, refreshExpiresAt, deviceLabel, ua, ip, now);
    }

    void insertRefreshToken(UUID id, UUID sessionId, byte[] tokenHash, Instant issuedAt, Instant expiresAt);

    Optional<ActiveSession> findByAccessHash(byte[] accessHash);

    /** Refresh tokeni sətir kilidi ilə oxuyur: eyni tokenlə paralel iki yeniləmə ardıcıl işlənir. */
    Optional<RefreshRow> lockRefreshToken(byte[] tokenHash);

    void consumeRefreshToken(UUID tokenId, Instant now);

    /** Yeni access token; refresh müddəti sürüşür (hər yeniləmədə 180 gün). */
    void rotateAccess(UUID sessionId, byte[] accessHash, Instant accessExpiresAt, Instant refreshExpiresAt, String ip,
                      Instant now);

    /** Son aktivlik (cihazlar siyahısı üçün): bazaya ən çox dəqiqədə bir dəfə yazılır. */
    void touchSession(UUID sessionId, String ip, Instant now, Instant threshold);

    default void touch(UUID sessionId, String ip, Instant now) {
        touchSession(sessionId, ip, now, now.minusSeconds(60));
    }

    void markStepUp(UUID sessionId, Instant now);

    int revokeSession(UUID sessionId, String reason, Instant now);

    default boolean revoke(UUID sessionId, String reason, Instant now) {
        return revokeSession(sessionId, reason, now) == 1;
    }

    /** Müştərinin bütün sessiyaları (istəyə görə cari sessiyadan başqa). Ləğv olunan sessiyaların ID-ləri qaytarılır. */
    List<UUID> revokeAll(UUID customerId, String reason, Instant now, UUID exceptSessionId);

    List<SessionView> listActive(UUID customerId, Instant now);
}
