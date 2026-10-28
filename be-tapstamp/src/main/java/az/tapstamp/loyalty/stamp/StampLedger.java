package az.tapstamp.loyalty.stamp;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.loyalty.repository.StampMapper;
import az.tapstamp.loyalty.reward.GiftCardService;
import az.tapstamp.shared.id.Ids;
import org.springframework.stereotype.Component;

/**
 * Möhürlər, loyallıq hesabı və sui-istifadə qaydalarının oxunması. Möhürün karta yazılması ({@link #applyConfirmed})
 * hesab sətri kilidlənmiş halda aparılır: eyni müştərinin paralel sorğuları ardıcıl icra olunur.
 */
@Component
public class StampLedger {

    public enum StampStatus {
        PENDING,
        CONFIRMED,
        REJECTED,
        REVOKED
    }

    public record Stamp(UUID id, UUID branchId, UUID customerId, StampStatus status, UUID rewardId, Instant createdAt) {
    }

    /** Kartın vəziyyəti: cari möhürlər, qayda üzrə hədd (qayda yoxdursa 0) və bu möhürlə qazanılan hədiyyə kartı. */
    public record CardState(int currentStamps, int stampsRequired, UUID giftCardId) {
    }

    private final StampMapper stamps;
    private final GiftCardService giftCards;

    public StampLedger(StampMapper stamps, GiftCardService giftCards) {
        this.stamps = stamps;
        this.giftCards = giftCards;
    }

    public StampPolicy policy(UUID branchId) {
        return stamps.findPolicy(branchId)
                .map(row -> new StampPolicy(Duration.ofMinutes(row.minIntervalMinutes()), row.maxStampsPerDay(),
                        row.requireStaffApproval(), Duration.ofMinutes(row.pendingTimeoutMinutes()),
                        row.enforceBusinessHours()))
                .orElse(StampPolicy.DEFAULT);
    }

    public Optional<String> businessHours(UUID branchId) {
        return stamps.findBusinessHours(branchId);
    }

    /** Hesab sətrini kilidləyir (yoxdursa yaradır — köhnə məlumat üçün). */
    public void lockAccount(UUID customerId, Instant now) {
        stamps.ensureAccount(Ids.newId(), customerId, now);
        stamps.lockAccount(customerId);
    }

    /** NFC ilə alınmış son aktiv (PENDING və ya CONFIRMED) möhürün vaxtı — cooldown üçün. */
    public Optional<Instant> lastTapStampAt(UUID customerId) {
        return stamps.lastTapStampAt(customerId);
    }

    public int tapStampsOn(UUID customerId, LocalDate localDate) {
        return stamps.countTapStampsOn(customerId, localDate);
    }

    public UUID insertTapStamp(UUID branchId, UUID customerId, UUID tagId, UUID tapTokenId, StampStatus status,
                               LocalDate localDate, Instant at) {
        UUID id = Ids.newId();
        stamps.insertTapStamp(id, branchId, customerId, tagId, tapTokenId, status, localDate,
                status == StampStatus.CONFIRMED ? at : null, at);
        return id;
    }

    /** Admin tərəfindən əl ilə əlavə olunan təsdiqlənmiş möhür (səbəb və icraçı məcburidir). */
    public UUID insertManualStamp(UUID branchId, UUID customerId, UUID staffId, String reason, LocalDate localDate,
                                  Instant at) {
        UUID id = Ids.newId();
        stamps.insertManualStamp(id, branchId, customerId, staffId, reason, localDate, at);
        return id;
    }

    /** Kampaniya möhürü (platforma təsdiqli möhür kampaniyası; çağıran tranzaksiyada {@link #lockAccount} etməlidir). */
    public UUID insertCampaignStamp(UUID branchId, UUID customerId, UUID campaignId, LocalDate localDate, Instant at) {
        UUID id = Ids.newId();
        stamps.insertCampaignStamp(id, branchId, customerId, campaignId, localDate, at);
        return id;
    }

    /** Dost dəvətinin bonus möhürü (çağıran tranzaksiyada {@link #lockAccount} etməlidir). */
    public UUID insertReferralStamp(UUID branchId, UUID customerId, UUID referralId, LocalDate localDate, Instant at) {
        UUID id = Ids.newId();
        stamps.insertReferralStamp(id, branchId, customerId, referralId, localDate, at);
        return id;
    }

    /** Bonus qaydasının möhürü (çağıran tranzaksiyada {@link #lockAccount} etməlidir). */
    public UUID insertBonusStamp(UUID branchId, UUID customerId, UUID bonusRuleId, String reason, LocalDate localDate,
                                 Instant at) {
        UUID id = Ids.newId();
        stamps.insertBonusStamp(id, branchId, customerId, bonusRuleId, reason, localDate, at);
        return id;
    }

    /** Səviyyə bonusunun möhürü (çağıran tranzaksiyada {@link #lockAccount} etməlidir). */
    public UUID insertTierStamp(UUID branchId, UUID customerId, UUID tierId, String reason, LocalDate localDate,
                                Instant at) {
        UUID id = Ids.newId();
        stamps.insertTierStamp(id, branchId, customerId, tierId, reason, localDate, at);
        return id;
    }

    public Optional<Stamp> findByTapToken(UUID tapTokenId) {
        return stamps.findByTapToken(tapTokenId);
    }

    public Optional<Stamp> lockStamp(UUID stampId) {
        return stamps.lockStamp(stampId);
    }

    /**
     * Təsdiqlənmiş möhürü karta yazır (hesab artıq kilidlənib): sayğaclar artır; hədd dolanda hədiyyə kartı yaranır,
     * kart sıfırlanır və möhür həmin hədiyyəyə bağlanır.
     */
    public CardState applyConfirmed(UUID customerId, UUID stampId, Instant at) {
        // Kartın öz qaydası (hədd dəyişəndə yarımçıq kart köhnə qayda ilə bitir)
        Optional<GiftCardService.ActiveRule> rule = giftCards.ruleFor(customerId);
        int current = stamps.addStampToCard(customerId, at);
        if (rule.isEmpty()) {
            return new CardState(current, 0, null);
        }
        int required = rule.get().stampsRequired();
        if (current < required) {
            return new CardState(current, required, null);
        }
        UUID giftCardId = giftCards.issue(customerId, rule.get(), at);
        stamps.redeemCardForReward(customerId, required);
        stamps.linkStampToReward(stampId, giftCardId);
        return new CardState(current - required, required, giftCardId);
    }

    public CardState card(UUID customerId) {
        int required = giftCards.ruleFor(customerId).map(GiftCardService.ActiveRule::stampsRequired).orElse(0);
        return new CardState(stamps.findCurrentStamps(customerId).orElse(0), required, null);
    }

    public void touchCustomerVisit(UUID customerId, UUID branchId, Instant at) {
        stamps.touchCustomerVisit(customerId, branchId, at);
    }
}
