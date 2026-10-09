package az.tapstamp.customer.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.customer.account.CustomerAccountService.AvailableReward;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.TenantMapper;

/** Müştərinin öz hesabı: möhür kartı, hədiyyələr, hesabın silinməsi. SQL: {@code mapper/tenant/customer/CustomerAccountMapper.xml}. */
@TenantMapper
public interface CustomerAccountMapper {

    /**
     * Kart: kartın öz qaydası ilə (hədd dəyişəndə yarımçıq kart köhnə qaydada qalır). {@code nextStampAt} — şirkətin
     * cooldown-una görə növbəti möhürün mümkün olduğu an (keçmişdə ola bilər).
     */
    record CardRow(int currentStamps, int totalStamps, int stampsRequired, LocalizedText rewardName,
                   LocalizedText rewardDescription, int pendingStamps, Instant nextStampAt) {
    }

    /** Qayda yoxdursa boş. */
    Optional<CardRow> findCard(UUID customerId);

    /** İstifadə oluna bilən (müddəti keçməmiş) hədiyyə kartları, köhnədən yeniyə. */
    List<AvailableReward> availableRewards(UUID customerId, Instant now);

    void cancelPendingRedemptions(UUID customerId);

    void revokeAvailableRewards(UUID customerId);

    /** Toxunuş jurnalında müştərinin izləri anonimləşir (bazadakı funksiya, jurnal yalnız əlavə olunandır). */
    int anonymizeTapEvents(UUID customerId);
}
