package az.tapstamp.security.crypto;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Telefon nömrəsinin deterministik hash-i (axtarış və unikallıq üçün): HMAC-SHA256(tenant açarı, E.164).
 * Tenant açarı master açardan HKDF ilə törədilir: eyni nömrənin iki şirkətdəki hash-ləri fərqlidir,
 * ona görə iki baza sızsa belə müştəriləri bir-birinə bağlamaq olmur.
 */
public class PhoneHasher {

    private static final byte[] SALT = "tapstamp:phone-hash:v1".getBytes(StandardCharsets.UTF_8);

    private final byte[] masterKey;
    private final Cache<UUID, byte[]> tenantKeys = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofHours(1))
            .build();

    public PhoneHasher(String masterKeyBase64) {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            throw new IllegalStateException("app.crypto.phone-hmac-key təyin olunmayıb");
        }
        this.masterKey = Base64.getDecoder().decode(masterKeyBase64.trim());
        if (masterKey.length < 32) {
            throw new IllegalStateException("app.crypto.phone-hmac-key ən azı 32 bayt olmalıdır");
        }
    }

    public byte[] hash(UUID tenantId, String e164) {
        byte[] key = tenantKeys.get(tenantId,
                id -> Hkdf.derive(masterKey, SALT, id.toString().getBytes(StandardCharsets.UTF_8), 32));
        return Hkdf.hmac(key, e164.getBytes(StandardCharsets.UTF_8));
    }
}
