package az.tapstamp.reminder.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.tapstamp.shared.persistence.TenantMapper;

/** Avtomatik xatırlatmalar: parametrlər, namizədlər və jurnal. SQL: {@code mapper/tenant/reminder/ReminderMapper.xml}. */
@TenantMapper
public interface ReminderMapper {

    record ReminderSettings(boolean nearRewardEnabled, boolean winbackEnabled, int winbackDays,
                            boolean unusedGiftEnabled) {
    }

    /** Xatırlatma namizədi: {@code cycleKey} — dövrün açarı (son gəliş; hədiyyədə — hədiyyə + son gəliş). */
    record Candidate(UUID customerId, String cycleKey, int current, int required) {
    }

    /** Növ üzrə son dövrün statistikası: göndərilən və 7 gün ərzində gələn (NFC möhürü alan) müştərilər. */
    record KindStats(String kind, long sent, long returned) {
    }

    ReminderSettings settings();

    void updateSettings(boolean nearRewardEnabled, boolean winbackEnabled, int winbackDays, boolean unusedGiftEnabled,
                        UUID staffId, Instant now);

    /** Kartında "hədd − 1" möhür olan və {@code visitedBefore}-dən bəri gəlməyən müştərilər. */
    List<Candidate> nearReward(Instant visitedBefore, Instant weekAgo, int limit);

    /** İstifadə olunmamış (müddəti keçməmiş) hədiyyəsi olan və {@code visitedBefore}-dən bəri gəlməyənlər. */
    List<Candidate> unusedGift(Instant visitedBefore, Instant weekAgo, Instant now, int limit);

    /** {@code visitedBefore}-dən bəri gəlməyənlər. */
    List<Candidate> winback(Instant visitedBefore, Instant weekAgo, int limit);

    /** Jurnala yazır; bu dövr üçün artıq göndərilibsə 0. */
    int record(UUID id, UUID customerId, String kind, String cycleKey, Instant now);

    List<KindStats> stats(Instant since);
}
