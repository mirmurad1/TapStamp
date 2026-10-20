package az.tapstamp.customer.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.customer.consent.LegalDocumentService.DocumentRef;
import az.tapstamp.customer.consent.LegalDocumentService;
import az.tapstamp.customer.repository.CustomerMapper;
import az.tapstamp.customer.repository.OtpChallengeMapper;
import az.tapstamp.referral.ReferralService;
import az.tapstamp.security.crypto.Aad;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.security.crypto.KeyPurpose;
import az.tapstamp.security.token.SecureTokens;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.pii.PhoneMasker;
import az.tapstamp.shared.web.ClientInfo;
import az.tapstamp.tenancy.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Yeni müştərinin qeydiyyatı: OTP ilə təsdiqlənmiş nömrə (qeydiyyat tokeni, 10 dəqiqə, bir dəfə), istəyə bağlı ad,
 * məcburi razılıqlar və dil; dəvət linki ilə gəlibsə — dost dəvəti qeydə alınır. Hamısı bir tranzaksiyada; razılıq
 * çatışmırsa, heç nə yaradılmır və token qüvvədə qalır.
 */
@Service
public class RegistrationService {

    private final OtpChallengeMapper challenges;
    private final CustomerMapper customers;
    private final LegalDocumentService legal;
    private final EnvelopeCipher cipher;
    private final AuditLogger audit;
    private final ReferralService referrals;
    private final TransactionTemplate tx;
    private final Clock clock;

    public RegistrationService(OtpChallengeMapper challenges, CustomerMapper customers, LegalDocumentService legal,
                               @Qualifier("tenantCipher") EnvelopeCipher cipher, AuditLogger audit, ReferralService referrals,
                               PlatformTransactionManager transactionManager, Clock clock) {
        this.challenges = challenges;
        this.customers = customers;
        this.legal = legal;
        this.cipher = cipher;
        this.audit = audit;
        this.referrals = referrals;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public UUID register(String registrationToken, String name, Collection<DocumentRef> acceptedDocuments,
                         Language language, ClientInfo client, @Nullable String referralCode) {
        Instant now = clock.instant();
        UUID tenantId = TenantContext.require().id();
        return tx.execute(status -> {
            OtpChallengeMapper.Registration registration = challenges
                    .consumeRegistrationToken(SecureTokens.hash(registrationToken == null ? "" : registrationToken), now)
                    .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
            // Eyni nömrə bu arada başqa tabdan qeydiyyatdan keçibsə — nömrəyə sahiblik sübut olunub, həmin hesaba daxil olur
            Optional<CustomerMapper.Customer> existing = customers.findByPhoneHash(registration.phoneHash());
            if (existing.isPresent()) {
                if (existing.get().status() == CustomerMapper.Status.BLOCKED) {
                    throw new ApiException(ErrorCode.CUSTOMER_BLOCKED);
                }
                legal.accept(existing.get().id(), acceptedDocuments, language, client);
                return existing.get().id();
            }
            String phone = cipher.decryptString(registration.phoneEnc(),
                    Aad.tenantField(tenantId, "otp_challenges", "phone_enc", registration.challengeId()));
            UUID id = Ids.newId();
            customers.insert(id,
                    cipher.encryptString(KeyPurpose.PII, phone, Aad.tenantField(tenantId, "customers", "phone_enc", id)),
                    registration.phoneHash(), PhoneMasker.mask(phone), name == null || name.isBlank() ? null : name.strip(),
                    language, now);
            legal.accept(id, acceptedDocuments, language, client);
            audit.tenant("CUSTOMER_REGISTERED", AuditLogger.ActorType.CUSTOMER, id, "customer", id,
                    Map.of("language", language.tag()));
            // Yalnız yeni müştəri dəvət oluna bilər (mövcud nömrə yuxarıda qayıdır)
            referrals.attach(id, referralCode, now);
            return id;
        });
    }
}
