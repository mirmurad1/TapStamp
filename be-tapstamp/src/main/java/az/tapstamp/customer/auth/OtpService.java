package az.tapstamp.customer.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.company.service.CompanySettings;
import az.tapstamp.customer.phone.PhoneNumberService;
import az.tapstamp.customer.repository.CustomerMapper;
import az.tapstamp.customer.repository.OtpChallengeMapper.Purpose;
import az.tapstamp.customer.repository.OtpChallengeMapper;
import az.tapstamp.nfc.BrowserIdCookie;
import az.tapstamp.nfc.repository.TapTokenMapper;
import az.tapstamp.notification.sms.SmsMessage;
import az.tapstamp.notification.sms.SmsProvider;
import az.tapstamp.notification.sms.SmsTemplates;
import az.tapstamp.security.crypto.Aad;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.security.crypto.KeyPurpose;
import az.tapstamp.security.crypto.PhoneHasher;
import az.tapstamp.security.ratelimit.RateLimitPolicy;
import az.tapstamp.security.ratelimit.RateLimiter;
import az.tapstamp.security.token.SecureTokens;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.web.ClientInfo;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.TenantInfo;
import az.tapstamp.tenancy.registry.TenantDirectory;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SMS ilə birdəfəlik kod (OTP).
 *
 * <ul>
 *   <li>Göndəriş: rate limit (IP, telefon — 15 dəqiqədə 3 və gündə 10, şirkət, qlobal) və 60 saniyəlik gözləmə; əvvəlki
 *       aktiv kod etibarsız olur; kodun özü saxlanılmır (yalnız HMAC); SMS tranzaksiyadan sonra arxa planda göndərilir.
 *       Cavab nömrənin qeydiyyatda olub-olmamasından asılı deyil.</li>
 *   <li>Yoxlama: cəhd sayı atomik artırılır (5 cəhd), sabit zamanlı müqayisə, telefon üzrə gündə 10 uğursuz cəhd.
 *       Qeydiyyat statusu yalnız kod təsdiqlənəndən sonra — nömrəyə sahiblik sübut olunanda bəlli olur.</li>
 * </ul>
 */
@Service
public class OtpService {

    public record Issued(UUID challengeId, Instant expiresAt, Instant resendAvailableAt) {
    }

    /** Giriş kodunun təsdiqinin nəticəsi: mövcud müştəri və ya yeni nömrə (qeydiyyat tələb olunur). */
    public sealed interface LoginResult {
        record Existing(UUID customerId) implements LoginResult {
        }

        record RegistrationRequired(String registrationToken, Instant expiresAt) implements LoginResult {
            @Override
            public @NonNull String toString() {
                return "RegistrationRequired[expiresAt=" + expiresAt + "]";
            }
        }
    }

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final String GLOBAL_SCOPE = "global";
    /** Toxunuş tokeni SMS gələnə və kod daxil edilənə qədər saxlanılır. */
    static final Duration TAP_RESERVATION = Duration.ofMinutes(10);

    private final OtpChallengeMapper challenges;
    private final TapTokenMapper tapTokens;
    private final OtpCodes codes;
    private final CustomerMapper customers;
    private final PhoneNumberService phoneNumbers;
    private final PhoneHasher phoneHasher;
    private final EnvelopeCipher cipher;
    private final RateLimiter rateLimiter;
    private final CompanySettings companySettings;
    private final TenantDirectory directory;
    private final SmsProvider sms;
    private final SmsTemplates templates;
    private final TaskExecutor executor;
    private final AuditLogger audit;
    private final CustomerAuthProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OtpService(OtpChallengeMapper challenges, TapTokenMapper tapTokens, OtpCodes codes, CustomerMapper customers,
                      PhoneNumberService phoneNumbers, PhoneHasher phoneHasher,
                      @Qualifier("tenantCipher") EnvelopeCipher cipher, RateLimiter rateLimiter,
                      CompanySettings companySettings, TenantDirectory directory, SmsProvider sms,
                      SmsTemplates templates, @Qualifier("applicationTaskExecutor") TaskExecutor executor, AuditLogger audit,
                      CustomerAuthProperties properties, PlatformTransactionManager transactionManager, Clock clock) {
        this.challenges = challenges;
        this.tapTokens = tapTokens;
        this.codes = codes;
        this.customers = customers;
        this.phoneNumbers = phoneNumbers;
        this.phoneHasher = phoneHasher;
        this.cipher = cipher;
        this.rateLimiter = rateLimiter;
        this.companySettings = companySettings;
        this.directory = directory;
        this.sms = sms;
        this.templates = templates;
        this.executor = executor;
        this.audit = audit;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ------------------------------------------------------------------ Giriş

    /**
     * @param tapToken  toxunuşdan sonra girişə başlayan qonağın tokeni (istəyə bağlı) — 10 dəqiqəlik bu kod sorğusuna
     *                  rezerv olunur; yalnız eyni brauzerdən. Token etibarsızdırsa giriş yenə davam edir.
     * @param browserId brauzer identifikatoru ({@code __Host-cl_bid})
     */
    public Issued requestLogin(String rawPhone, Language language, ClientInfo client, String tapToken,
                               String browserId) {
        CompanySettings.Settings settings = companySettings.current();
        String phone = phoneNumbers.normalize(rawPhone, settings.allowedPhoneRegions());
        return issue(phone, language, Purpose.LOGIN, null, settings, client, tapToken, browserId);
    }

    public LoginResult verifyLogin(UUID challengeId, String code, ClientInfo client) {
        OtpChallengeMapper.Attempt attempt = verify(challengeId, code, Purpose.LOGIN, null, client);
        Instant now = clock.instant();
        Optional<CustomerMapper.Customer> customer = customers.findByPhoneHash(attempt.phoneHash());
        if (customer.isPresent()) {
            if (customer.get().status() == CustomerMapper.Status.BLOCKED) {
                throw new ApiException(ErrorCode.CUSTOMER_BLOCKED);
            }
            return new LoginResult.Existing(customer.get().id());
        }
        String token = SecureTokens.generate();
        Instant expiresAt = now.plus(properties.registrationTtl());
        challenges.setRegistrationToken(challengeId, SecureTokens.hash(token), expiresAt);
        return new LoginResult.RegistrationRequired(token, expiresAt);
    }

    // ------------------------------------------------------------------ Step-up (həssas əməliyyatlar)

    public Issued requestStepUp(CustomerPrincipal principal, ClientInfo client) {
        CustomerMapper.Customer customer = customers.findById(principal.customerId())
                .filter(c -> c.status() == CustomerMapper.Status.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        String phone = cipher.decryptString(customer.phoneEnc(), customerPhoneAad(customer.id()));
        return issue(phone, customer.language(), Purpose.STEP_UP, customer.id(), companySettings.current(), client,
                null, null);
    }

    public void verifyStepUp(CustomerPrincipal principal, UUID challengeId, String code, ClientInfo client) {
        verify(challengeId, code, Purpose.STEP_UP, principal.customerId(), client);
    }

    // ------------------------------------------------------------------ Ümumi məntiq

    private Issued issue(String phone, Language language, Purpose purpose, UUID customerId,
                         CompanySettings.Settings settings, ClientInfo client, String tapToken, String browserId) {
        TenantInfo tenant = TenantContext.require();
        String scope = "t:" + tenant.id();
        Instant now = clock.instant();
        byte[] phoneHash = phoneHasher.hash(tenant.id(), phone);

        rateLimiter.consume(RateLimitPolicy.OTP_REQUEST_PER_IP, scope, client.ip());
        Optional<Instant> last = challenges.lastCreatedAt(phoneHash, purpose);
        if (last.isPresent() && last.get().plus(properties.otpResendCooldown()).isAfter(now)) {
            throw RateLimiter.rateLimited(Duration.between(now, last.get().plus(properties.otpResendCooldown())).toNanos());
        }
        rateLimiter.consume(RateLimitPolicy.OTP_REQUEST_PER_PHONE, scope, phone);
        rateLimiter.consume(RateLimitPolicy.OTP_REQUEST_PER_PHONE_DAILY, scope, phone);
        rateLimiter.consume(RateLimitPolicy.OTP_REQUEST_PER_TENANT, scope, "all");
        rateLimiter.consume(RateLimitPolicy.OTP_REQUEST_GLOBAL, GLOBAL_SCOPE, "all");

        UUID id = Ids.newId();
        String code = codes.generate();
        Instant expiresAt = now.plus(properties.otpTtl());
        OtpChallengeMapper.NewChallenge challenge = new OtpChallengeMapper.NewChallenge(id, phoneHash,
                cipher.encryptString(KeyPurpose.PII, phone, Aad.tenantField(tenant.id(), "otp_challenges", "phone_enc", id)),
                codes.hash(id, code), language, properties.otpMaxAttempts(), purpose, customerId, client.ip(), now,
                expiresAt);
        tx.executeWithoutResult(status -> {
            challenges.supersedeActive(phoneHash, purpose);
            challenges.insert(challenge);
            if (tapToken != null && !tapToken.isBlank()) {
                tapTokens.reserve(SecureTokens.hash(tapToken), BrowserIdCookie.bindingHash(browserId), id,
                        now.plus(TAP_RESERVATION), now)
                        .ifPresent(tokenId -> challenges.linkTapToken(id, tokenId));
            }
        });

        // SMS yalnız commit-dən sonra və arxa planda: cavab vaxtı provayderin sürətindən asılı deyil
        String companyName = settings.smsSenderName() != null ? settings.smsSenderName() : settings.name().resolve(language);
        String host = directory.primaryHost(tenant.id()).orElse(null);
        SmsMessage message = new SmsMessage(phone, templates.otp(language, companyName, code, host),
                settings.smsSenderName(), tenant.id().toString());
        executor.execute(() -> {
            try {
                sms.send(message);
            } catch (RuntimeException e) {
                log.warn("OTP SMS göndərilmədi: {} ({})", message, e.getClass().getSimpleName());
            }
        });
        return new Issued(id, expiresAt, now.plus(properties.otpResendCooldown()));
    }

    private OtpChallengeMapper.Attempt verify(UUID challengeId, String code, Purpose purpose, UUID customerId,
                                             ClientInfo client) {
        TenantInfo tenant = TenantContext.require();
        String scope = "t:" + tenant.id();
        Instant now = clock.instant();
        rateLimiter.consume(RateLimitPolicy.OTP_VERIFY_PER_IP, scope, client.ip());

        Optional<OtpChallengeMapper.Attempt> found = challenges.attempt(challengeId, purpose, customerId, now);
        if (found.isEmpty()) {
            throw new ApiException(challenges.find(challengeId)
                    .filter(state -> "LOCKED".equals(state.status()) || state.attempts() >= state.maxAttempts())
                    .isPresent() ? ErrorCode.OTP_ATTEMPTS_EXCEEDED : ErrorCode.OTP_EXPIRED);
        }
        OtpChallengeMapper.Attempt attempt = found.get();
        String phoneKey = HexFormat.of().formatHex(attempt.phoneHash());
        if (rateLimiter.isExhausted(RateLimitPolicy.OTP_VERIFY_FAILURE_PER_PHONE, scope, phoneKey)) {
            challenges.markLocked(attempt.id());
            throw new ApiException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        }
        if (!codes.matches(attempt.id(), code == null ? null : code.strip(), attempt.codeHash())) {
            rateLimiter.tryConsume(RateLimitPolicy.OTP_VERIFY_FAILURE_PER_PHONE, scope, phoneKey);
            if (attempt.attempts() >= attempt.maxAttempts()) {
                challenges.markLocked(attempt.id());
                audit.tenant("CUSTOMER_OTP_LOCKED", AuditLogger.ActorType.SYSTEM, null, "otp_challenge", attempt.id(),
                        Map.of("purpose", purpose.name()));
                throw new ApiException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
            }
            throw new ApiException(ErrorCode.OTP_INVALID, new Object[0],
                    Map.of("attemptsRemaining", attempt.maxAttempts() - attempt.attempts()));
        }
        if (!challenges.markVerified(attempt.id(), now)) {
            // Paralel ikinci düzgün cavab — kod artıq istifadə olunub
            throw new ApiException(ErrorCode.OTP_EXPIRED);
        }
        return attempt;
    }

    public static String customerPhoneAad(UUID customerId) {
        return Aad.tenantField(TenantContext.require().id(), "customers", "phone_enc", customerId);
    }
}
