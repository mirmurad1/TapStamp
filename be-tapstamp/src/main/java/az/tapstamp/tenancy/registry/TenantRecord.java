package az.tapstamp.tenancy.registry;

import java.time.ZoneId;
import java.util.UUID;

import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.tenancy.TenantInfo;
import az.tapstamp.tenancy.TenantStatus;

/** Platforma reyestrindəki tenant qeydi, o cümlədən bazaya qoşulma məlumatı (şifrələr şifrəli). */
public record TenantRecord(
        UUID id,
        String slug,
        LocalizedText displayName,
        TenantStatus status,
        Language defaultLanguage,
        ZoneId timezone,
        String dbHost,
        int dbPort,
        String dbName,
        String ownerUsername,
        byte[] ownerPasswordEnc,
        String appUsername,
        byte[] appPasswordEnc) {

    public TenantInfo toInfo() {
        return new TenantInfo(id, slug, status, defaultLanguage, timezone);
    }

    @Override
    public String toString() {
        return "TenantRecord[id=" + id + ", slug=" + slug + ", status=" + status + ", db=" + dbName + "]";
    }
}
