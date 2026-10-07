package az.cafeloyalty.company.domain;

import java.time.Instant;

import az.cafeloyalty.shared.i18n.LocalizedText;
import az.cafeloyalty.shared.persistence.LocalizedTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnTransformer;

/** Şirkət profili — tenant bazasında həmişə tək sətir. */
@Entity
@Table(name = "company_profile")
public class CompanyProfile {

    @Id
    @Column(name = "singleton")
    private Boolean singleton = Boolean.TRUE;

    @Convert(converter = LocalizedTextConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "name", nullable = false)
    private LocalizedText name;

    @Convert(converter = LocalizedTextConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "description")
    private LocalizedText description;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "default_language", nullable = false)
    private String defaultLanguage;

    @Column(name = "allowed_phone_regions", nullable = false)
    private String[] allowedPhoneRegions;

    @Column(name = "sms_sender_name")
    private String smsSenderName;

    @Column(name = "brand_color", nullable = false)
    private String brandColor;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CompanyProfile() {
    }

    public LocalizedText getName() {
        return name;
    }

    public LocalizedText getDescription() {
        return description;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public String[] getAllowedPhoneRegions() {
        return allowedPhoneRegions.clone();
    }

    public String getSmsSenderName() {
        return smsSenderName;
    }

    public String getBrandColor() {
        return brandColor;
    }
}
