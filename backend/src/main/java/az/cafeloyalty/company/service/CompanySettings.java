package az.cafeloyalty.company.service;

import java.sql.Array;
import java.util.Arrays;
import java.util.List;

import az.cafeloyalty.shared.i18n.LocalizedText;
import az.cafeloyalty.shared.persistence.LocalizedTextConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Şirkət profilindən müştəri girişi üçün lazım olan parametrlər (cari tenant). */
@Component
public class CompanySettings {

    /**
     * @param name                  şirkətin adı (3 dildə) — SMS mətnində istifadə olunur
     * @param allowedPhoneRegions   qəbul olunan nömrələrin ölkələri (ISO 3166, məs. AZ)
     * @param smsSenderName         SMS göndərən adı (provayderdə qeydiyyatdan keçmiş; yoxdursa provayderin standart adı)
     */
    public record Settings(LocalizedText name, List<String> allowedPhoneRegions, String smsSenderName) {
    }

    private static final LocalizedTextConverter TEXT = new LocalizedTextConverter();

    private final JdbcClient jdbc;

    public CompanySettings(@Qualifier("tenantJdbc") JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Settings current() {
        return jdbc.sql("SELECT name::text AS name, allowed_phone_regions, sms_sender_name FROM company_profile WHERE singleton")
                .query((rs, n) -> {
                    Array regions = rs.getArray("allowed_phone_regions");
                    List<String> allowed = regions == null ? List.of("AZ")
                            : Arrays.stream((Object[]) regions.getArray()).map(String::valueOf).toList();
                    return new Settings(TEXT.convertToEntityAttribute(rs.getString("name")), allowed,
                            rs.getString("sms_sender_name"));
                })
                .optional()
                .orElseThrow(() -> new IllegalStateException("company_profile is missing"));
    }
}
