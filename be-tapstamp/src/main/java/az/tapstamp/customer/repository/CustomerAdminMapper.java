package az.tapstamp.customer.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.customer.admin.CustomerAdminService.FraudFlag;
import az.tapstamp.shared.persistence.TenantMapper;

/**
 * Admin panelinin müştəri əməliyyatları: tam nömrə (şifrəli), möhürlərin əl ilə düzəlişi (idempotent), bloklama,
 * fırıldaq işarələri. SQL: {@code mapper/tenant/customer/CustomerAdminMapper.xml}.
 */
@TenantMapper
public interface CustomerAdminMapper {

    /** Əvvəlki düzəlişin nəticəsi (eyni {@code Idempotency-Key}). */
    record StoredAdjustment(byte[] requestHash, int currentStamps, int totalStamps, int giftCardsIssued,
                            int giftCardsRevoked) {
    }

    /** Ləğv ediləcək hədiyyə kartı (sətir kilidlənir). */
    record RevocableReward(UUID id, int stampsRequired) {
    }

    record CardTotals(int currentStamps, int totalStamps) {
    }

    Optional<byte[]> findEncryptedPhone(UUID customerId);

    Optional<StoredAdjustment> findAdjustment(String scope, String key);

    void saveAdjustment(String scope, String key, byte[] requestHash, String responseBody, Instant now,
                        Instant expiresAt);

    /** İstifadə olunmamış son hədiyyə kartı (kilidlənir). */
    Optional<RevocableReward> lockLatestAvailableReward(UUID customerId);

    void revokeReward(UUID rewardId);

    void unlinkStampsFromReward(UUID rewardId);

    /** Ləğv edilmiş kartın möhürləri karta qaytarılır. */
    void restoreCardStamps(UUID customerId, int stamps, Instant now);

    /** Son {@code count} təsdiqlənmiş (hədiyyəyə bağlanmamış) möhür REVOKED olur. */
    void revokeLatestStamps(UUID customerId, int count, UUID staffId, String reason, Instant now);

    void deductCardStamps(UUID customerId, int count, Instant now);

    int currentStamps(UUID customerId);

    CardTotals cardTotals(UUID customerId);

    void updateStatus(UUID customerId, String status, Instant now);

    List<FraudFlag> fraudFlags(UUID customerId);

    Optional<UUID> findFraudFlagCustomer(UUID flagId);

    /** Yalnız açıq işarə bağlanır; bağlanan sətirlərin sayı qaytarılır (idempotent). */
    int resolveFraudFlag(UUID flagId, UUID staffId, String note, Instant now);
}
