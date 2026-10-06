package az.cafeloyalty.shared.persistence;

import java.util.Objects;
import java.util.UUID;

import az.cafeloyalty.shared.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * UUIDv7 açarlı entity-lərin bazası. ID tətbiqdə (yaradılarkən) təyin olunur, ona görə
 * {@link Persistable#isNew()} açıq izlənir — əks halda Spring Data hər save-də əvvəlcə SELECT edərdi.
 */
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Transient
    private boolean newEntity;

    /** Yalnız JPA üçün. */
    protected BaseEntity() {
    }

    protected BaseEntity(boolean create) {
        if (create) {
            this.id = Ids.newId();
            this.newEntity = true;
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        this.newEntity = false;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof BaseEntity entity && id != null && id.equals(entity.id)
                && getClass() == other.getClass();
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(id);
    }
}
