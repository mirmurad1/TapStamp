package az.tapstamp.company.service;

import java.time.ZoneId;
import java.util.List;

import az.tapstamp.company.repository.CompanyMapper;
import az.tapstamp.shared.i18n.LocalizedText;
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

    private final CompanyMapper companies;

    public CompanySettings(CompanyMapper companies) {
        this.companies = companies;
    }

    public Settings current() {
        return companies.findSettings()
                .map(row -> new Settings(row.name(),
                        row.allowedPhoneRegions() == null || row.allowedPhoneRegions().isBlank()
                                ? List.of("AZ") : List.of(row.allowedPhoneRegions().split(",")),
                        row.smsSenderName(), ZoneId.of(row.timezone()), row.brandColor()))
                .orElseThrow(() -> new IllegalStateException("company_profile is missing"));
    }
}
