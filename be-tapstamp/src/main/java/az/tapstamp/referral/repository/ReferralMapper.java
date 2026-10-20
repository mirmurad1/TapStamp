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

    record ReferralSettings(boolean enabled, int referrerStamps, int friendStamps, int monthlyCap, int validDays) {
    }

    /** Bonus verilən dəvət: dəvət edən və dost. */
    record Claimed(UUID id, UUID referrerId) {
    }

    /** Müştərinin öz dəvətləri üzrə saylar ({@code pending} — müddəti bitməmiş gözləyənlər). */
    record Stats(int invited, int rewarded, int pending) {
    }

    /** Admin siyahısı üçün; {@code status}-da müddəti keçmiş PENDING — EXPIRED kimi qaytarılır. */
    record ReferralRow(UUID id, UUID referrerId, @Nullable String referrerName, @Nullable String referrerPhone, UUID friendId,
               @Nullable String friendName, @Nullable String friendPhone, String status, Instant createdAt,
               Instant expiresAt, @Nullable Instant rewardedAt) {
    }

    record ReferralSummary(long total, long rewarded, long pending, long expired, long limitReached, long bonusStamps) {
    }

    ReferralSettings settings();

    void updateSettings(boolean enabled, int referrerStamps, int friendStamps, int monthlyCap, int validDays,
                        UUID staffId, Instant now);

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

    boolean isActive(UUID customerId);

    Stats stats(UUID referrerId, Instant now);

    List<ReferralRow> list(Instant now, int limit, long offset);

    long count();

    ReferralSummary summary(Instant now);
}
