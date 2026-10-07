package az.cafeloyalty.security.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.shared.id.Ids;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Admin hesabları, bərpa kodları və şifrə bərpası tokenləri (realm-ə görə şirkət və ya platforma bazasında).
 * Bütün sorğular parametrlidir; cədvəl adları {@link AdminRealm}-dakı sabitlərdir.
 */
public class AdminAccountStore {

    private final AdminRealm realm;
    private final JdbcClient jdbc;
    private final String users;

    AdminAccountStore(AdminRealm realm, JdbcClient jdbc) {
        this.realm = realm;
        this.jdbc = jdbc;
        this.users = realm.usersTable();
    }

    public Optional<AdminAccount> findByEmail(String email) {
        return jdbc.sql("SELECT * FROM " + users + " WHERE email = :email AND status <> 'DELETED'")
                .param("email", normalizeEmail(email))
                .query(AdminAccountStore::map)
                .optional();
    }

    public Optional<AdminAccount> findById(UUID id) {
        return jdbc.sql("SELECT * FROM " + users + " WHERE id = :id").param("id", id).query(AdminAccountStore::map).optional();
    }

    /** Yeni hesab (dəvətin qəbulu, seed, CLI). */
    public UUID insert(String email, String passwordHash, String displayName, Language language,
                       AdminAccount.AccountStatus status, Instant now) {
        UUID id = Ids.newId();
        jdbc.sql("INSERT INTO " + users + """
                         (id, email, password_hash, display_name, preferred_language, status, password_changed_at,
                          created_at, updated_at)
                        VALUES (:id, :email, :hash, :name, :lang, :status, :now, :now, :now)""")
                .param("id", id)
                .param("email", normalizeEmail(email))
                .param("hash", passwordHash)
                .param("name", displayName)
                .param("lang", language.tag())
                .param("status", status.name())
                .param("now", ts(now))
                .update();
        return id;
    }

    /** Uğursuz cəhdlərin sayını atomik artırır və yeni dəyəri qaytarır. */
    public int incrementFailedLogins(UUID id, Instant now) {
        return jdbc.sql("UPDATE " + users + """
                         SET failed_login_count = failed_login_count + 1, updated_at = :now
                        WHERE id = :id RETURNING failed_login_count""")
                .param("id", id)
                .param("now", ts(now))
                .query(Integer.class)
                .single();
    }

    public void lockUntil(UUID id, Instant until) {
        jdbc.sql("UPDATE " + users + " SET locked_until = :until WHERE id = :id")
                .param("until", ts(until)).param("id", id).update();
    }

    public void recordSuccessfulLogin(UUID id, Instant now) {
        jdbc.sql("UPDATE " + users + """
                         SET failed_login_count = 0, locked_until = NULL, last_login_at = :now, updated_at = :now
                        WHERE id = :id""")
                .param("id", id).param("now", ts(now)).update();
    }

    public void updatePassword(UUID id, String passwordHash, Instant now) {
        jdbc.sql("UPDATE " + users + """
                         SET password_hash = :hash, password_changed_at = :now, failed_login_count = 0,
                             locked_until = NULL, updated_at = :now, version = version + 1
                        WHERE id = :id""")
                .param("hash", passwordHash).param("id", id).param("now", ts(now)).update();
    }

    public void updatePasswordHash(UUID id, String passwordHash) {
        jdbc.sql("UPDATE " + users + " SET password_hash = :hash WHERE id = :id")
                .param("hash", passwordHash).param("id", id).update();
    }

    /** Hələ təsdiqlənməmiş TOTP sirri (qeydiyyat zamanı). */
    public void storePendingTotp(UUID id, byte[] secretEnc, Instant now) {
        jdbc.sql("UPDATE " + users + """
                         SET totp_secret_enc = :secret, totp_enabled_at = NULL, totp_last_used_step = NULL, totp_disabled_at = NULL,
                             updated_at = :now
                        WHERE id = :id AND totp_enabled_at IS NULL""")
                .param("secret", secretEnc).param("id", id).param("now", ts(now)).update();
    }

    public void enableTotp(UUID id, long usedStep, Instant now) {
        jdbc.sql("UPDATE " + users + """
                         SET totp_enabled_at = :now, totp_last_used_step = :step, totp_disabled_at = NULL, updated_at = :now
                        WHERE id = :id AND totp_secret_enc IS NOT NULL""")
                .param("step", usedStep).param("id", id).param("now", ts(now)).update();
    }

    /**
     * TOTP addımını atomik qeyd edir: yalnız əvvəlkindən böyük addım qəbul olunur. Paralel iki sorğu eyni kodu
     * göndərsə, yalnız biri uğurlu olur (kodun təkrar istifadəsinin qarşısı).
     */
    public boolean advanceTotpStep(UUID id, long step) {
        return jdbc.sql("UPDATE " + users + """
                         SET totp_last_used_step = :step
                        WHERE id = :id AND (totp_last_used_step IS NULL OR totp_last_used_step < :step)""")
                .param("step", step).param("id", id).update() == 1;
    }

    public void resetTotp(UUID id, Instant now) {
        jdbc.sql("UPDATE " + users + """
                         SET totp_secret_enc = NULL, totp_enabled_at = NULL, totp_last_used_step = NULL, totp_disabled_at = NULL,
                             updated_at = :now
                        WHERE id = :id""")
                .param("id", id).param("now", ts(now)).update();
        jdbc.sql("DELETE FROM " + realm.recoveryCodesTable() + " WHERE " + realm.userIdColumn() + " = :id")
                .param("id", id).update();
    }

    /** 2FA-nın müvəqqəti söndürülməsi: sirr və bərpa kodları saxlanılır. */
    public void pauseTotp(UUID id, Instant now) {
        jdbc.sql("UPDATE " + users + """
                         SET totp_enabled_at = NULL, totp_disabled_at = :now, updated_at = :now
                        WHERE id = :id AND totp_enabled_at IS NOT NULL""")
                .param("id", id).param("now", ts(now)).update();
    }

    /** Yumşaq silinmə: sətir qalır, status DELETED olur və email yeni hesab üçün boşalır. */
    public void softDelete(UUID id, UUID deletedBy, Instant now) {
        jdbc.sql("UPDATE " + users + """
                         SET status = 'DELETED', deleted_at = :now, deleted_by = :by, updated_at = :now,
                             version = version + 1
                        WHERE id = :id AND status <> 'DELETED'""")
                .param("id", id).param("by", deletedBy).param("now", ts(now)).update();
    }

    public void updateProfile(UUID id, String displayName, Language language, Instant now) {
        jdbc.sql("UPDATE " + users + """
                         SET display_name = COALESCE(:name, display_name),
                             preferred_language = COALESCE(:lang, preferred_language), updated_at = :now
                        WHERE id = :id""")
                .param("name", displayName)
                .param("lang", language == null ? null : language.tag())
                .param("id", id)
                .param("now", ts(now))
                .update();
    }

    public void updateStatus(UUID id, AdminAccount.AccountStatus status, Instant now) {
        jdbc.sql("UPDATE " + users + " SET status = :status, updated_at = :now WHERE id = :id")
                .param("status", status.name()).param("id", id).param("now", ts(now)).update();
    }

    // --- Bərpa kodları ---

    public void replaceRecoveryCodes(UUID userId, List<byte[]> codeHashes, Instant now) {
        jdbc.sql("DELETE FROM " + realm.recoveryCodesTable() + " WHERE " + realm.userIdColumn() + " = :id")
                .param("id", userId).update();
        for (byte[] hash : codeHashes) {
            jdbc.sql("INSERT INTO " + realm.recoveryCodesTable() + " (id, " + realm.userIdColumn()
                            + ", code_hash, created_at) VALUES (:id, :userId, :hash, :now)")
                    .param("id", Ids.newId()).param("userId", userId).param("hash", hash).param("now", ts(now))
                    .update();
        }
    }

    /** Bərpa kodunu atomik istifadə edir (hər kod bir dəfə). */
    public boolean useRecoveryCode(UUID userId, byte[] codeHash, Instant now) {
        return jdbc.sql("UPDATE " + realm.recoveryCodesTable() + " SET used_at = :now WHERE "
                        + realm.userIdColumn() + " = :userId AND code_hash = :hash AND used_at IS NULL")
                .param("now", ts(now)).param("userId", userId).param("hash", codeHash).update() == 1;
    }

    public int remainingRecoveryCodes(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM " + realm.recoveryCodesTable() + " WHERE " + realm.userIdColumn()
                        + " = :userId AND used_at IS NULL")
                .param("userId", userId).query(Integer.class).single();
    }

    // --- Şifrə bərpası tokenləri ---

    public void createResetToken(UUID userId, byte[] tokenHash, Instant expiresAt, String ip, Instant now) {
        // Əvvəlki istifadə olunmamış tokenlər etibarsız olur — yalnız sonuncu link işləyir
        jdbc.sql("UPDATE " + realm.resetTokensTable() + " SET used_at = :now WHERE " + realm.userIdColumn()
                        + " = :userId AND used_at IS NULL")
                .param("now", ts(now)).param("userId", userId).update();
        jdbc.sql("INSERT INTO " + realm.resetTokensTable() + " (id, " + realm.userIdColumn()
                        + ", token_hash, expires_at, requested_ip, created_at)"
                        + " VALUES (:id, :userId, :hash, :expiresAt, CAST(:ip AS inet), :now)")
                .param("id", Ids.newId())
                .param("userId", userId)
                .param("hash", tokenHash)
                .param("expiresAt", ts(expiresAt))
                .param("ip", ip)
                .param("now", ts(now))
                .update();
    }

    /** Tokeni atomik istifadə edir; etibarlıdırsa istifadəçinin ID-sini qaytarır. */
    public Optional<UUID> consumeResetToken(byte[] tokenHash, Instant now) {
        return jdbc.sql("UPDATE " + realm.resetTokensTable() + " SET used_at = :now"
                        + " WHERE token_hash = :hash AND used_at IS NULL AND expires_at > :now"
                        + " RETURNING " + realm.userIdColumn())
                .param("now", ts(now)).param("hash", tokenHash)
                .query(UUID.class)
                .optional();
    }

    // --- Rollar ---

    /** İşçinin rolları (şirkət bazasından); platforma adminləri həmişə SUPER_ADMIN-dir. */
    public Set<RoleGrant> loadRoles(UUID userId) {
        if (realm == AdminRealm.PLATFORM) {
            return Set.of(new RoleGrant(AdminRole.SUPER_ADMIN, null));
        }
        return Set.copyOf(jdbc.sql("SELECT role, branch_id FROM staff_roles WHERE staff_user_id = :id")
                .param("id", userId)
                .query((rs, n) -> new RoleGrant(AdminRole.valueOf(rs.getString("role")), rs.getObject("branch_id", UUID.class)))
                .list());
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static AdminAccount map(ResultSet rs, int rowNum) throws SQLException {
        long stepValue = rs.getLong("totp_last_used_step");
        Long step = rs.wasNull() ? null : stepValue;
        return new AdminAccount(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                Language.fromTag(rs.getString("preferred_language")).orElse(Language.DEFAULT),
                AdminAccount.AccountStatus.valueOf(rs.getString("status")),
                rs.getBytes("totp_secret_enc"),
                instant(rs, "totp_enabled_at"),
                step,
                rs.getInt("failed_login_count"),
                instant(rs, "locked_until"),
                instant(rs, "last_login_at"),
                instant(rs, "totp_disabled_at"));
    }

    static Set<AdminRole> roles(Set<RoleGrant> grants) {
        Set<AdminRole> roles = EnumSet.noneOf(AdminRole.class);
        grants.forEach(grant -> roles.add(grant.role()));
        return roles;
    }
}
