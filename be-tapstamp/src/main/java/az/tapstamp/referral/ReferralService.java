package az.tapstamp.referral;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.company.service.CompanySettings;
import az.tapstamp.customer.auth.CustomerPrincipal;
import az.tapstamp.loyalty.stamp.StampLedger;
import az.tapstamp.loyalty.stamp.StampLedger.CardState;
import az.tapstamp.referral.repository.ReferralMapper;
import az.tapstamp.referral.repository.ReferralMapper.Claimed;
import az.tapstamp.referral.repository.ReferralMapper.ReferralRow;
import az.tapstamp.referral.repository.ReferralMapper.ReferralSettings;
import az.tapstamp.referral.repository.ReferralMapper.Stats;
import az.tapstamp.referral.repository.ReferralMapper.ReferralSummary;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Dost dəvəti (referral). Müştəri şəxsi kodu ilə link paylaşır; dost həmin linklə <b>yeni</b> müştəri kimi qeydiyyatdan
 * keçəndə dəvət yaranır (PENDING). Dost <b>ilk təsdiqlənmiş NFC möhürünü</b> alanda (yəni real gəlişdə) hər ikisinə
 * bonus möhür verilir — eyni tranzaksiyada və yalnız bir dəfə. Qorunma: yalnız yeni müştəri, özünü dəvət etmək olmaz,
 * dəvət edənin aylıq limiti, dəvətin etibarlılıq müddəti; bonus saxta qeydiyyata yox, gəlişə verilir.
 */
@Service
public class ReferralService {

    /** Kodda qarışdırıla bilən simvollar yoxdur (0/O, 1/I). */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final Pattern CODE = Pattern.compile("^[A-Z2-9]{8}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public record ProgramInfo(boolean enabled, int referrerStamps, int friendStamps) {
    }

    public record CustomerReferral(boolean enabled, @Nullable String code, int referrerStamps, int friendStamps,
                                   int monthlyCap, int validDays, int invited, int rewarded, int pending) {
    }

    private final ReferralMapper referrals;
    private final StampLedger ledger;
    private final CompanySettings company;
    private final AuditLogger audit;
    private final Clock clock;

    public ReferralService(ReferralMapper referrals, StampLedger ledger, CompanySettings company, AuditLogger audit,
                           Clock clock) {
        this.referrals = referrals;
        this.ledger = ledger;
        this.company = company;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- müştəri

    /** Dəvət səhifəsi üçün (giriş tələb etmir): proqram aktivdirmi və neçə bonus möhür. */
    public ProgramInfo program() {
        ReferralSettings settings = referrals.settings();
        return new ProgramInfo(settings.enabled(), settings.referrerStamps(), settings.friendStamps());
    }

    /** Müştərinin dəvət kodu (lazım olanda yaranır) və dəvətləri üzrə saylar. */
    public CustomerReferral forCustomer(CustomerPrincipal principal) {
        ReferralSettings settings = referrals.settings();
        if (!settings.enabled()) {
            return new CustomerReferral(false, null, settings.referrerStamps(), settings.friendStamps(),
                    settings.monthlyCap(), settings.validDays(), 0, 0, 0);
        }
        String code = codeFor(principal.customerId());
        Stats stats = referrals.stats(principal.customerId(), clock.instant());
        return new CustomerReferral(true, code, settings.referrerStamps(), settings.friendStamps(),
                settings.monthlyCap(), settings.validDays(), stats.invited(), stats.rewarded(), stats.pending());
    }

    private String codeFor(UUID customerId) {
        Optional<String> existing = referrals.referralCode(customerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                // 0 qaytarırsa, paralel sorğu artıq kod yazıb — o da yararlıdır
                referrals.setReferralCode(customerId, newCode());
                break;
            } catch (DuplicateKeyException e) {
                // Eyni kod başqa müştəridə var — yenisini yaradırıq
            }
        }
        return referrals.referralCode(customerId).orElseThrow(() -> new IllegalStateException("Referral code"));
    }

    private static String newCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * Qeydiyyat tranzaksiyasında: yeni müştəri dəvət linki ilə gəlibsə, dəvəti qeydə alır. Kod etibarsızdırsa və ya
     * proqram söndürülübsə, qeydiyyat adi qaydada davam edir (heç bir xəta göstərilmir).
     */
    public void attach(UUID friendId, @Nullable String rawCode, Instant now) {
        if (rawCode == null || rawCode.isBlank()) {
            return;
        }
        String code = rawCode.strip().toUpperCase(Locale.ROOT);
        ReferralSettings settings = referrals.settings();
        if (!settings.enabled() || !CODE.matcher(code).matches()) {
            return;
        }
        Optional<UUID> referrer = referrals.findActiveByCode(code);
        if (referrer.isEmpty() || referrer.get().equals(friendId)) {
            return;
        }
        ZoneId zone = company.current().timezone();
        Instant monthStart = LocalDate.ofInstant(now, zone).withDayOfMonth(1).atStartOfDay(zone).toInstant();
        boolean limitReached = referrals.countSince(referrer.get(), monthStart) >= settings.monthlyCap();
        UUID id = Ids.newId();
        referrals.insert(id, referrer.get(), friendId, limitReached ? "LIMIT_REACHED" : "PENDING", now,
                now.plus(Duration.ofDays(settings.validDays())));
        audit.tenant(limitReached ? "REFERRAL_LIMIT_REACHED" : "REFERRAL_CREATED", AuditLogger.ActorType.CUSTOMER,
                friendId, "referral", id, Map.of("referrerId", referrer.get().toString()));
    }

    /**
     * Dostun NFC möhürü təsdiqlənəndə (eyni tranzaksiyada, hesab kilidlidir): gözləyən dəvət varsa, hər ikisinə bonus
     * möhür verilir. Bonus yalnız bir dəfə verilir — dəvət atomik olaraq REWARDED olur.
     *
     * @return dostun bonusdan sonrakı kartı; bonus verilməyibsə {@code null}
     */
    public @Nullable CardState onTapStampConfirmed(UUID friendId, UUID branchId, Instant at) {
        ReferralSettings settings = referrals.settings();
        if (!settings.enabled()) {
            return null;
        }
        Optional<Claimed> claimed = referrals.claimPending(friendId, at);
        if (claimed.isEmpty()) {
            return null;
        }
        LocalDate day = LocalDate.ofInstant(at, company.current().timezone());
        UUID referralId = claimed.get().id();
        CardState friendCard = grant(friendId, branchId, referralId, settings.friendStamps(), day, at);
        UUID referrerId = claimed.get().referrerId();
        boolean referrerRewarded = referrals.isActive(referrerId);
        if (referrerRewarded) {
            // Dəvət edən həmişə dostdan əvvəl qeydiyyatdan keçib — kilid sırası tsikl yaratmır
            grant(referrerId, branchId, referralId, settings.referrerStamps(), day, at);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("referrerId", referrerId.toString());
        details.put("friendId", friendId.toString());
        details.put("friendStamps", settings.friendStamps());
        details.put("referrerStamps", referrerRewarded ? settings.referrerStamps() : 0);
        audit.tenant("REFERRAL_REWARDED", AuditLogger.ActorType.SYSTEM, null, "referral", referralId, details);
        return friendCard;
    }

    private CardState grant(UUID customerId, UUID branchId, UUID referralId, int count, LocalDate day, Instant at) {
        ledger.lockAccount(customerId, at);
        CardState card = null;
        UUID giftCardId = null;
        for (int i = 0; i < count; i++) {
            UUID stampId = ledger.insertReferralStamp(branchId, customerId, referralId, day, at);
            card = ledger.applyConfirmed(customerId, stampId, at);
            if (giftCardId == null) {
                giftCardId = card.giftCardId();
            }
        }
        return new CardState(card.currentStamps(), card.stampsRequired(), giftCardId);
    }

    // ---------------------------------------------------------------- admin (şirkət sahibi)

    public ReferralSettings settings(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        return referrals.settings();
    }

    public ReferralSettings updateSettings(AdminPrincipal principal, ReferralSettings next) {
        AccessPolicy.requireCompanyWide(principal);
        if (next.referrerStamps() < 1 || next.referrerStamps() > 3 || next.friendStamps() < 1 || next.friendStamps() > 3
                || next.monthlyCap() < 1 || next.monthlyCap() > 50 || next.validDays() < 7 || next.validDays() > 90) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        ReferralSettings before = referrals.settings();
        referrals.updateSettings(next.enabled(), next.referrerStamps(), next.friendStamps(), next.monthlyCap(),
                next.validDays(), principal.userId(), clock.instant());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("before", before);
        details.put("after", next);
        audit.tenant("REFERRAL_SETTINGS_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "referral_settings",
                "company", details);
        return referrals.settings();
    }

    public PageResult<ReferralRow> list(AdminPrincipal principal, PageQuery page) {
        AccessPolicy.requireCompanyWide(principal);
        return page.result(referrals.list(clock.instant(), page.limit(), page.offset()), referrals.count());
    }

    public ReferralSummary summary(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        return referrals.summary(clock.instant());
    }
}
