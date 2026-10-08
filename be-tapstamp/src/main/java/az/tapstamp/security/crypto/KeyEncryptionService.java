package az.tapstamp.security.crypto;

/**
 * Master açar (KEK) ilə məlumat açarlarının (DEK) şifrələnməsi — envelope encryption-ın üst qatı.
 * İndiki implementasiya açarı env-dən oxuyur; gələcəkdə AWS KMS və ya HashiCorp Vault Transit
 * implementasiyası ilə əvəz edilə bilər (çağıran kod dəyişmir).
 */
public interface KeyEncryptionService {

    /**
     * @param context şifrəli açarın bağlandığı kontekst (məs. DEK-in ID-si və məqsədi); başqa kontekstdə açılmır
     */
    WrappedKey wrap(byte[] dataKey, String context);

    byte[] unwrap(WrappedKey wrapped, String context);

    /** Şifrələnmiş DEK və onu şifrələyən KEK-in identifikatoru. */
    record WrappedKey(String kekId, byte[] ciphertext) {
    }
}
