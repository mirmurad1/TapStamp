package az.tapstamp.loyalty.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.loyalty.stamp.StampLedger.Stamp;
import az.tapstamp.loyalty.stamp.StampLedger.StampStatus;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.TenantMapper;

/**
 * Möhürlər ({@code stamps}), loyallıq hesabı ({@code loyalty_accounts}) və sui-istifadə qaydası
 * ({@code stamp_policies}). SQL: {@code mapper/tenant/loyalty/StampMapper.xml}.
 */
@TenantMapper
public interface StampMapper {

    /** Filialın qaydası (yoxdursa şirkətin standartı) — dəqiqələrlə. */
    record PolicyRow(int minIntervalMinutes, int maxStampsPerDay, boolean requireStaffApproval,
                     int pendingTimeoutMinutes, boolean enforceBusinessHours) {
    }

    Optional<PolicyRow> findPolicy(UUID branchId);

    /** Filialın iş saatları (JSON mətni) — filial yoxdursa boş. */
    Optional<String> findBusinessHours(UUID branchId);

    /** Hesab yoxdursa yaradılır (köhnə məlumat üçün). */
    void ensureAccount(UUID id, UUID customerId, Instant now);

    /** Hesab sətrinin kilidi: eyni müştərinin paralel sorğuları ardıcıl icra olunur. */
    UUID lockAccount(UUID customerId);

    /** NFC ilə alınmış son aktiv (PENDING və ya CONFIRMED) möhürün vaxtı. */
    Optional<Instant> lastTapStampAt(UUID customerId);

    int countTapStampsOn(UUID customerId, LocalDate day);

    void insertTapStamp(UUID id, UUID branchId, UUID customerId, UUID tagId, UUID tapTokenId, StampStatus status,
                        LocalDate day, Instant confirmedAt, Instant at);

    void insertManualStamp(UUID id, UUID branchId, UUID customerId, UUID staffId, String reason, LocalDate day,
                           Instant at);

    /** Kampaniya möhürü (mənbə CAMPAIGN, təsdiqlənmiş). */
    void insertCampaignStamp(UUID id, UUID branchId, UUID customerId, UUID campaignId, LocalDate day, Instant at);

    Optional<Stamp> findByTapToken(UUID tapTokenId);

    Optional<Stamp> lockStamp(UUID stampId);

    /** Kartın sayğacları bir möhür artır; yeni cari möhür sayı qaytarılır. */
    int addStampToCard(UUID customerId, Instant at);

    /** Hədiyyə verildi: hədd qədər möhür kartdan çıxılır, kart aktiv qaydaya keçir. */
    void redeemCardForReward(UUID customerId, int required);

    void linkStampToReward(UUID stampId, UUID rewardId);

    Optional<Integer> findCurrentStamps(UUID customerId);

    void touchCustomerVisit(UUID customerId, UUID branchId, Instant at);

    LocalizedText findBranchName(UUID branchId);

    // --- İşçi təsdiqi (PENDING möhürlər) ---

    /** Gözləyən möhür (müştərinin adı və maskalı telefonu ilə). */
    record PendingRow(UUID id, UUID branchId, UUID customerId, String customerName, String phoneMasked,
                      Instant createdAt) {
    }

    /** Filialın gözləyən möhürləri, ən köhnəsi birinci (ən çox 100). */
    List<PendingRow> findPending(UUID branchId, int limit, long offset);

    long countPending(UUID branchId);

    void confirmStamp(UUID stampId, UUID staffId, Instant now);

    void rejectStamp(UUID stampId, UUID staffId, String reason, Instant now);

    /** Vaxtı keçmiş PENDING möhürlər (hər filialın öz müddəti ilə) {@code PENDING_TIMEOUT} ilə rədd edilir. */
    int rejectTimedOut(Instant now);
}
