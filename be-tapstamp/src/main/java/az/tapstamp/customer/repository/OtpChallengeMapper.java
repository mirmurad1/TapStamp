package az.tapstamp.customer.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.persistence.TenantMapper;

/** SMS kodları ({@code otp_challenges}, cari tenant-ın bazası). SQL: {@code mapper/tenant/customer/OtpChallengeMapper.xml}. */
@TenantMapper
public interface OtpChallengeMapper {

    enum Purpose {
        LOGIN,
        STEP_UP
    }

    record NewChallenge(UUID id, byte[] phoneHash, byte[] phoneEnc, byte[] codeHash, Language language,
                        int maxAttempts, Purpose purpose, UUID customerId, String ip, Instant createdAt,
                        Instant expiresAt, boolean newNumber) {
    }

    /** Cəhd sayı artırılandan sonrakı vəziyyət (kod hələ yoxlanılmayıb). */
    record Attempt(UUID id, byte[] codeHash, byte[] phoneHash, Language language, int attempts, int maxAttempts,
                   UUID customerId) {
    }

    record State(String status, int attempts, int maxAttempts, Instant expiresAt, byte[] phoneHash) {
    }

    record Registration(UUID challengeId, byte[] phoneHash, byte[] phoneEnc, Language language) {
    }

    Optional<Instant> lastCreatedAt(byte[] phoneHash, Purpose purpose);

    /** Eyni nömrə üçün əvvəlki aktiv kod etibarsız olur — yalnız sonuncu kod işləyir. */
    void supersedeActive(byte[] phoneHash, Purpose purpose);

    void insert(NewChallenge challenge);

    /** {@code since}-dən bəri yeni (qeydiyyatsız) nömrələrə göndərilən giriş kodları — şirkətin gündəlik SMS limiti. */
    long countNewNumbersSince(Instant since);

    /**
     * Cəhd sayını atomik artırır. Yalnız aktiv, vaxtı keçməmiş və limiti dolmamış kod üçün işləyir — paralel sorğularla
     * cəhd limitini keçmək mümkün deyil (hər sorğu ayrıca sayılır, sətir kilidlənir).
     */
    Optional<Attempt> attempt(UUID id, Purpose purpose, UUID customerId, Instant now);

    Optional<State> find(UUID id);

    int markVerifiedRow(UUID id, Instant now);

    /** Yalnız bir dəfə: paralel iki düzgün cavabdan yalnız biri uğurlu olur. */
    default boolean markVerified(UUID id, Instant now) {
        return markVerifiedRow(id, now) == 1;
    }

    void linkTapToken(UUID id, UUID tapTokenId);

    void markLocked(UUID id);

    void setRegistrationToken(UUID id, byte[] tokenHash, Instant expiresAt);

    /** Qeydiyyat tokenini atomik istifadə edir (bir dəfə). Tranzaksiya geri qaytarılarsa, token qüvvədə qalır. */
    Optional<Registration> consumeRegistrationToken(byte[] tokenHash, Instant now);

    /** Hesab silinəndə: həmin nömrənin OTP izləri (şifrəli nömrə daxil) qalmır. */
    int deleteForCustomer(UUID customerId, byte[] phoneHash);
}
