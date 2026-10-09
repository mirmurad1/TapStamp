package az.tapstamp.security.admin;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import az.tapstamp.security.admin.repository.AdminAccountMapper;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.id.Ids;

/**
 * Admin hesabları, bərpa kodları və şifrə bərpası tokenləri — realm-ə görə şirkət və ya platforma bazasında. SQL
 * {@link AdminAccountMapper}-dədir; bu sinif realm-ə aid qaydaları (email normallaşdırması, platforma adminlərinin rolu,
 * çoxaddımlı əməliyyatlar) saxlayır.
 */
public class AdminAccountStore {

    private final AdminRealm realm;
    private final AdminAccountMapper accounts;

    AdminAccountStore(AdminRealm realm, AdminAccountMapper accounts) {
        this.realm = realm;
        this.accounts = accounts;
    }

    public Optional<AdminAccount> findByEmail(String email) {
        return accounts.findByEmail(normalizeEmail(email));
    }

    public Optional<AdminAccount> findById(UUID id) {
        return accounts.findById(id);
    }

    /** Yeni hesab (dəvətin qəbulu, seed, CLI). */
    public UUID insert(String email, String passwordHash, String displayName, Language language,
                       AdminAccount.AccountStatus status, Instant now) {
        UUID id = Ids.newId();
        accounts.insert(id, normalizeEmail(email), passwordHash, displayName, language, status, now);
        return id;
    }

    /** Uğursuz cəhdlərin sayını atomik artırır və yeni dəyəri qaytarır. */
    public int incrementFailedLogins(UUID id, Instant now) {
        return accounts.incrementFailedLogins(id, now);
    }

    public void lockUntil(UUID id, Instant until) {
        accounts.lockUntil(id, until);
    }

    public void recordSuccessfulLogin(UUID id, Instant now) {
        accounts.recordSuccessfulLogin(id, now);
    }

    public void updatePassword(UUID id, String passwordHash, Instant now) {
        accounts.updatePassword(id, passwordHash, now);
    }

    public void updatePasswordHash(UUID id, String passwordHash) {
        accounts.updatePasswordHash(id, passwordHash);
    }

    /** Hələ təsdiqlənməmiş TOTP sirri (qeydiyyat zamanı). */
    public void storePendingTotp(UUID id, byte[] secretEnc, Instant now) {
        accounts.storePendingTotp(id, secretEnc, now);
    }

    public void enableTotp(UUID id, long usedStep, Instant now) {
        accounts.enableTotp(id, usedStep, now);
    }

    /**
     * TOTP addımını atomik qeyd edir: yalnız əvvəlkindən böyük addım qəbul olunur. Paralel iki sorğu eyni kodu
     * göndərsə, yalnız biri uğurlu olur (kodun təkrar istifadəsinin qarşısı).
     */
    public boolean advanceTotpStep(UUID id, long step) {
        return accounts.advanceTotpStep(id, step) == 1;
    }

    /** 2FA tamamilə silinir: sirr və bərpa kodları. */
    public void resetTotp(UUID id, Instant now) {
        accounts.clearTotp(id, now);
        accounts.deleteRecoveryCodes(id);
    }

    /** 2FA-nın müvəqqəti söndürülməsi: sirr və bərpa kodları saxlanılır. */
    public void pauseTotp(UUID id, Instant now) {
        accounts.pauseTotp(id, now);
    }

    /** Yumşaq silinmə: sətir qalır, status DELETED olur və email yeni hesab üçün boşalır. */
    public void softDelete(UUID id, UUID deletedBy, Instant now) {
        accounts.softDelete(id, deletedBy, now);
    }

    public void updateProfile(UUID id, String displayName, Language language, Instant now) {
        accounts.updateProfile(id, displayName, language, now);
    }

    public void updateStatus(UUID id, AdminAccount.AccountStatus status, Instant now) {
        accounts.updateStatus(id, status, now);
    }

    // --- Bərpa kodları ---

    public void replaceRecoveryCodes(UUID userId, List<byte[]> codeHashes, Instant now) {
        accounts.deleteRecoveryCodes(userId);
        for (byte[] hash : codeHashes) {
            accounts.insertRecoveryCode(Ids.newId(), userId, hash, now);
        }
    }

    /** Bərpa kodunu atomik istifadə edir (hər kod bir dəfə). */
    public boolean useRecoveryCode(UUID userId, byte[] codeHash, Instant now) {
        return accounts.useRecoveryCode(userId, codeHash, now) == 1;
    }

    public int remainingRecoveryCodes(UUID userId) {
        return accounts.remainingRecoveryCodes(userId);
    }

    // --- Şifrə bərpası tokenləri ---

    /** Yeni token; əvvəlki istifadə olunmamış tokenlər etibarsız olur — yalnız sonuncu link işləyir. */
    public void createResetToken(UUID userId, byte[] tokenHash, Instant expiresAt, String ip, Instant now) {
        accounts.invalidateResetTokens(userId, now);
        accounts.insertResetToken(Ids.newId(), userId, tokenHash, expiresAt, ip, now);
    }

    /** Tokeni atomik istifadə edir; etibarlıdırsa istifadəçinin ID-sini qaytarır. */
    public Optional<UUID> consumeResetToken(byte[] tokenHash, Instant now) {
        return accounts.consumeResetToken(tokenHash, now);
    }

    // --- Rollar ---

    /** İşçinin rolları (şirkət bazasından); platforma adminləri həmişə SUPER_ADMIN-dir. */
    public Set<RoleGrant> loadRoles(UUID userId) {
        if (realm == AdminRealm.PLATFORM) {
            return Set.of(new RoleGrant(AdminRole.SUPER_ADMIN, null));
        }
        return Set.copyOf(accounts.staffRoles(userId));
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
