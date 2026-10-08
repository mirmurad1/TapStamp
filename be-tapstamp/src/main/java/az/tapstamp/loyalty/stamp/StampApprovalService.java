package az.tapstamp.loyalty.stamp;

import static az.tapstamp.shared.persistence.SqlTime.instant;
import static az.tapstamp.shared.persistence.SqlTime.ts;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.loyalty.stamp.StampLedger.CardState;
import az.tapstamp.loyalty.stamp.StampLedger.StampStatus;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
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

    private final JdbcClient jdbc;
    private final StampLedger ledger;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public StampApprovalService(@Qualifier("tenantJdbc") JdbcClient jdbc, StampLedger ledger, AuditLogger audit,
                                PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Filialın gözləyən möhürləri, ən köhnəsi birinci. */
    public List<PendingStamp> pending(AdminPrincipal principal, UUID branchId) {
        AccessPolicy.requireBranch(principal, branchId);
        StampPolicy policy = ledger.policy(branchId);
        return jdbc.sql("""
                        SELECT s.id, s.branch_id, s.customer_id, c.name, c.phone_masked, s.created_at
                        FROM stamps s JOIN customers c ON c.id = s.customer_id
                        WHERE s.branch_id = :branchId AND s.status = 'PENDING'
                        ORDER BY s.created_at LIMIT 100""")
                .param("branchId", branchId)
                .query((rs, n) -> {
                    Instant created = instant(rs, "created_at");
                    return new PendingStamp(rs.getObject("id", UUID.class), rs.getObject("branch_id", UUID.class),
                            rs.getObject("customer_id", UUID.class), rs.getString("name"),
                            rs.getString("phone_masked"), created, created.plus(policy.pendingTimeout()));
                })
                .list();
    }

    public Decision confirm(AdminPrincipal principal, UUID stampId) {
        Instant now = clock.instant();
        return tx.execute(status -> {
            StampLedger.Stamp stamp = lockPending(principal, stampId);
            ledger.lockAccount(stamp.customerId(), now);
            jdbc.sql("UPDATE stamps SET status = 'CONFIRMED', confirmed_at = :now, confirmed_by = :staffId WHERE id = :id")
                    .param("now", ts(now))
                    .param("staffId", principal.userId())
                    .param("id", stampId)
                    .update();
            CardState card = ledger.applyConfirmed(stamp.customerId(), stampId, now);
            audit.tenant("STAMP_CONFIRMED", AuditLogger.ActorType.STAFF, principal.userId(), "stamp", stampId,
                    Map.of("branchId", stamp.branchId().toString()));
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
        return jdbc.sql("""
                        UPDATE stamps s SET status = 'REJECTED', revoke_reason = 'PENDING_TIMEOUT', revoked_at = :now
                        WHERE s.status = 'PENDING'
                          AND s.created_at + make_interval(mins => (
                                SELECT p.pending_timeout_minutes FROM stamp_policies p
                                WHERE p.branch_id = s.branch_id OR p.branch_id IS NULL
                                ORDER BY p.branch_id NULLS LAST LIMIT 1)) <= :now""")
                .param("now", ts(now))
                .update();
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
        jdbc.sql("""
                        UPDATE stamps SET status = 'REJECTED', revoke_reason = :reason, revoked_at = :now,
                                          revoked_by = :staffId
                        WHERE id = :id""")
                .param("reason", reason)
                .param("now", ts(now))
                .param("staffId", staffId)
                .param("id", stampId)
                .update();
    }
}
