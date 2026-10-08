package az.tapstamp.company.service;

import java.sql.Array;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.LocalizedTextConverter;
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
     * @param timezone              şirkətin saat qurşağı ("gün" — gündəlik möhür limiti, iş saatları)
     * @param brandColor            brend rəngi (#RRGGBB)
     */
    public record Settings(LocalizedText name, List<String> allowedPhoneRegions, String smsSenderName, ZoneId timezone,
                           String brandColor) {
    }

    private static final LocalizedTextConverter TEXT = new LocalizedTextConverter();

    private final JdbcClient jdbc;

    public CompanySettings(@Qualifier("tenantJdbc") JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Settings current() {
        return jdbc.sql("""
                        SELECT name::text AS name, allowed_phone_regions, sms_sender_name, timezone, brand_color
                        FROM company_profile WHERE singleton""")
                .query((rs, n) -> {
                    Array regions = rs.getArray("allowed_phone_regions");
                    List<String> allowed = regions == null ? List.of("AZ")
                            : Arrays.stream((Object[]) regions.getArray()).map(String::valueOf).toList();
                    return new Settings(TEXT.convertToEntityAttribute(rs.getString("name")), allowed,
                            rs.getString("sms_sender_name"), ZoneId.of(rs.getString("timezone")),
                            rs.getString("brand_color"));
                })
                .optional()
                .orElseThrow(() -> new IllegalStateException("company_profile is missing"));
    }
}
