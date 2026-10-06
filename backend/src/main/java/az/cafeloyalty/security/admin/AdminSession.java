package az.cafeloyalty.security.admin;

import java.time.Instant;
import java.util.UUID;

/** Admin sessiyası (bazada; token yalnız hash kimi saxlanılır). */
public record AdminSession(
        UUID id,
        UUID userId,
        State state,
        Instant createdAt,
        Instant lastActivityAt,
        Instant absoluteExpiresAt,
        Instant mfaVerifiedAt,
        Instant stepUpAt,
        int mfaFailedAttempts,
        String ip,
        String userAgent) {

    public enum State {
        /** Şifrə doğrudur, TOTP gözlənilir. */
        PENDING_MFA,
        /** OWNER/SUPER_ADMIN üçün TOTP qurulması məcburidir; yalnız qeydiyyat endpoint-ləri açıqdır. */
        MFA_ENROLLMENT,
        ACTIVE
    }
}
