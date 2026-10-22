package az.tapstamp.plan.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.persistence.PlatformMapper;
import org.jspecify.annotations.Nullable;

/** Şirkətlərin paketləri və istifadə olunmuş sınaq müddətləri. SQL: {@code mapper/platform/plan/SubscriptionMapper.xml}. */
@PlatformMapper
public interface SubscriptionMapper {

    record SubscriptionRow(UUID tenantId, String plan, String status, Instant trialEndsAt, int extraBranches) {
    }

    record TrialRow(String plan, Instant startedAt, Instant endsAt) {
    }

    Optional<SubscriptionRow> find(UUID tenantId);

    List<SubscriptionRow> findAll();

    void upsert(UUID tenantId, String plan, String status, @Nullable Instant trialEndsAt, int extraBranches,
                @Nullable UUID updatedBy, Instant now);

    List<TrialRow> trials(UUID tenantId);

    /** Bu paketin sınağı artıq istifadə olunubsa 0 (yazılmır), ilk dəfədirsə 1. */
    int insertTrial(UUID tenantId, String plan, Instant startedAt, Instant endsAt, @Nullable UUID startedBy);
}
