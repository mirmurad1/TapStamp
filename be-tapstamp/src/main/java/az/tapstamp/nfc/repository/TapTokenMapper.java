package az.tapstamp.nfc.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.persistence.TenantMapper;
import org.jspecify.annotations.NonNull;

/**
 * {@code tap_tokens}: uğurlu toxunuşdan sonra verilən birdəfəlik token (60 saniyə, brauzerə bağlı). Bazada yalnız
 * tokenin SHA-256 hash-i saxlanılır. SQL: {@code mapper/tenant/nfc/TapTokenMapper.xml}.
 */
@TenantMapper
public interface TapTokenMapper {

    enum Status {
        ACTIVE,
        RESERVED,
        CONSUMED,
        REJECTED,
        EXPIRED
    }

    record TapToken(UUID id, UUID branchId, UUID tagId, byte[] bindingHash, Status status, Instant expiresAt,
                    UUID reservedChallengeId, Instant reservedUntil, UUID consumedBy, Instant createdAt) {

        @Override
        public @NonNull String toString() {
            return "TapToken[id=" + id + ", status=" + status + "]";
        }
    }

    void insert(UUID id, byte[] tokenHash, UUID branchId, UUID tagId, UUID tapEventId, byte[] bindingHash,
                Instant expiresAt, Instant now);

    Optional<TapToken> find(byte[] tokenHash);

    /** Sətir kilidi: eyni tokenin paralel claim sorğularından yalnız biri möhür yaradır. */
    Optional<TapToken> lock(byte[] tokenHash);

    /**
     * Daxil olmamış istifadəçi kod istəyəndə token həmin OTP sorğusuna rezerv olunur (SMS-in gəlməsi 60 saniyədən uzun
     * çəkə bilər). Yalnız eyni brauzerdən, aktiv (və ya artıq rezerv olunmuş — kod təkrar istənilib) token üçün.
     */
    Optional<UUID> reserve(byte[] tokenHash, byte[] bindingHash, UUID challengeId, Instant until, Instant now);

    void markConsumed(UUID id, UUID customerId, Instant now);

    void markRejected(UUID id);

    /** Fon işi: vaxtı keçmiş tokenlər EXPIRED olur. */
    int expireStale(Instant now);
}
