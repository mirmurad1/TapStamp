package az.cafeloyalty.shared.i18n;

import java.util.Locale;
import java.util.Optional;

/** Platformanın dəstəklədiyi dillər. Kod dəyərləri BCP 47 dil teqləridir və DB-də də belə saxlanılır. */
public enum Language {
    AZ("az"),
    EN("en"),
    RU("ru");

    public static final Language DEFAULT = AZ;

    private final String tag;
    private final Locale locale;

    Language(String tag) {
        this.tag = tag;
        this.locale = Locale.of(tag);
    }

    public String tag() {
        return tag;
    }

    public Locale locale() {
        return locale;
    }

    /** "az", "AZ", "az-Latn-AZ", "ru_RU" kimi dəyərləri tanıyır; dəstəklənməyən dil üçün boş qaytarır. */
    public static Optional<Language> fromTag(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String primary = value.trim().replace('_', '-').split("-", 2)[0].toLowerCase(Locale.ROOT);
        for (Language language : values()) {
            if (language.tag.equals(primary)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    public static Optional<Language> fromLocale(Locale locale) {
        return locale == null ? Optional.empty() : fromTag(locale.getLanguage());
    }
}
