package az.tapstamp.security.crypto;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import javax.crypto.spec.SecretKeySpec;

import az.tapstamp.shared.id.Ids;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * DEK-ləri {@code data_encryption_keys} cədvəlində KEK ilə şifrələnmiş saxlayır.
 * Açılmış açarlar yaddaşda qısa müddət keşlənir; keş açarı bazanın (platforma və ya tenant) adını daşıyır,
 * ona görə bir tenant-ın açarı heç vaxt digərinin kontekstində işlədilmir.
 */
public class JdbcDataKeyProvider implements DataKeyProvider {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;
    private final KeyEncryptionService kek;
    private final Supplier<String> scope;
    private final Cache<String, DataKey> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    /**
     * @param scope cari bazanın identifikatoru (məs. {@code platform} və ya {@code t:<tenantId>})
     */
    public JdbcDataKeyProvider(JdbcClient jdbc, KeyEncryptionService kek, Supplier<String> scope) {
        this.jdbc = jdbc;
        this.kek = kek;
        this.scope = scope;
    }

    @Override
    public DataKey activeKey(KeyPurpose purpose) {
        String cacheKey = scope.get() + ":active:" + purpose;
        return cache.get(cacheKey, ignored -> loadActive(purpose).orElseGet(() -> createActive(purpose)));
    }

    @Override
    public DataKey keyById(UUID id) {
        String cacheKey = scope.get() + ":" + id;
        return cache.get(cacheKey, ignored -> jdbc.sql("""
                        SELECT id, purpose, wrapped_key, kek_id FROM data_encryption_keys WHERE id = :id""")
                .param("id", id)
                .query((rs, n) -> unwrap(rs.getObject("id", UUID.class), KeyPurpose.valueOf(rs.getString("purpose")),
                        rs.getBytes("wrapped_key"), rs.getString("kek_id")))
                .optional()
                .orElseThrow(() -> new CryptoException("Data key not found: " + id)));
    }

    /** Provisioning zamanı bütün məqsədlər üçün açarları əvvəlcədən yaradır (idempotent). */
    public void ensureKeys(KeyPurpose... purposes) {
        for (KeyPurpose purpose : purposes) {
            if (loadActive(purpose).isEmpty()) {
                createActive(purpose);
            }
        }
    }

    private Optional<DataKey> loadActive(KeyPurpose purpose) {
        return jdbc.sql("""
                        SELECT id, wrapped_key, kek_id FROM data_encryption_keys
                        WHERE purpose = :purpose AND status = 'ACTIVE'""")
                .param("purpose", purpose.name())
                .query((rs, n) -> unwrap(rs.getObject("id", UUID.class), purpose, rs.getBytes("wrapped_key"),
                        rs.getString("kek_id")))
                .optional();
    }

    private DataKey createActive(KeyPurpose purpose) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        UUID id = Ids.newId();
        KeyEncryptionService.WrappedKey wrapped = kek.wrap(raw, context(id, purpose));
        try {
            jdbc.sql("""
                            INSERT INTO data_encryption_keys (id, purpose, wrapped_key, kek_id, status, created_at)
                            VALUES (:id, :purpose, :wrapped, :kekId, 'ACTIVE', :createdAt)""")
                    .param("id", id)
                    .param("purpose", purpose.name())
                    .param("wrapped", wrapped.ciphertext())
                    .param("kekId", wrapped.kekId())
                    .param("createdAt", Timestamp.from(Instant.now()))
                    .update();
            return new DataKey(id, purpose, new SecretKeySpec(raw, "AES"));
        } catch (DuplicateKeyException concurrentCreation) {
            // Paralel sorğu açarı artıq yaradıb — onu istifadə edirik
            return loadActive(purpose).orElseThrow(() -> concurrentCreation);
        }
    }

    private DataKey unwrap(UUID id, KeyPurpose purpose, byte[] wrappedKey, String kekId) {
        byte[] raw = kek.unwrap(new KeyEncryptionService.WrappedKey(kekId, wrappedKey), context(id, purpose));
        return new DataKey(id, purpose, new SecretKeySpec(raw, "AES"));
    }

    private static String context(UUID id, KeyPurpose purpose) {
        return "dek:" + id + ":" + purpose;
    }
}
