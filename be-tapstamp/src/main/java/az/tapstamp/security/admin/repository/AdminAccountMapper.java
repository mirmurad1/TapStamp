package az.tapstamp.security.admin.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.security.admin.AdminAccount;
import az.tapstamp.security.admin.RoleGrant;
import az.tapstamp.shared.i18n.Language;

/**
 * Admin hesabları, bərpa kodları və şifrə bərpası tokenləri — hər iki realm üçün eyni SQL. Cədvəl adları
 * {@code SqlSessionFactory}-nin dəyişənlərindən gəlir ({@code ${admin.users}} və s.): şirkət bazasında işçilər,
 * platforma bazasında platforma adminləri. İki nüsxəsi var (bax: {@code AdminRealms}).
 * SQL: {@code mapper/admin/AdminAccountMapper.xml}.
 */
public interface AdminAccountMapper {

    Optional<AdminAccount> findByEmail(String email);

    Optional<AdminAccount> findById(UUID id);

    void insert(UUID id, String email, String passwordHash, String displayName, Language language,
                AdminAccount.AccountStatus status, Instant now);

    /** Uğursuz cəhdlərin sayını atomik artırır və yeni dəyəri qaytarır. */
    int incrementFailedLogins(UUID id, Instant now);

    void lockUntil(UUID id, Instant until);

    void recordSuccessfulLogin(UUID id, Instant now);

    void updatePassword(UUID id, String passwordHash, Instant now);

    void updatePasswordHash(UUID id, String passwordHash);

    void storePendingTotp(UUID id, byte[] secretEnc, Instant now);

    void enableTotp(UUID id, long usedStep, Instant now);

    /** Yalnız əvvəlkindən böyük addım qəbul olunur; dəyişən sətirlərin sayı qaytarılır. */
    int advanceTotpStep(UUID id, long step);

    void clearTotp(UUID id, Instant now);

    void pauseTotp(UUID id, Instant now);

    void softDelete(UUID id, UUID deletedBy, Instant now);

    void updateProfile(UUID id, String displayName, Language language, Instant now);

    void updateStatus(UUID id, AdminAccount.AccountStatus status, Instant now);

    void deleteRecoveryCodes(UUID userId);

    void insertRecoveryCode(UUID id, UUID userId, byte[] codeHash, Instant now);

    int useRecoveryCode(UUID userId, byte[] codeHash, Instant now);

    int remainingRecoveryCodes(UUID userId);

    /** Əvvəlki istifadə olunmamış tokenlər etibarsız olur — yalnız sonuncu link işləyir. */
    void invalidateResetTokens(UUID userId, Instant now);

    void insertResetToken(UUID id, UUID userId, byte[] tokenHash, Instant expiresAt, String ip, Instant now);

    /** Tokeni atomik istifadə edir; etibarlıdırsa istifadəçinin ID-sini qaytarır. */
    Optional<UUID> consumeResetToken(byte[] tokenHash, Instant now);

    /** İşçinin rolları (yalnız şirkət bazasında; platforma adminləri həmişə SUPER_ADMIN-dir). */
    List<RoleGrant> staffRoles(UUID userId);
}
