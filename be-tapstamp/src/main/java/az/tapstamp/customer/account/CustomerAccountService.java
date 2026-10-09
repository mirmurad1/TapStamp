package az.tapstamp.customer.account;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.customer.auth.CustomerPrincipal;
import az.tapstamp.customer.auth.CustomerSessionService;
import az.tapstamp.customer.consent.LegalDocumentService;
import az.tapstamp.customer.repository.CustomerAccountMapper;
import az.tapstamp.customer.repository.CustomerMapper;
import az.tapstamp.customer.repository.OtpChallengeMapper;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.i18n.LocalizedText;
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

    private final CustomerAccountMapper accounts;
    private final CustomerMapper customers;
    private final CustomerSessionService sessions;
    private final OtpChallengeMapper challenges;
    private final LegalDocumentService legal;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CustomerAccountService(CustomerAccountMapper accounts, CustomerMapper customers,
                                  CustomerSessionService sessions, OtpChallengeMapper challenges,
                                  LegalDocumentService legal, AuditLogger audit,
                                  PlatformTransactionManager transactionManager, Clock clock) {
        this.accounts = accounts;
        this.customers = customers;
        this.sessions = sessions;
        this.challenges = challenges;
        this.legal = legal;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Profile profile(CustomerPrincipal principal) {
        CustomerMapper.Customer customer = active(principal);
        Card card = accounts.findCard(customer.id())
                .map(row -> new Card(row.currentStamps(), row.totalStamps(), row.stampsRequired(), row.rewardName(),
                        row.rewardDescription(), row.pendingStamps(), futureOrNull(row.nextStampAt())))
                .orElse(null);
        List<AvailableReward> rewards = accounts.availableRewards(customer.id(), clock.instant());
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
        CustomerMapper.Customer customer = active(principal);
        Instant now = clock.instant();
        tx.executeWithoutResult(status -> {
            accounts.cancelPendingRedemptions(customer.id());
            accounts.revokeAvailableRewards(customer.id());
            challenges.deleteForCustomer(customer.id(), customer.phoneHash());
            accounts.anonymizeTapEvents(customer.id());
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

    private CustomerMapper.Customer active(CustomerPrincipal principal) {
        return customers.findById(principal.customerId())
                .filter(customer -> customer.status() == CustomerMapper.Status.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }
}
