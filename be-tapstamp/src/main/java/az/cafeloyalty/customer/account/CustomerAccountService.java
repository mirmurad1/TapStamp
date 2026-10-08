package az.cafeloyalty.customer.account;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.cafeloyalty.audit.AuditLogger;
import az.cafeloyalty.customer.CustomerStore;
import az.cafeloyalty.customer.auth.CustomerPrincipal;
import az.cafeloyalty.customer.auth.CustomerSessionService;
import az.cafeloyalty.customer.auth.OtpChallengeStore;
import az.cafeloyalty.customer.consent.LegalDocumentService;
import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.shared.i18n.LocalizedText;
import az.cafeloyalty.shared.persistence.LocalizedTextConverter;
import az.cafeloyalty.shared.persistence.SqlTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Müştərinin öz hesabı: profil, möhür kartı, dil, hesabın silinməsi (anonimləşdirmə). */
@Service
public class CustomerAccountService {

    /**
     * @param pendingStamps işçi təsdiqi gözləyən möhürlər ("barista təsdiqi gözlənilir")
     * @param nextStampAt   şirkətin standart cooldown-una görə növbəti möhürün mümkün olduğu an (null — indi mümkündür)
     */
    public record Card(int currentStamps, int totalStamps, int stampsRequired, LocalizedText rewardName,
                       LocalizedText rewardDescription, int pendingStamps, Instant nextStampAt) {
    }

    public record AvailableReward(UUID id, LocalizedText name, Instant earnedAt) {
    }

    public record Profile(UUID id, String name, String phoneMasked, Language language, Instant registeredAt, Card card,
                          List<AvailableReward> availableRewards, List<LegalDocumentService.DocumentRef> pendingConsents) {
    }

    private static final LocalizedTextConverter TEXT = new LocalizedTextConverter();

    private final JdbcClient jdbc;
    private final CustomerStore customers;
    private final CustomerSessionService sessions;
    private final OtpChallengeStore challenges;
    private final LegalDocumentService legal;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CustomerAccountService(@Qualifier("tenantJdbc") JdbcClient jdbc, CustomerStore customers,
                                  CustomerSessionService sessions, OtpChallengeStore challenges,
                                  LegalDocumentService legal, AuditLogger audit,
                                  PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.customers = customers;
        this.sessions = sessions;
        this.challenges = challenges;
        this.legal = legal;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Profile profile(CustomerPrincipal principal) {
        CustomerStore.Customer customer = active(principal);
        Card card = jdbc.sql("""
                        SELECT la.current_stamps, la.total_stamps, r.stamps_required, r.reward_name::text AS reward_name,
                               r.reward_description::text AS reward_description,
                               (SELECT count(*) FROM stamps WHERE customer_id = :customerId AND status = 'PENDING') AS pending,
                               (SELECT max(created_at) + make_interval(mins => (
                                        SELECT min_interval_minutes FROM stamp_policies WHERE branch_id IS NULL))
                                FROM stamps WHERE customer_id = :customerId AND source = 'NFC_TAP'
                                  AND status IN ('PENDING', 'CONFIRMED')) AS next_stamp_at
                        FROM reward_rules r
                        LEFT JOIN loyalty_accounts la ON la.customer_id = :customerId
                        WHERE r.active""")
                .param("customerId", customer.id())
                .query((rs, n) -> new Card(rs.getInt("current_stamps"), rs.getInt("total_stamps"),
                        rs.getInt("stamps_required"), TEXT.convertToEntityAttribute(rs.getString("reward_name")),
                        TEXT.convertToEntityAttribute(rs.getString("reward_description")), rs.getInt("pending"),
                        futureOrNull(SqlTime.instant(rs, "next_stamp_at"))))
                .optional()
                .orElse(null);
        List<AvailableReward> rewards = jdbc.sql("""
                        SELECT id, reward_name_snapshot::text AS name, earned_at FROM rewards
                        WHERE customer_id = :customerId AND status = 'AVAILABLE'
                          AND (expires_at IS NULL OR expires_at > now()) ORDER BY earned_at""")
                .param("customerId", customer.id())
                .query((rs, n) -> {
                    Timestamp earned = rs.getTimestamp("earned_at");
                    return new AvailableReward(rs.getObject("id", UUID.class),
                            TEXT.convertToEntityAttribute(rs.getString("name")), earned.toInstant());
                })
                .list();
        List<LegalDocumentService.DocumentRef> pending = legal.pendingFor(customer.id()).stream()
                .map(document -> new LegalDocumentService.DocumentRef(document.type(), document.version()))
                .toList();
        return new Profile(customer.id(), customer.name(), customer.phoneMasked(), customer.language(),
                customer.registeredAt(), card, rewards, pending);
    }

    public void update(CustomerPrincipal principal, String name, Language language) {
        active(principal);
        customers.updateProfile(principal.customerId(), name, language, clock.instant());
        if (language != null) {
            // Dil sessiya keşində də var — növbəti sorğu yeni dildə cavab alsın
            sessions.evictCustomer(principal.customerId());
        }
    }

    /**
     * Hesabın silinməsi (OTP ilə təkrar təsdiqdən sonra): şəxsi məlumat silinir, bütün sessiyalar bağlanır, gözləyən
     * mükafat kodları ləğv olunur, OTP izləri və toxunuşlardakı IP-lər silinir. Möhürlər və mükafatlar anonim ID ilə
     * ümumi statistikada qalır. Eyni nömrə sonra yeni, boş hesab ala bilər.
     */
    public void delete(CustomerPrincipal principal) {
        sessions.requireStepUp(principal);
        CustomerStore.Customer customer = active(principal);
        Instant now = clock.instant();
        tx.executeWithoutResult(status -> {
            jdbc.sql("UPDATE redemptions SET status = 'CANCELLED' WHERE customer_id = :id AND status = 'PENDING'")
                    .param("id", customer.id()).update();
            jdbc.sql("UPDATE rewards SET status = 'REVOKED' WHERE customer_id = :id AND status = 'AVAILABLE'")
                    .param("id", customer.id()).update();
            challenges.deleteForCustomer(customer.id(), customer.phoneHash());
            jdbc.sql("SELECT anonymize_customer_tap_events(:id)").param("id", customer.id()).query(Integer.class).single();
            legal.withdrawAll(customer.id());
            customers.anonymize(customer.id(), now);
            audit.tenant("CUSTOMER_DELETED", AuditLogger.ActorType.CUSTOMER, customer.id(), "customer", customer.id(),
                    Map.of("deletedAt", now.atOffset(ZoneOffset.UTC).toString()));
        });
        sessions.revokeAll(customer.id(), "ACCOUNT_DELETED");
    }

    private Instant futureOrNull(Instant at) {
        return at != null && at.isAfter(clock.instant()) ? at : null;
    }

    private CustomerStore.Customer active(CustomerPrincipal principal) {
        return customers.findById(principal.customerId())
                .filter(customer -> customer.status() == CustomerStore.Status.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }
}
