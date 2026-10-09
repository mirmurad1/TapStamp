package az.tapstamp.customer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.id.Ids;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Müştərilər (cari tenant-ın bazası). Telefon şifrəli ({@code phone_enc}), axtarış HMAC ilə ({@code phone_hash}). */
@Component
public class CustomerStore {

    public enum Status {
        ACTIVE,
        BLOCKED,
        DELETED
    }

    public record Customer(UUID id, String name, byte[] phoneEnc, byte[] phoneHash, String phoneMasked,
                           Language language, Status status, Instant registeredAt) {

        @Override
        public @NonNull String toString() {
            return "Customer[id=" + id + ", status=" + status + "]";
        }
    }

    private final JdbcClient jdbc;

    public CustomerStore(@Qualifier("tenantJdbc") JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Customer> findById(UUID id) {
        return jdbc.sql("SELECT * FROM customers WHERE id = :id").param("id", id).query(CustomerStore::map).optional();
    }

    /** Silinmiş müştərinin hash-i NULL olduğu üçün o, nömrə ilə tapılmır. */
    public Optional<Customer> findByPhoneHash(byte[] phoneHash) {
        return jdbc.sql("SELECT * FROM customers WHERE phone_hash = :hash")
                .param("hash", phoneHash)
                .query(CustomerStore::map)
                .optional();
    }

    public UUID insert(UUID id, byte[] phoneEnc, byte[] phoneHash, String phoneMasked, String name, Language language,
                       Instant now) {
        jdbc.sql("""
                        INSERT INTO customers (id, phone_enc, phone_hash, phone_masked, name, preferred_language, status,
                                               registered_at, created_at, updated_at)
                        VALUES (:id, :phoneEnc, :phoneHash, :phoneMasked, :name, :language, 'ACTIVE', :now, :now, :now)""")
                .param("id", id)
                .param("phoneEnc", phoneEnc)
                .param("phoneHash", phoneHash)
                .param("phoneMasked", phoneMasked)
                .param("name", name)
                .param("language", language.tag())
                .param("now", ts(now))
                .update();
        jdbc.sql("""
                        INSERT INTO loyalty_accounts (id, customer_id, created_at, updated_at)
                        VALUES (:id, :customerId, :now, :now)""")
                .param("id", Ids.newId())
                .param("customerId", id)
                .param("now", ts(now))
                .update();
        return id;
    }

    /** {@code name}: null — dəyişmir, boş sətir — silinir. */
    public void updateProfile(UUID id, String name, Language language, Instant now) {
        jdbc.sql("""
                        UPDATE customers
                           SET name = CASE WHEN CAST(:keepName AS boolean) THEN name ELSE :name END,
                               preferred_language = COALESCE(:language, preferred_language),
                               updated_at = :now, version = version + 1
                         WHERE id = :id AND status = 'ACTIVE'""")
                .param("keepName", name == null)
                .param("name", name == null || name.isBlank() ? null : name.strip())
                .param("language", language == null ? null : language.tag())
                .param("now", ts(now))
                .param("id", id)
                .update();
    }

    /** Şəxsi məlumat silinir; möhürlər və mükafatlar anonim ID ilə statistikada qalır. */
    public void anonymize(UUID id, Instant now) {
        jdbc.sql("""
                        UPDATE customers
                           SET status = 'DELETED', phone_enc = NULL, phone_hash = NULL, phone_masked = NULL, name = NULL,
                               deleted_at = :now, updated_at = :now, version = version + 1
                         WHERE id = :id""")
                .param("now", ts(now))
                .param("id", id)
                .update();
    }

    static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Customer map(ResultSet rs, int ignoredRowNum) throws SQLException {
        Timestamp registered = rs.getTimestamp("registered_at");
        return new Customer(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getBytes("phone_enc"),
                rs.getBytes("phone_hash"),
                rs.getString("phone_masked"),
                Language.fromTag(rs.getString("preferred_language")).orElse(Language.DEFAULT),
                Status.valueOf(rs.getString("status")),
                registered == null ? null : registered.toInstant());
    }
}
