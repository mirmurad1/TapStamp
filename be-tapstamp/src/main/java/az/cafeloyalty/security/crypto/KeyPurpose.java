package az.cafeloyalty.security.crypto;

/** Məlumat açarlarının (DEK) təyinatı. Hər məqsəd üçün ayrıca açar — birinin sızması digərinə təsir etmir. */
public enum KeyPurpose {
    /** Şəxsi məlumatlar (telefon nömrələri). */
    PII,
    /** NFC çiplərinin AES açarları. */
    NFC_KEYS,
    /** İşçilərin TOTP sirləri. */
    TOTP,
    /** Platforma bazasında tenant DB rollarının şifrələri. */
    TENANT_DB_CREDENTIALS
}
