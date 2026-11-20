package az.tapstamp.catalog.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.TenantMapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * Endirim bildirişləri ({@code deal_notification_settings}, {@code deal_announcements}, {@code deal_push_log}).
 * SQL: {@code mapper/tenant/catalog/DealNotificationMapper.xml}.
 */
@TenantMapper
public interface DealNotificationMapper {

    record Settings(boolean autoEnabled, LocalTime sendTime, boolean lastDayEnabled,
                    @Nullable LocalDate lastAutoRunOn) {
    }

    /** Elan oluna bilən endirimli məhsul (menyu açıq, kateqoriya görünür, məhsul mövcud, endirim bu gün qüvvədə). */
    record DealItem(UUID id, LocalizedText name, BigDecimal price, String discountKind, BigDecimal discountValue,
                    @Nullable UUID branchId) {
    }

    /** Bildiriş ala bilən müştəri: aktiv, push abunəsi var. */
    record Recipient(UUID customerId, @Nullable UUID lastBranchId) {
    }

    /** {@code recipients} — göndərilib; {@code waiting} — filialı açılanda gedəcək. */
    record DealAnnouncement(UUID id, String kind, String trigger, LocalDate announcedOn, int itemCount, int recipients,
                            int waiting, int delivered, int opened, Instant createdAt) {
    }

    /** Növbədəki bildiriş: müştərinin filialı açılanda göndərilir. */
    record WaitingPush(UUID announcementId, UUID customerId, String kind, @Nullable UUID branchId, int itemCount,
                       LocalizedText itemsText) {
    }

    /** Aktiv filialın iş saatları (JSON; null — qrafik yoxdur, həmişə açıq). */
    record BranchHours(UUID id, @Nullable String businessHours) {
    }

    Settings settings();

    void updateSettings(boolean autoEnabled, LocalTime sendTime, boolean lastDayEnabled, UUID staffId, Instant now);

    /** Bu gün üçün avtomatik yoxlamanı "tutur": 1 — bu çağırış ilk oldu (bir neçə server eyni anda işləməsin). */
    int claimAutoRun(LocalDate today);

    /** Hələ elan olunmamış, bu gün qüvvədə olan endirimlər (menyudakı sıra ilə). */
    List<DealItem> newDeals(LocalDate today);

    /** Bu gün son günü olan, əvvəl elan olunmuş və son gün xatırlatması getməmiş endirimlər. */
    List<DealItem> lastDayDeals(LocalDate today);

    /** Gündə bir elan: həmin gün artıq elan varsa 0. */
    int insertAnnouncement(UUID id, String kind, String trigger, LocalDate announcedOn, int itemCount,
                           @Nullable UUID staffId, Instant now);

    boolean announcedOn(LocalDate day);

    void markAnnounced(@Param("ids") List<UUID> ids, Instant now);

    void markLastDayAnnounced(@Param("ids") List<UUID> ids, Instant now);

    /**
     * Bu gün endirim bildirişi almamış (bugünkü elanda yoxdur, növbədə gözləyən bildirişi yoxdur, {@code dayStart}-dan
     * sonra push almayıb) müştərilər; {@code after} — səhifələmə üçün son müştəri (null — əvvəldən).
     */
    List<Recipient> recipients(LocalDate today, Instant dayStart, @Nullable UUID after, int limit);

    /** Müştəriyə bu gün endirim bildirişi yoxdursa növbəyə yazılır: 1 — yazıldı. */
    int queuePush(UUID announcementId, UUID customerId, LocalDate sentOn, @Nullable UUID branchId, int itemCount,
                  LocalizedText itemsText, Instant now);

    /** Göndərilməmiş bildirişlər; {@code after} — səhifələmə üçün sonuncu sətir (null — əvvəldən). */
    List<WaitingPush> waiting(@Nullable WaitingPush after, int limit);

    /** 1 — bu çağırış göndərməni "tutdu" (təkrar göndərilməsin). */
    int markSent(UUID announcementId, UUID customerId, Instant now);

    /** {@code before}-dan əvvəl yaranıb hələ getməyən bildirişlər ləğv olunur (filial açılmadı). */
    int dropStale(Instant before, Instant now);

    List<BranchHours> branchHours();

    void incrementDelivered(UUID announcementId);

    /** Bildirişə toxunub menyunu açdı (bir dəfə sayılır): 1 — ilk açılış. */
    int markOpened(UUID announcementId, UUID customerId, Instant now);

    /** Son elanlar (yenidən köhnəyə), göndərilən və açan müştərilərin sayı ilə. */
    List<DealAnnouncement> recent(int limit);

    /** Məhsulun endirimi yenidən qurulub — növbəti elana yenə düşsün. */
    void resetAnnouncement(UUID itemId);

    /** Endirimin son günü dəyişib — son gün xatırlatması yenidən göndərilə bilsin. */
    void resetLastDayAnnouncement(UUID itemId);
}
