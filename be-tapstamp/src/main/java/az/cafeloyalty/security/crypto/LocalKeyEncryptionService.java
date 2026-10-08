package az.cafeloyalty.security.crypto;

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * KEK-ləri env-dən ({@code APP_CRYPTO_KEKS="id:base64,id2:base64"}) oxuyan implementasiya.
 * Bir neçə KEK saxlanılır ki, rotasiyadan sonra köhnə KEK ilə şifrələnmiş DEK-lər də açıla bilsin;
 * yeni DEK-lər həmişə aktiv KEK ilə şifrələnir.
 */
public class LocalKeyEncryptionService implements KeyEncryptionService {

    private static final Pattern KEK_ID = Pattern.compile("^[a-zA-Z0-9_.-]{1,64}$");

    private final Map<String, SecretKey> keks;
    private final String activeKekId;

    public LocalKeyEncryptionService(String keksSpec, String activeKekId) {
        this.keks = parse(keksSpec);
        if (activeKekId == null || !keks.containsKey(activeKekId)) {
            throw new IllegalStateException("app.crypto.active-kek-id app.crypto.keks siyahısında yoxdur");
        }
        this.activeKekId = activeKekId;
    }

    @Override
    public WrappedKey wrap(byte[] dataKey, String context) {
        return new WrappedKey(activeKekId, AesGcm.encrypt(keks.get(activeKekId), dataKey, aad(activeKekId, context)));
    }

    @Override
    public byte[] unwrap(WrappedKey wrapped, String context) {
        SecretKey kek = keks.get(wrapped.kekId());
        if (kek == null) {
            throw new CryptoException("Unknown KEK id: " + wrapped.kekId());
        }
        return AesGcm.decrypt(kek, wrapped.ciphertext(), aad(wrapped.kekId(), context));
    }

    public String activeKekId() {
        return activeKekId;
    }

    private static String aad(String kekId, String context) {
        return "kek:" + kekId + "|" + context;
    }

    static Map<String, SecretKey> parse(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalStateException("app.crypto.keks təyin olunmayıb (scripts/gen-secrets ilə yaradın)");
        }
        Map<String, SecretKey> result = new LinkedHashMap<>();
        for (String entry : spec.split(",")) {
            String[] parts = entry.trim().split(":", 2);
            if (parts.length != 2 || !KEK_ID.matcher(parts[0]).matches()) {
                throw new IllegalStateException("app.crypto.keks formatı səhvdir: 'id:base64' gözlənilir");
            }
            byte[] key;
            try {
                key = Base64.getDecoder().decode(parts[1].trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("KEK '" + parts[0] + "' base64 deyil");
            }
            if (key.length != 32) {
                throw new IllegalStateException("KEK '" + parts[0] + "' 32 bayt (AES-256) olmalıdır");
            }
            result.put(parts[0], new SecretKeySpec(key, "AES"));
        }
        return Collections.unmodifiableMap(result);
    }
}
