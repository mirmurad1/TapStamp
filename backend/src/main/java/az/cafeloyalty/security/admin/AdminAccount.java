package az.cafeloyalty.security.admin;

import java.time.Instant;
import java.util.UUID;

import az.cafeloyalty.shared.i18n.Language;

/** Admin hesabının bazadakı vəziyyəti. Şifrə hash-i və şifrəli TOTP sirri toString-də göstərilmir. */
public record AdminAccount(
        UUID id,
        String email,
        String passwordHash,
        String displayName,
        Language preferredLanguage,
        AccountStatus status,
        byte[] totpSecretEnc,
        Instant totpEnabledAt,
        Long totpLastUsedStep,
        int failedLoginCount,
        Instant lockedUntil,
        Instant lastLoginAt) {

    public enum AccountStatus {
        ACTIVE,
        DISABLED,
        INVITED
    }

    public boolean totpEnabled() {
        return totpEnabledAt != null && totpSecretEnc != null;
    }

    public boolean lockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    @Override
    public String toString() {
        return "AdminAccount[id=" + id + ", status=" + status + "]";
    }
}
