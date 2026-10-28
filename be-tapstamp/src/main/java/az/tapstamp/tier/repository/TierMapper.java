package az.tapstamp.tier.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.TenantMapper;
import org.jspecify.annotations.Nullable;

/** Müştəri səviyyələri ({@code loyalty_tiers}). SQL: {@code mapper/tenant/tier/TierMapper.xml}. */
@TenantMapper
public interface TierMapper {

    /**
     * Səviyyə. İmtiyazlar: {@code bonusStamps} — hər NFC möhürünə əlavə; {@code reachGift} — çatanda cari hədiyyə;
     * {@code reachStamps} — çatanda bir dəfəlik möhür.
     */
    record Tier(UUID id, LocalizedText name, String color, int minVisits, int bonusStamps, boolean reachGift,
                int reachStamps) {
    }

    /** Səviyyədəki aktiv müştərilərin sayı. */
    record TierCount(UUID tierId, long customers) {
    }

    /** Proqram açıqdırmı ({@code tier_settings}). */
    boolean enabled();

    void updateEnabled(boolean enabled, UUID staffId, Instant now);

    /** Həddə görə artan sırada. */
    List<Tier> findAll();

    Optional<Tier> findById(UUID id);

    void insert(UUID id, LocalizedText name, String color, int minVisits, int bonusStamps, boolean reachGift,
                int reachStamps, UUID staffId, Instant now);

    void update(UUID id, LocalizedText name, String color, int minVisits, int bonusStamps, boolean reachGift,
                int reachStamps, UUID staffId, Instant now);

    /** Səviyyəyə çatma imtiyazının qeydi; bu müştəriyə bu səviyyə üçün artıq verilibsə 0. */
    int insertReachGrant(UUID customerId, UUID tierId, Instant now);

    void setReachGrantReward(UUID customerId, UUID tierId, UUID rewardId);

    /** Bonus möhürü və ya çatma imtiyazı verməmiş səviyyə silinir; vermişsə 0. */
    int deleteUnused(UUID id);

    boolean minVisitsTaken(int minVisits, @Nullable UUID exceptId);

    /** Müştərinin ziyarətləri — təsdiqlənmiş NFC möhürləri. */
    int visits(UUID customerId);

    /** Hər səviyyədəki aktiv müştərilər (heç bir səviyyəyə çatmayanlar sayılmır). */
    List<TierCount> distribution();

    /** Səviyyəyə çatmamış aktiv müştərilər (ən aşağı həddən az ziyarət). */
    long belowAll();
}
