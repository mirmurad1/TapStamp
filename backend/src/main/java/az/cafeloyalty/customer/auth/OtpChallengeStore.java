package az.cafeloyalty.customer.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import az.cafeloyalty.shared.i18n.Language;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code otp_challenges} cədvəli (cari tenant-ın bazası). */
@Component
public class OtpChallengeStore {

    public enum Purpose {
        LOGIN,
        STEP_UP
    }

    public record NewChallenge(UUID id, byte[] phoneHash, byte[] phoneEnc, byte[] codeHash, Language language,
                               int maxAttempts, Purpose purpose, UUID customerId, String ip, Instant createdAt,
                               Instant expiresAt) {
    }

    /** Cəhd sayı artırılandan sonrakı vəziyyət (kod hələ yoxlanılmayıb). */
    public record Attempt(UUID id, byte[] codeHash, byte[] phoneHash, Language language, int attempts, int maxAttempts,
                          UUID customerId) {
    }

    public record State(String status, int attempts, int maxAttempts, Instant expiresAt, byte[] phoneHash) {
    }

    public record Registration(UUID challengeId, byte[] phoneHash, byte[] phoneEnc, Language language) {
    }

    private final JdbcClient jdbc;

    public OtpChallengeStore(@Qualifier("tenantJdbc") JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Instant> lastCreatedAt(byte[] phoneHash, Purpose purpose) {
        return jdbc.sql("""
                        SELECT created_at FROM otp_challenges WHERE phone_hash = :hash AND purpose = :purpose
                        ORDER BY created_at DESC LIMIT 1""")
                .param("hash", phoneHash)
                .param("purpose", purpose.name())
                .query((rs, n) -> instant(rs, "created_at"))
                .optional();
    }

    /** Eyni nömrə üçün əvvəlki aktiv kod etibarsız olur — yalnız sonuncu kod işləyir. */
    public void supersedeActive(byte[] phoneHash, Purpose purpose) {
        jdbc.sql("""
                        UPDATE otp_challenges SET status = 'SUPERSEDED'
                        WHERE phone_hash = :hash AND purpose = :purpose AND status = 'ACTIVE'""")
                .param("hash", phoneHash)
                .param("purpose", purpose.name())
                .update();
    }

    public void insert(NewChallenge c) {
        jdbc.sql("""
                        INSERT INTO otp_challenges (id, phone_hash, phone_enc, code_hash, language, attempts, max_attempts,
                                                    status, purpose, customer_id, ip, created_at, expires_at)
                        VALUES (:id, :phoneHash, :phoneEnc, :codeHash, :language, 0, :maxAttempts, 'ACTIVE', :purpose,
                                :customerId, CAST(:ip AS inet), :createdAt, :expiresAt)""")
                .param("id", c.id())
                .param("phoneHash", c.phoneHash())
                .param("phoneEnc", c.phoneEnc())
                .param("codeHash", c.codeHash())
                .param("language", c.language().tag())
                .param("maxAttempts", c.maxAttempts())
                .param("purpose", c.purpose().name())
                .param("customerId", c.customerId())
                .param("ip", c.ip())
                .param("createdAt", ts(c.createdAt()))
                .param("expiresAt", ts(c.expiresAt()))
                .update();
    }

    /**
     * Cəhd sayını atomik artırır. Yalnız aktiv, vaxtı keçməmiş və limiti dolmamış kod üçün işləyir — paralel sorğularla
     * cəhd limitini keçmək mümkün deyil (hər sorğu ayrıca sayılır, sətir kilidlənir).
     */
    public Optional<Attempt> attempt(UUID id, Purpose purpose, UUID customerId, Instant now) {
        return jdbc.sql("""
                        UPDATE otp_challenges SET attempts = attempts + 1
                        WHERE id = :id AND purpose = :purpose AND status = 'ACTIVE' AND attempts < max_attempts
                          AND expires_at > :now AND (CAST(:customerId AS uuid) IS NULL OR customer_id = CAST(:customerId AS uuid))
                        RETURNING id, code_hash, phone_hash, language, attempts, max_attempts, customer_id""")
                .param("id", id)
                .param("purpose", purpose.name())
                .param("customerId", customerId)
                .param("now", ts(now))
                .query((rs, n) -> new Attempt(
                        rs.getObject("id", UUID.class),
                        rs.getBytes("code_hash"),
                        rs.getBytes("phone_hash"),
                        Language.fromTag(rs.getString("language")).orElse(Language.DEFAULT),
                        rs.getInt("attempts"),
                        rs.getInt("max_attempts"),
                        rs.getObject("customer_id", UUID.class)))
                .optional();
    }

    public Optional<State> find(UUID id) {
        return jdbc.sql("SELECT status, attempts, max_attempts, expires_at, phone_hash FROM otp_challenges WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new State(rs.getString("status"), rs.getInt("attempts"), rs.getInt("max_attempts"),
                        instant(rs, "expires_at"), rs.getBytes("phone_hash")))
                .optional();
    }

    /** Yalnız bir dəfə: paralel iki düzgün cavabdan yalnız biri uğurlu olur. */
    public boolean markVerified(UUID id, Instant now) {
        return jdbc.sql("UPDATE otp_challenges SET status = 'VERIFIED', verified_at = :now WHERE id = :id AND status = 'ACTIVE'")
                .param("now", ts(now))
                .param("id", id)
                .update() == 1;
    }

    public void linkTapToken(UUID id, UUID tapTokenId) {
        jdbc.sql("UPDATE otp_challenges SET tap_token_id = :tokenId WHERE id = :id")
                .param("tokenId", tapTokenId)
                .param("id", id)
                .update();
    }

    public void markLocked(UUID id) {
        jdbc.sql("UPDATE otp_challenges SET status = 'LOCKED' WHERE id = :id AND status = 'ACTIVE'").param("id", id).update();
    }

    public void setRegistrationToken(UUID id, byte[] tokenHash, Instant expiresAt) {
        jdbc.sql("""
                        UPDATE otp_challenges SET registration_token_hash = :hash, registration_expires_at = :expiresAt
                        WHERE id = :id""")
                .param("hash", tokenHash)
                .param("expiresAt", ts(expiresAt))
                .param("id", id)
                .update();
    }

    /** Qeydiyyat tokenini atomik istifadə edir (bir dəfə). Tranzaksiya geri qaytarılarsa, token qüvvədə qalır. */
    public Optional<Registration> consumeRegistrationToken(byte[] tokenHash, Instant now) {
        return jdbc.sql("""
                        UPDATE otp_challenges SET registration_token_hash = NULL
                        WHERE registration_token_hash = :hash AND registration_expires_at > :now AND status = 'VERIFIED'
                        RETURNING id, phone_hash, phone_enc, language""")
                .param("hash", tokenHash)
                .param("now", ts(now))
                .query((rs, n) -> new Registration(rs.getObject("id", UUID.class), rs.getBytes("phone_hash"),
                        rs.getBytes("phone_enc"), Language.fromTag(rs.getString("language")).orElse(Language.DEFAULT)))
                .optional();
    }

    /** Hesab silinəndə: həmin nömrənin OTP izləri (şifrəli nömrə daxil) qalmır. */
    public int deleteForCustomer(UUID customerId, byte[] phoneHash) {
        return jdbc.sql("DELETE FROM otp_challenges WHERE customer_id = :customerId OR phone_hash = :phoneHash")
                .param("customerId", customerId)
                .param("phoneHash", phoneHash)
                .update();
    }

    static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
