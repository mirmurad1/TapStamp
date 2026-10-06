package az.cafeloyalty.tenancy;

import java.time.ZoneId;
import java.util.UUID;

import az.cafeloyalty.shared.i18n.Language;

/** Sorğu müddətində lazım olan tenant məlumatı (DB şifrələri daxil deyil). */
public record TenantInfo(UUID id, String slug, TenantStatus status, Language defaultLanguage, ZoneId timezone) {
}
