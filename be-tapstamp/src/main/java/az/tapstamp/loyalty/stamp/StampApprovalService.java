package az.tapstamp.loyalty.stamp;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.bonus.BonusRuleService;
import az.tapstamp.loyalty.repository.StampMapper;
import az.tapstamp.loyalty.stamp.StampLedger.CardState;
import az.tapstamp.loyalty.stamp.StampLedger.StampStatus;
import az.tapstamp.notification.push.PushService;
import az.tapstamp.referral.ReferralService;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import az.tapstamp.tier.TierService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * İşçi təsdiqi rejimi (plan §9.2): PENDING möhür təsdiqlənənə qədər karta əlavə olunmur. İşçi yalnız öz filialının
 * möhürlərini görür və idarə edir (başqa filialın möhürü üçün {@code NOT_FOUND}). Təsdiqlənməyən möhür
 * {@code pending_timeout_minutes}-dən sonra avtomatik rədd edilir ({@code PENDING_TIMEOUT}).
 */
@Service
public class StampApprovalService {

    public record PendingStamp(UUID id, UUID branchId, UUID customerId, String customerName, String phoneMasked,
                               Instant createdAt, Instant expiresAt) {
    }

    public record Decision(UUID stampId, StampStatus status, CardState card) {
    }

    private final StampMapper stamps;
    private final StampLedger ledger;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final ReferralService referrals;
    private final BonusRuleService bonusRules;
    private final TierService tiers;
    private final PushService push;
    private final Clock clock;

    public StampApprovalService(StampMapper stamps, StampLedger ledger, AuditLogger audit,
                                PlatformTransactionManager transactionManager, ReferralService referrals, Clock clock, PushService push,
                                BonusRuleService bonusRules, TierService tiers) {
        this.bonusRules = bonusRules;
        this.tiers = tiers;
        this.referrals = referrals;
        this.push = push;
        this.stamps = stamps;
        this.ledger = ledger;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Filialın gözləyən möhürləri, ən köhnəsi birinci, səhifə ilə. */
    public PageResult<PendingStamp> pending(AdminPrincipal principal, UUID branchId, PageQuery page) {
        AccessPolicy.requireBranch(principal, branchId);
        StampPolicy policy = ledger.policy(branchId);
        return page.result(stamps.findPending(branchId, page.limit(), page.offset()), stamps.countPending(branchId))
                .map(row -> new PendingStamp(row.id(), row.branchId(), row.customerId(), row.customerName(),
                        row.phoneMasked(), row.createdAt(), row.createdAt().plus(policy.pendingTimeout())));
    }

    public Decision confirm(AdminPrincipal principal, UUID stampId) {
        Instant now = clock.instant();
        return tx.execute(status -> {
            StampLedger.Stamp stamp = lockPending(principal, stampId);
            ledger.lockAccount(stamp.customerId(), now);
            stamps.confirmStamp(stampId, principal.userId(), now);
            CardState card = ledger.applyConfirmed(stamp.customerId(), stampId, now);
            // Bonus qaydası möhürün alındığı (toxunuş) anına görə yoxlanılır
            card = StampClaimService.withBonus(card,
                    bonusRules.onTapStampConfirmed(stamp.customerId(), stamp.branchId(), stamp.createdAt(), now));
            card = StampClaimService.withBonus(card,
                    tiers.onTapStampConfirmed(stamp.customerId(), stamp.branchId(), stamp.createdAt(), now));
            card = StampClaimService.withBonus(card,
                    referrals.onTapStampConfirmed(stamp.customerId(), stamp.branchId(), now));
            audit.tenant("STAMP_CONFIRMED", AuditLogger.ActorType.STAFF, principal.userId(), "stamp", stampId,
                    Map.of("branchId", stamp.branchId().toString()));
            // Müştəri kassadan uzaqlaşmış ola bilər — təsdiq bildirişi (hədiyyə yaranıbsa, hədiyyə bildirişi)
            if (card.giftCardId() != null) {
                push.notify(stamp.customerId(), PushService.Kind.GIFT_READY);
            } else {
                push.notify(stamp.customerId(), PushService.Kind.STAMP_CONFIRMED, card.currentStamps(), card.stampsRequired());
            }
            return new Decision(stampId, StampStatus.CONFIRMED, card);
        });
    }

    public Decision reject(AdminPrincipal principal, UUID stampId, String reason) {
        Instant now = clock.instant();
        return tx.execute(status -> {
            StampLedger.Stamp stamp = lockPending(principal, stampId);
            markRejected(stampId, principal.userId(), reason, now);
            audit.tenant("STAMP_REJECTED", AuditLogger.ActorType.STAFF, principal.userId(), "stamp", stampId,
                    Map.of("branchId", stamp.branchId().toString()));
            return new Decision(stampId, StampStatus.REJECTED, ledger.card(stamp.customerId()));
        });
    }

    /** Fon işi: vaxtı keçmiş PENDING möhürlər (hər filialın öz müddəti ilə) rədd edilir. */
    public int rejectTimedOut() {
        Instant now = clock.instant();
        return stamps.rejectTimedOut(now);
    }

    private StampLedger.Stamp lockPending(AdminPrincipal principal, UUID stampId) {
        StampLedger.Stamp stamp = ledger.lockStamp(stampId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        AccessPolicy.requireBranch(principal, stamp.branchId());
        if (stamp.status() != StampStatus.PENDING) {
            throw new ApiException(ErrorCode.STAMP_NOT_PENDING);
        }
        return stamp;
    }

    private void markRejected(UUID stampId, UUID staffId, String reason, Instant now) {
        stamps.rejectStamp(stampId, staffId, reason, now);
    }
}
