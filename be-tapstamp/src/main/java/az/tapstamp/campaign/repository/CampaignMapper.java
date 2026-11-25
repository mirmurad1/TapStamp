package az.tapstamp.campaign.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.persistence.TenantMapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/** Kampaniyalar və alıcıları (cari tenant-ın bazası). SQL: {@code mapper/tenant/campaign/CampaignMapper.xml}. */
@TenantMapper
public interface CampaignMapper {

    /**
     * Kampaniya. Sayğaclar canlıdır (alıcıların vəziyyətindən): göndərilib, xəta, gözləyir (təkrar cəhd daxil),
     * ötürülüb; möhür kampaniyasında — möhür verilmiş alıcılar.
     */
    record Campaign(UUID id, String type, @Nullable Integer stampCount, String text, boolean personalized, boolean latin,
                    String audience, @Nullable Integer inactiveDays, @Nullable UUID branchId,
                    int recipients, int segmentsPerMessage, int totalSms, String status, @Nullable String rejectReason,
                    @Nullable String pauseReason, UUID createdBy, Instant createdAt, @Nullable Instant updatedAt,
                    @Nullable Instant decidedAt, @Nullable Instant deadlineAt, @Nullable Instant finishedAt,
                    int sentCount, int failedCount, int pendingCount, int skippedCount, int stampsGranted) {
    }

    /** Auditoriya filtri: {@code inactiveSince} — INACTIVE üçün, {@code branchId} — istəyə bağlı. */
    record Audience(String audience, @Nullable Instant inactiveSince, @Nullable UUID branchId) {
    }

    /** Göndərişə götürülmüş alıcı. */
    record Recipient(UUID customerId, byte[] phoneEnc, @Nullable String name, @Nullable UUID lastBranchId,
                     int attempts) {
    }

    /** SMS-i göndərilməyən alıcı (sahibə səbəbi ilə göstərilir). */
    record FailedRecipient(UUID customerId, @Nullable String name, byte @Nullable [] phoneEnc, @Nullable String error,
                           @Nullable Instant at) {
    }

    long countAudience(@Param("a") Audience audience);

    boolean branchExists(UUID branchId);

    /** Aktiv hədiyyə qaydasının möhür həddi (qayda yoxdursa boş). */
    Optional<Integer> activeStampsRequired();

    /** Şirkətin ilk aktiv filialı — möhürün filialı müəyyən deyilsə. */
    Optional<UUID> defaultBranch();

    /** Auditoriyadakı müştərilərin adları (adı yoxdursa null) — fərdi müraciətdə SMS sayını hesablamaq üçün. */
    List<@Nullable String> audienceNames(@Param("a") Audience audience);

    /** Kampaniyanın alıcılarının adları (adı yoxdursa null). */
    List<@Nullable String> recipientNames(UUID campaignId);

    void insert(@Param("c") Campaign campaign);

    /** Auditoriyanı kampaniyanın alıcıları kimi yazır; yazılan sayı qaytarır. */
    int insertRecipients(@Param("campaignId") UUID campaignId, @Param("a") Audience audience);

    void updateCounts(UUID id, int recipients, int segmentsPerMessage, int totalSms);

    /** Təsdiq gözləyən kampaniyanın mətni və auditoriyası dəyişir; 0 — artıq təsdiq gözləmir. */
    int updateDraft(UUID id, @Nullable Integer stampCount, String text, boolean personalized, boolean latin,
                    String audience, @Nullable Integer inactiveDays, @Nullable UUID branchId, Instant now);

    /** Alıcılar yenidən müəyyənləşəndən əvvəl köhnələri silinir (yalnız təsdiq gözləyən kampaniya). */
    void deleteRecipients(UUID campaignId);

    List<Campaign> list(String type, int limit, long offset);

    long countByType(String type);

    List<Campaign> listByStatus(List<String> statuses, int limit);

    long countByStatus(List<String> statuses);

    Optional<Campaign> find(UUID id);

    List<FailedRecipient> failedRecipients(UUID campaignId, int limit, long offset);

    long countFailed(UUID campaignId);

    /** Status yalnız {@code from} vəziyyətindədirsə dəyişir; dəyişən sətir sayı (0 — artıq dəyişib). */
    int changeStatus(UUID id, String from, String to, @Nullable Instant decidedAt, @Nullable UUID decidedBy,
                     @Nullable String rejectReason);

    // ---------------------------------------------------------------- göndəriş

    /** Göndərilməli kampaniyalar (APPROVED və ya SENDING; PAUSED gözləyir). */
    List<UUID> sendable();

    /** APPROVED → SENDING; son müddət bu andan sayılır. */
    int startSending(UUID id, Instant deadlineAt);

    /** Hesab səviyyəli xəta: SENDING → PAUSED (səbəb ilə). */
    int pause(UUID id, String reason);

    /** Platforma admini: PAUSED → SENDING; son müddət davam etmə anından yenidən hesablanır. */
    int resume(UUID id, Instant deadlineAt);

    /** Ləğv olunan kampaniya: hələ göndərilməmiş alıcılar SKIPPED olur. */
    int skipUnsent(UUID campaignId);

    /** Artıq SMS ala bilməyən alıcılar (bloklanıb, silinib) SKIPPED olur. */
    int skipIneligible(UUID campaignId);

    /** Uzun müddət SENDING-də qalanlar (proses SMS-in ortasında kəsilib) — nəticə məlum deyil. */
    List<Recipient> staleSending(UUID campaignId, Instant claimedBefore);

    /** Son müddət bitib: gözləyən alıcılar son olaraq FAILED olur. */
    int expirePending(UUID campaignId, Instant now);

    /** Vaxtı çatmış PENDING alıcılardan {@code limit} qədərini SENDING kimi götürür (başqa backend götürmür). */
    List<Recipient> claim(UUID campaignId, int limit, Instant now);

    /** SMS qəbul olundu (və ya nəticə məlum deyil — göndərilmiş sayılır); {@code note} — izah (adətən null). */
    void markSent(UUID campaignId, UUID customerId, Instant now, @Nullable String note);

    void markFailed(UUID campaignId, UUID customerId, Instant now, String error);

    /** Müvəqqəti xəta: cəhd sayılır, alıcı {@code nextAttemptAt}-də yenidən növbəyə düşür. */
    void retryLater(UUID campaignId, UUID customerId, Instant nextAttemptAt, String error);

    /** Göndəriş cəhdi edilmədi (provayder əlçatmaz, kampaniya dayandı) — cəhd sayılmır. */
    void release(UUID campaignId, UUID customerId);

    /** Möhürlərin verildiyini qeyd edir (bir dəfə); 1 — indi qeyd olundu, 0 — artıq verilib. */
    int markStampsGranted(UUID campaignId, UUID customerId, Instant now);

    /** PENDING və SENDING qalmayıbsa kampaniyanı SENT edir; 1 — bitdi. */
    int finishIfDone(UUID campaignId, Instant now);
}
