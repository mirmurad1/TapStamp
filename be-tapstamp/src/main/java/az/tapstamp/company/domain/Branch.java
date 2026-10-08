package az.tapstamp.company.domain;

import java.time.Instant;

import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.BaseEntity;
import az.tapstamp.shared.persistence.LocalizedTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnTransformer;

/** Şirkətin filialı (kafe nöqtəsi). */
@Entity
@Table(name = "branches")
public class Branch extends BaseEntity {

    @Convert(converter = LocalizedTextConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "name", nullable = false)
    private LocalizedText name;

    @Convert(converter = LocalizedTextConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "address")
    private LocalizedText address;

    @Convert(converter = LocalizedTextConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "description")
    private LocalizedText description;

    /** İş saatları (JSON); ayrıca model iş saatları qaydası ilə birlikdə əlavə olunacaq. */
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "business_hours")
    private String businessHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BranchStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Branch() {
    }

    public Branch(LocalizedText name, LocalizedText address, LocalizedText description, Instant now) {
        super(true);
        this.name = name;
        this.address = address;
        this.description = description;
        this.status = BranchStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public LocalizedText getName() {
        return name;
    }

    public LocalizedText getAddress() {
        return address;
    }

    public LocalizedText getDescription() {
        return description;
    }

    public String getBusinessHours() {
        return businessHours;
    }

    public BranchStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
