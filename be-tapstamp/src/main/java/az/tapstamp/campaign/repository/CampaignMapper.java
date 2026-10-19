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

    record Campaign(UUID id, String text, String audience, @Nullable Integer inactiveDays, @Nullable UUID branchId,
                    int recipients, int segmentsPerMessage, int totalSms, String status, @Nullable String rejectReason,
                    UUID createdBy, Instant createdAt, @Nullable Instant decidedAt, int sentCount, int failedCount,
                    @Nullable Instant finishedAt) {
    }

    /** Auditoriya filtri: {@code inactiveSince} — INACTIVE üçün, {@code branchId} — istəyə bağlı. */
    record Audience(String audience, @Nullable Instant inactiveSince, @Nullable UUID branchId) {
    }

    /** Göndərişə götürülmüş alıcı. */
    record Recipient(UUID customerId, byte[] phoneEnc) {
    }

    long countAudience(@Param("a") Audience audience);

    boolean branchExists(UUID branchId);

    void insert(Campaign campaign);

    /** Auditoriyanı kampaniyanın alıcıları kimi yazır; yazılan sayı qaytarır. */
    int insertRecipients(@Param("campaignId") UUID campaignId, @Param("a") Audience audience);

    void updateCounts(UUID id, int recipients, int totalSms);

    List<Campaign> list(int limit);

    List<Campaign> listByStatus(List<String> statuses, int limit);

    Optional<Campaign> find(UUID id);

    /** Status yalnız {@code from} vəziyyətindədirsə dəyişir; dəyişən sətir sayı (0 — artıq dəyişib). */
    int changeStatus(UUID id, String from, String to, @Nullable Instant decidedAt, @Nullable UUID decidedBy,
                     @Nullable String rejectReason);

    /** Göndərilməli kampaniyalar (APPROVED və ya SENDING). */
    List<UUID> sendable();

    /** Artıq SMS ala bilməyən alıcılar (bloklanıb, silinib, imtina edib) SKIPPED olur. */
    int skipIneligible(UUID campaignId);

    /** Uzun müddət SENDING-də qalanlar (proses kəsilib) FAILED olur — iki dəfə göndərmək riskinə girmirik. */
    int failStale(UUID campaignId, Instant claimedBefore, Instant now);

    /** PENDING alıcılardan {@code limit} qədərini SENDING kimi götürür (başqa backend eyni sətirləri götürmür). */
    List<Recipient> claim(UUID campaignId, int limit, Instant now);

    void markRecipient(UUID campaignId, UUID customerId, String status, Instant now, @Nullable String error);

    /** PENDING və SENDING qalmayıbsa kampaniyanı SENT edir, sayları yazır; 1 — bitdi. */
    int finishIfDone(UUID campaignId, Instant now);
}
