package az.tapstamp.loyalty.stamp;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.bonus.BonusRuleService;
import az.tapstamp.company.service.CompanySettings;
import az.tapstamp.customer.repository.CustomerMapper;
import az.tapstamp.feedback.FeedbackService;
import az.tapstamp.customer.repository.OtpChallengeMapper;
import az.tapstamp.loyalty.repository.StampMapper;
import az.tapstamp.loyalty.stamp.StampLedger.CardState;
import az.tapstamp.loyalty.stamp.StampLedger.StampStatus;
import az.tapstamp.nfc.BrowserIdCookie;
import az.tapstamp.nfc.FraudFlags;
import az.tapstamp.nfc.TapEventLog.Event;
import az.tapstamp.nfc.TapEventLog.Source;
import az.tapstamp.nfc.TapEventLog.Stage;
import az.tapstamp.nfc.TapEventLog;
import az.tapstamp.nfc.repository.TapEventMapper;
import az.tapstamp.nfc.repository.TapTokenMapper.TapToken;
import az.tapstamp.nfc.repository.TapTokenMapper;
import az.tapstamp.referral.ReferralService;
import az.tapstamp.security.token.SecureTokens;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.web.ClientInfo;
import az.tapstamp.tier.TierService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Toxunuşa görə möhürün alınması (plan §9.1) — bir tranzaksiyada:
 * <ol>
 *   <li>token kilidlənir: ACTIVE (və ya bu müştərinin təsdiqlənmiş OTP sorğusuna RESERVED), vaxtı keçməyib, eyni brauzer;</li>
 *   <li>artıq bu müştəri tərəfindən istifadə olunubsa — əvvəlki nəticə qaytarılır (idempotentlik);</li>
 *   <li>hesab kilidlənir (eyni müştərinin sorğuları ardıcıl), qaydalar: cooldown, gündəlik limit (şirkətin saat
 *       qurşağında), iş saatları;</li>
 *   <li>möhür (işçi təsdiqi rejimində PENDING), hədd dolanda hədiyyə kartı; token CONSUMED; jurnal.</li>
 * </ol>
 * Rədd halında token REJECTED olur, jurnal və fırıldaq işarəsi commit olunur, xəta isə tranzaksiyadan sonra atılır —
 * qeyd itmir. Möhürün vaxtı kimi toxunuşun vaxtı götürülür (giriş bir neçə dəqiqə çəkə bilər).
 */
@Service
public class StampClaimService {

    /** Bir brauzerdən bir gündə bu qədər fərqli hesab — fırıldaq işarəsi. */
    static final int MULTI_ACCOUNT_THRESHOLD = 3;
    static final int COOLDOWN_HAMMERING_THRESHOLD = 3;
    /**
     * {@code giftCardId} — kart doldu; {@code reachedTier} — bu ziyarətdə çatılan səviyyə (çatma imtiyazı ilə),
     * {@code tierGiftCardId} və {@code reachedTierStamps} — onun hədiyyəsi və bir dəfəlik möhürləri.
     */
    public record ClaimResult(UUID stampId, StampStatus status, int currentStamps, int stampsRequired,
                              @Nullable UUID giftCardId, UUID branchId, @Nullable LocalizedText reachedTier,
                              @Nullable UUID tierGiftCardId, int reachedTierStamps) {
    }

    private record Outcome(ClaimResult result, ErrorCode error, Map<String, Object> properties) {
        static Outcome ok(ClaimResult result) {
            return new Outcome(result, null, Map.of());
        }

        static Outcome fail(ErrorCode error) {
            return new Outcome(null, error, Map.of());
        }

        static Outcome fail(ErrorCode error, Map<String, Object> properties) {
            return new Outcome(null, error, properties);
        }
    }

    private final TapTokenMapper tokens;
    private final TapEventLog events;
    private final FraudFlags fraudFlags;
    private final StampLedger ledger;
    private final CustomerMapper customers;
    private final OtpChallengeMapper challenges;
    private final CompanySettings companySettings;
    private final StampMapper stamps;
    private final TapEventMapper tapEvents;
    private final TransactionTemplate tx;
    private final ReferralService referrals;
    private final BonusRuleService bonusRules;
    private final TierService tiers;
    private final FeedbackService feedback;
    private final Clock clock;

    public StampClaimService(TapTokenMapper tokens, TapEventLog events, FraudFlags fraudFlags, StampLedger ledger,
                             CustomerMapper customers, OtpChallengeMapper challenges, CompanySettings companySettings,
                             StampMapper stamps, TapEventMapper tapEvents,
                             PlatformTransactionManager transactionManager, ReferralService referrals,
                             Clock clock, BonusRuleService bonusRules, TierService tiers,
                             FeedbackService feedback) {
        this.feedback = feedback;
        this.bonusRules = bonusRules;
        this.tiers = tiers;
        this.referrals = referrals;
        this.tokens = tokens;
        this.events = events;
        this.fraudFlags = fraudFlags;
        this.ledger = ledger;
        this.customers = customers;
        this.challenges = challenges;
        this.companySettings = companySettings;
        this.stamps = stamps;
        this.tapEvents = tapEvents;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Tokenin vəziyyəti (yan təsiri yoxdur): toxunuş səhifəsi nə göstərəcəyini bilir. */
    public record TapStatus(TapTokenMapper.Status status, UUID branchId, LocalizedText branchName, Instant expiresAt,
                            boolean sameBrowser) {
    }

    public Optional<TapStatus> status(String token, String browserId) {
        Instant now = clock.instant();
        return tokens.find(SecureTokens.hash(token == null ? "" : token)).map(found -> new TapStatus(
                effectiveStatus(found, now), found.branchId(),
                stamps.findBranchName(found.branchId()),
                found.status() == TapTokenMapper.Status.RESERVED ? found.reservedUntil() : found.expiresAt(),
                MessageDigest.isEqual(found.bindingHash(), BrowserIdCookie.bindingHash(browserId))));
    }

    public ClaimResult claim(String token, UUID customerId, String browserId, ClientInfo client) {
        byte[] tokenHash = SecureTokens.hash(token == null ? "" : token);
        Source source = new Source(client.ip(), BrowserIdCookie.bindingHash(browserId),
                BrowserIdCookie.userAgentHash(client.userAgent()));
        Outcome outcome = Objects.requireNonNull(
                tx.execute(status -> claimInTransaction(tokenHash, customerId, source)));
        if (outcome.error() != null) {
            throw new ApiException(outcome.error(), new Object[0], outcome.properties());
        }
        return outcome.result();
    }

    /** Vaxtı keçmiş ACTIVE/RESERVED token fon işi onu yeniləməmişdən əvvəl də EXPIRED sayılır. */
    static TapTokenMapper.Status effectiveStatus(TapToken token, Instant now) {
        if (token.status() == TapTokenMapper.Status.ACTIVE && !token.expiresAt().isAfter(now)) {
            return TapTokenMapper.Status.EXPIRED;
        }
        if (token.status() == TapTokenMapper.Status.RESERVED
                && (token.reservedUntil() == null || !token.reservedUntil().isAfter(now))) {
            return TapTokenMapper.Status.EXPIRED;
        }
        return token.status();
    }

    private Outcome claimInTransaction(byte[] tokenHash, UUID customerId, Source source) {
        Instant now = clock.instant();
        Optional<TapToken> found = tokens.lock(tokenHash);
        if (found.isEmpty()) {
            return Outcome.fail(ErrorCode.TAP_TOKEN_EXPIRED);
        }
        TapToken token = found.get();
        CustomerMapper.Customer customer = customers.findById(customerId)
                .filter(c -> c.status() != CustomerMapper.Status.DELETED)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (customer.status() == CustomerMapper.Status.BLOCKED) {
            return Outcome.fail(ErrorCode.CUSTOMER_BLOCKED);
        }

        switch (effectiveStatus(token, now)) {
            case CONSUMED -> {
                return customerId.equals(token.consumedBy()) ? Outcome.ok(previousResult(token, customerId))
                        : Outcome.fail(ErrorCode.TAP_TOKEN_USED);
            }
            case REJECTED -> {
                return repeatRejection(token, customerId, now);
            }
            case EXPIRED -> {
                return Outcome.fail(ErrorCode.TAP_TOKEN_EXPIRED);
            }
            case RESERVED -> {
                // Rezerv olunmuş token yalnız həmin OTP sorğusunun uğurlu təsdiqi ilə (eyni nömrə) istifadə olunur
                boolean verifiedByOwner = token.reservedChallengeId() != null && challenges.find(token.reservedChallengeId())
                        .filter(state -> "VERIFIED".equals(state.status()))
                        .filter(state -> MessageDigest.isEqual(state.phoneHash(), customer.phoneHash()))
                        .isPresent();
                if (!verifiedByOwner) {
                    return Outcome.fail(ErrorCode.TAP_TOKEN_USED);
                }
            }
            case ACTIVE -> {
                // davam
            }
        }

        if (!MessageDigest.isEqual(token.bindingHash(), source.browserIdHash())) {
            events.record(Event.of(Stage.CLAIM, token.branchId(), token.tagId(), "BINDING_MISMATCH", source)
                    .withClaim(customerId, token.id(), null), now);
            fraudFlags.raise(FraudFlags.Type.BINDING_MISMATCH, FraudFlags.Severity.MEDIUM, token.tagId(), customerId,
                    Map.of("tapTokenId", token.id().toString()), now);
            return Outcome.fail(ErrorCode.TAP_BINDING_MISMATCH);
        }

        ledger.lockAccount(customerId, now);
        StampPolicy policy = ledger.policy(token.branchId());
        ZoneId zone = companySettings.current().timezone();
        Instant tapAt = token.createdAt();
        LocalDate day = tapAt.atZone(zone).toLocalDate();

        Optional<Instant> last = ledger.lastTapStampAt(customerId);
        if (last.isPresent() && tapAt.isBefore(last.get().plus(policy.minInterval()))) {
            Outcome outcome = reject(token, customerId, source, ErrorCode.STAMP_COOLDOWN_ACTIVE,
                    retryAfter(now, last.get().plus(policy.minInterval())), now);
            detectCooldownHammering(token, customerId, now);
            return outcome;
        }
        if (ledger.tapStampsOn(customerId, day) >= policy.maxStampsPerDay()) {
            return reject(token, customerId, source, ErrorCode.STAMP_DAILY_LIMIT_REACHED,
                    retryAfter(now, nextDayStart(now, zone)), now);
        }
        if (policy.enforceBusinessHours()
                && !BusinessHours.isOpen(ledger.businessHours(token.branchId()).orElse(null), tapAt.atZone(zone))) {
            return reject(token, customerId, source, ErrorCode.OUTSIDE_BUSINESS_HOURS, Map.of(), now);
        }

        StampStatus status = policy.requireStaffApproval() ? StampStatus.PENDING : StampStatus.CONFIRMED;
        UUID stampId = ledger.insertTapStamp(token.branchId(), customerId, token.tagId(), token.id(), status, day, tapAt);
        CardState card = status == StampStatus.CONFIRMED ? ledger.applyConfirmed(customerId, stampId, tapAt)
                : ledger.card(customerId);
        TierService.TierBonus tierBonus = TierService.TierBonus.NONE;
        if (status == StampStatus.CONFIRMED) {
            card = withBonus(card, bonusRules.onTapStampConfirmed(customerId, token.branchId(), tapAt, tapAt));
            tierBonus = tiers.onTapStampConfirmed(customerId, token.branchId(), tapAt, tapAt);
            card = withBonus(card, tierBonus.card());
            card = withBonus(card, referrals.onTapStampConfirmed(customerId, token.branchId(), tapAt));
            feedback.onVisit(customerId, token.branchId(), tapAt);
        }
        tokens.markConsumed(token.id(), customerId, now);
        ledger.touchCustomerVisit(customerId, token.branchId(), tapAt);
        events.record(Event.of(Stage.CLAIM, token.branchId(), token.tagId(), null, source)
                .withClaim(customerId, token.id(), stampId), now);
        detectMultiAccount(token, customerId, source, now);
        return Outcome.ok(new ClaimResult(stampId, status, card.currentStamps(), card.stampsRequired(),
                card.giftCardId(), token.branchId(), tierBonus.reached() == null ? null : tierBonus.reached().name(),
                tierBonus.giftCardId(), tierBonus.reachStamps()));
    }

    /** Bonus (qayda və ya dəvət) verilibsə, kart bonusdan sonrakı vəziyyətdir; hədiyyə kartı — ilk yaranan. */
    static CardState withBonus(CardState card, @Nullable CardState bonus) {
        if (bonus == null) {
            return card;
        }
        return new CardState(bonus.currentStamps(), bonus.stampsRequired(),
                card.giftCardId() != null ? card.giftCardId() : bonus.giftCardId());
    }

    private ClaimResult previousResult(TapToken token, UUID customerId) {
        StampLedger.Stamp stamp = ledger.findByTapToken(token.id())
                .orElseThrow(() -> new IllegalStateException("Consumed token without stamp: " + token.id()));
        CardState card = ledger.card(customerId);
        return new ClaimResult(stamp.id(), stamp.status(), card.currentStamps(), card.stampsRequired(),
                stamp.rewardId(), stamp.branchId(), null, null, 0);
    }

    /** Səhifə yenilənəndə eyni rədd səbəbi (məs. cooldown-un qalan vaxtı ilə) göstərilir. */
    private Outcome repeatRejection(TapToken token, UUID customerId, Instant now) {
        Optional<String> reason = events.lastClaimRejection(token.id(), customerId);
        if (reason.isEmpty()) {
            return Outcome.fail(ErrorCode.TAP_TOKEN_USED);
        }
        StampPolicy policy = ledger.policy(token.branchId());
        ZoneId zone = companySettings.current().timezone();
        return switch (reason.get()) {
            case "STAMP_COOLDOWN_ACTIVE" -> Outcome.fail(ErrorCode.STAMP_COOLDOWN_ACTIVE,
                    ledger.lastTapStampAt(customerId)
                            .map(last -> retryAfter(now, last.plus(policy.minInterval())))
                            .orElse(Map.of()));
            case "STAMP_DAILY_LIMIT_REACHED" -> Outcome.fail(ErrorCode.STAMP_DAILY_LIMIT_REACHED,
                    retryAfter(now, nextDayStart(now, zone)));
            case "OUTSIDE_BUSINESS_HOURS" -> Outcome.fail(ErrorCode.OUTSIDE_BUSINESS_HOURS);
            default -> Outcome.fail(ErrorCode.TAP_TOKEN_USED);
        };
    }

    private Outcome reject(TapToken token, UUID customerId, Source source, ErrorCode code,
                           Map<String, Object> properties, Instant now) {
        tokens.markRejected(token.id());
        events.record(Event.of(Stage.CLAIM, token.branchId(), token.tagId(), code.name(), source)
                .withClaim(customerId, token.id(), null), now);
        return Outcome.fail(code, properties);
    }

    private void detectCooldownHammering(TapToken token, UUID customerId, Instant now) {
        int recent = tapEvents.countCooldownRejections(customerId, now.minus(Duration.ofHours(1)));
        if (recent >= COOLDOWN_HAMMERING_THRESHOLD) {
            fraudFlags.raise(FraudFlags.Type.COOLDOWN_HAMMERING, FraudFlags.Severity.MEDIUM, token.tagId(), customerId,
                    Map.of("attemptsLastHour", recent), now);
        }
    }

    /**
     * Bir brauzerdən bir gündə çoxlu fərqli hesab möhür alıbsa — işarə. (Bir çipdə çoxlu müştəri isə normaldır:
     * piştaxtada növbə ola bilər.)
     */
    private void detectMultiAccount(TapToken token, UUID customerId, Source source, Instant now) {
        int accounts = tapEvents.countAccountsForBrowser(source.browserIdHash(), now.minus(Duration.ofDays(1)));
        if (accounts >= MULTI_ACCOUNT_THRESHOLD) {
            fraudFlags.raise(FraudFlags.Type.RAPID_MULTI_ACCOUNT, FraudFlags.Severity.HIGH, token.tagId(), customerId,
                    Map.of("accountsFromBrowserLastDay", accounts), now);
        }
    }

    private static Map<String, Object> retryAfter(Instant now, Instant until) {
        return Map.of("retryAfterSeconds", Math.max(1, Duration.between(now, until).toSeconds() + 1));
    }

    private static Instant nextDayStart(Instant now, ZoneId zone) {
        return now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
    }
}
