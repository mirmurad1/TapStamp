package az.tapstamp.plan.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.tapstamp.shared.persistence.PlatformMapper;
import org.jspecify.annotations.Nullable;

/** Şirkətlərin açıq əlavələri. SQL: {@code mapper/platform/plan/AddonMapper.xml}. */
@PlatformMapper
public interface AddonMapper {

    List<String> find(UUID tenantId);

    /** Artıq açıqdırsa 0. */
    int enable(UUID tenantId, String addon, Instant now, @Nullable UUID by);

    /** Onsuz da bağlıdırsa 0. */
    int disable(UUID tenantId, String addon);
}
