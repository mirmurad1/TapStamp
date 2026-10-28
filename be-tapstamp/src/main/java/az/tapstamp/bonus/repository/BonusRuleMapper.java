package az.tapstamp.bonus.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.TenantMapper;
import org.jspecify.annotations.Nullable;

/** Bonus qaydaları ({@code bonus_rules}) və onların statistikası. SQL: {@code mapper/tenant/bonus/BonusRuleMapper.xml}. */
@TenantMapper
public interface BonusRuleMapper {

    /**
     * Qayda.
     *
     * @param daysMask həftənin günləri: bazar ertəsi = 1, ..., bazar = 64
     * @param branchId boşdursa — bütün filiallar
     */
    record BonusRule(UUID id, LocalizedText name, int bonusStamps, int daysMask, @Nullable LocalTime startTime,
                     @Nullable LocalTime endTime, @Nullable LocalDate startsOn, @Nullable LocalDate endsOn,
                     @Nullable UUID branchId, boolean active, boolean notifyOnStart, @Nullable Instant notifiedAt,
                     Instant createdAt) {
    }

    /** Qaydanın son 30 gündəki nəticəsi: verilən bonus möhürlər və onları alan müştərilər. */
    record RuleStats(UUID ruleId, long bonusStamps, long customers) {
    }

    List<BonusRule> findAll();

    Optional<BonusRule> findById(UUID id);

    /** Bu gün (tarix aralığına görə) qüvvədə ola biləcək aktiv qaydalar — gün/saat yoxlaması Java-dadır. */
    List<BonusRule> findActiveOn(LocalDate day);

    void insert(UUID id, LocalizedText name, int bonusStamps, int daysMask, @Nullable LocalTime startTime,
                @Nullable LocalTime endTime, @Nullable LocalDate startsOn, @Nullable LocalDate endsOn,
                @Nullable UUID branchId, boolean active, boolean notifyOnStart, UUID staffId, Instant now);

    int update(UUID id, LocalizedText name, int bonusStamps, int daysMask, @Nullable LocalTime startTime,
               @Nullable LocalTime endTime, @Nullable LocalDate startsOn, @Nullable LocalDate endsOn,
               @Nullable UUID branchId, boolean active, boolean notifyOnStart, UUID staffId, Instant now);

    /** Bonus möhürü olmayan qayda silinir; varsa 0 (onda söndürülür). */
    int deleteUnused(UUID id);

    boolean branchExists(UUID branchId);

    /** Bildirişi hələ göndərilməmiş qayda üçün işarə qoyur; artıq qoyulubsa 0. */
    int markNotified(UUID id, Instant now);

    List<RuleStats> stats(Instant since);

    /** Bildirişləri açıq aktiv müştərilər. */
    List<UUID> pushAudience(int limit, long offset);
}
