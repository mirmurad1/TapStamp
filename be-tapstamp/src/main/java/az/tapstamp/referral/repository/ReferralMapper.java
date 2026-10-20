package az.tapstamp.referral.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.persistence.TenantMapper;
import org.jspecify.annotations.Nullable;

/** Dost dəvəti: parametrlər, dəvət kodları və dəvətlər (cari tenant-ın bazası). SQL: {@code mapper/tenant/referral/ReferralMapper.xml}. */
@TenantMapper
public interface ReferralMapper {

    /** {@code rewardOn}: FIRST_VISIT — dostun ilk NFC möhüründə, REGISTRATION — dost qeydiyyatdan keçən kimi. */
    record ReferralSettings(boolean enabled, String rewardOn, int referrerStamps, int friendStamps, int monthlyCap,
                            int validDays) {
    }

    /** Bonus verilən dəvət: dəvət edən və dost. */
    record Claimed(UUID id, UUID referrerId) {
    }

    /** Müştərinin öz dəvətləri üzrə saylar ({@code pending} — müddəti bitməmiş gözləyənlər). */
    record Stats(int invited, int rewarded, int pending) {
    }

    /**
     * Admin siyahısı üçün; {@code status}-da müddəti keçmiş PENDING — EXPIRED kimi qaytarılır. {@code visits} —
     * dostun təsdiqlənmiş NFC möhürləri (gəlişləri), {@code lastVisitAt} — sonuncusu.
     */
    record ReferralRow(UUID id, UUID referrerId, @Nullable String referrerName, @Nullable String referrerPhone,
                       UUID friendId, @Nullable String friendName, @Nullable String friendPhone, String status,
                       Instant createdAt, Instant expiresAt, @Nullable Instant rewardedAt, int visits,
                       @Nullable Instant lastVisitAt) {
    }

    /**
     * Dövr üzrə statistika: dəvət olunanlar, gələnlər (1+ NFC möhürü), qayıdanlar (2+ fərqli gün), gəlişlər,
     * hədiyyə kartı qazananlar və verilən bonus möhürlər.
     */
    record ReferralStats(long invited, long came, long returned, long visits, long giftCards, long bonusStamps) {
    }

    record TopReferrer(UUID customerId, @Nullable String name, @Nullable String phone, long invited, long came,
                       long returned) {
    }

    record ReferralSummary(long total, long rewarded, long pending, long expired, long limitReached, long bonusStamps) {
    }

    ReferralSettings settings();

    void updateSettings(boolean enabled, String rewardOn, int referrerStamps, int friendStamps, int monthlyCap,
                        int validDays, UUID staffId, Instant now);

    Optional<String> referralCode(UUID customerId);

    /** Kod yalnız hələ yoxdursa yazılır; dəyişən sətir sayı (unikallıq toqquşmasında istisna). */
    int setReferralCode(UUID customerId, String code);

    /** Kodun sahibi (yalnız aktiv müştəri). */
    Optional<UUID> findActiveByCode(String code);

    /** {@code since}-dən bəri limitə düşən (LIMIT_REACHED olmayan) dəvətlərin sayı. */
    int countSince(UUID referrerId, Instant since);

    void insert(UUID id, UUID referrerId, UUID friendId, String status, Instant createdAt, Instant expiresAt);

    /** Dostun gözləyən (müddəti bitməmiş) dəvətini atomik olaraq REWARDED edir — bonus yalnız bir dəfə verilir. */
    Optional<Claimed> claimPending(UUID friendId, Instant now);

    Optional<UUID> registrationBranch(UUID referrerId);

    boolean isActive(UUID customerId);

    Stats stats(UUID referrerId, Instant now);

    /** {@code activity}: ALL, NONE (gəlməyib), ONCE (bir gün), RETURNED (2+ gün). */
    List<ReferralRow> list(Instant now, String activity, int limit, long offset);

    long count(String activity);

    ReferralStats periodStats(Instant from, Instant to, @Nullable UUID branchId);

    List<TopReferrer> topReferrers(Instant from, Instant to, @Nullable UUID branchId, int limit);

    ReferralSummary summary(Instant now);
}
