package az.tapstamp.shared.i18n;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin tərəfindən daxil edilən 3 dilli mətn (mükafat adı, kafe təsviri və s.).
 * Bazada JSONB kimi saxlanılır: {@code {"az": "...", "en": "...", "ru": "..."}}. {@code az} məcburidir.
 */
public record LocalizedText(
        @NotBlank @Size(max = 2000) String az,
        @Size(max = 2000) String en,
        @Size(max = 2000) String ru) {

    public static LocalizedText of(String az, String en, String ru) {
        return new LocalizedText(az, en, ru);
    }

    /** İstənilən dildəki mətn; tərcümə yoxdursa Azərbaycan dilindəki versiya qaytarılır. */
    public String resolve(Language language) {
        String value = switch (language) {
            case AZ -> az;
            case EN -> en;
            case RU -> ru;
        };
        return value == null || value.isBlank() ? az : value;
    }
}
