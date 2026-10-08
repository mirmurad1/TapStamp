package az.cafeloyalty.tenancy.datasource;

import java.util.UUID;

import az.cafeloyalty.security.crypto.Aad;
import az.cafeloyalty.security.crypto.EnvelopeCipher;
import az.cafeloyalty.security.crypto.KeyPurpose;
import az.cafeloyalty.tenancy.TenancyProperties;
import az.cafeloyalty.tenancy.registry.TenantRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Tenant DB rollarının şifrələri: platforma bazasında envelope encryption ilə saxlanılır. */
@Component
public class TenantCredentials {

    static final String APP_PASSWORD_COLUMN = "db_app_password_enc";
    static final String OWNER_PASSWORD_COLUMN = "db_owner_password_enc";

    private final EnvelopeCipher platformCipher;
    private final TenancyProperties properties;

    public TenantCredentials(@Qualifier("platformCipher") EnvelopeCipher platformCipher, TenancyProperties properties) {
        this.platformCipher = platformCipher;
        this.properties = properties;
    }

    public String appPassword(TenantRecord tenant) {
        return platformCipher.decryptString(tenant.appPasswordEnc(), aad(tenant, APP_PASSWORD_COLUMN));
    }

    public String ownerPassword(TenantRecord tenant) {
        return platformCipher.decryptString(tenant.ownerPasswordEnc(), aad(tenant, OWNER_PASSWORD_COLUMN));
    }

    public byte[] encryptAppPassword(UUID tenantId, String password) {
        return platformCipher.encryptString(KeyPurpose.TENANT_DB_CREDENTIALS, password,
                Aad.platformField("tenants", APP_PASSWORD_COLUMN, tenantId));
    }

    public byte[] encryptOwnerPassword(UUID tenantId, String password) {
        return platformCipher.encryptString(KeyPurpose.TENANT_DB_CREDENTIALS, password,
                Aad.platformField("tenants", OWNER_PASSWORD_COLUMN, tenantId));
    }

    public String jdbcUrl(TenantRecord tenant) {
        return properties.jdbcUrl(tenant.dbHost(), tenant.dbPort(), tenant.dbName());
    }

    private static String aad(TenantRecord tenant, String column) {
        return Aad.platformField("tenants", column, tenant.id());
    }
}
