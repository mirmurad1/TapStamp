package az.cafeloyalty.tenancy.provisioning;

import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.shared.i18n.LocalizedText;

/**
 * Yeni şirkətin (tenant-ın) yaradılması üçün məlumat.
 *
 * @param slug          texniki ad (DB adında istifadə olunur), məs. {@code mockcoffee}
 * @param displayName   şirkətin adı (3 dildə)
 * @param primaryDomain şirkətin öz domeni, məs. {@code bonus.mockcoffee.az}
 */
public record ProvisionTenantCommand(
        String slug,
        LocalizedText displayName,
        String primaryDomain,
        Language defaultLanguage,
        String timezone) {
}
