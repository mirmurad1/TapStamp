package az.cafeloyalty.tenancy.registry;

import java.time.ZoneId;
import java.util.UUID;

import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.shared.i18n.LocalizedText;
import az.cafeloyalty.tenancy.TenantInfo;
import az.cafeloyalty.tenancy.TenantStatus;

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
