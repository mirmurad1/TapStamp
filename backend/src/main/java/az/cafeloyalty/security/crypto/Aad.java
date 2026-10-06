package az.cafeloyalty.security.crypto;

import java.util.UUID;

/** Şifrəli sahələrin AAD (kontekst) dəyərləri. Format dəyişməməlidir — əks halda köhnə məlumat açılmaz. */
public final class Aad {

    private Aad() {
    }

    /** Tenant bazasındakı sahə: tenant + cədvəl + sütun + sətir. */
    public static String tenantField(UUID tenantId, String table, String column, UUID rowId) {
        return "t:" + tenantId + "/" + table + "." + column + ":" + rowId;
    }

    /** Platforma bazasındakı sahə. */
    public static String platformField(String table, String column, UUID rowId) {
        return "platform/" + table + "." + column + ":" + rowId;
    }
}
