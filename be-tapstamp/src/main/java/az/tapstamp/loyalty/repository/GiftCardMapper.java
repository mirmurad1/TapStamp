package az.tapstamp.loyalty.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.loyalty.reward.GiftCardService.ActiveRule;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.TenantMapper;

/**
 * Hədiyyə kartları ({@code rewards}) və onların qaydası ({@code reward_rules}): verilmə, müştərinin siyahısı, işçinin
 * yoxlaması və təhvili. SQL: {@code mapper/tenant/loyalty/GiftCardMapper.xml}.
 */
@TenantMapper
public interface GiftCardMapper {

    /** Müştərinin kartı (kod {@code codeNonce}-dan hesablanır, bazada açıq saxlanılmır). */
    record GiftCardRow(UUID id, LocalizedText name, String status, Instant earnedAt, Instant expiresAt,
                       Instant redeemedAt, int codeNonce) {
    }

    /** İşçinin yoxladığı kart: müştəri (telefon maskalı) və təhvil məlumatı ilə. */
    record RedemptionRow(UUID id, int codeNonce, String status, LocalizedText name, Instant earnedAt, Instant expiresAt,
                         Instant redeemedAt, UUID redeemedBy, UUID redeemedBranchId, String customerName,
                         String phoneMasked, LocalizedText branchName, UUID customerId) {
    }

    Optional<ActiveRule> findActiveRule();

    /** Kartın qaydası: hədd dəyişəndə köhnə qaydaya bağlanmış kart onunla, əks halda aktiv qayda ilə. */
    Optional<ActiveRule> findRuleFor(UUID customerId);

    void insert(UUID id, UUID customerId, UUID ruleId, int stampsRequired, LocalizedText name, Instant earnedAt,
                Instant expiresAt, int codeNonce, byte[] codeLookup);

    boolean codeTaken(byte[] codeLookup);

    /** Əvvəlcə istifadə oluna bilənlər, sonra tarixçə (ən yenisi birinci), ən çox 100. */
    List<GiftCardRow> listForCustomer(UUID customerId);

    /** Kodsuz köhnə kartlar (məs. dev seed). */
    List<UUID> findWithoutCode(UUID customerId);

    void assignCode(UUID id, int codeNonce, byte[] codeLookup);

    /** Kodun HMAC-ı ilə axtarış; {@code forUpdate} — kart sətri kilidlənir (təhvil). */
    Optional<RedemptionRow> findByLookup(byte[] codeLookup, boolean forUpdate);

    /** Filialda istifadə olunmuş son {@code limit} kart (ən yenisi birinci). */
    List<RedemptionRow> recentlyRedeemed(UUID branchId, int limit);

    void markRedeemed(UUID id, UUID staffId, UUID branchId, Instant now);

    void incrementRedeemedCount(UUID customerId, Instant now);

    /** Fon işi: müddəti bitmiş kartlar EXPIRED olur. */
    int expireOverdue(Instant now);
}
