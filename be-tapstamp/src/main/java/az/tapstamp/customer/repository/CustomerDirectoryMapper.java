package az.tapstamp.customer.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.customer.admin.CustomerDirectory.TimelineItem;
import az.tapstamp.shared.persistence.TenantMapper;
import org.apache.ibatis.session.ResultHandler;

/**
 * Admin panelinin müştəri sorğuları: siyahı (axtarış, filtrlər, sıralama, səhifələmə), detallar, zaman xətti, CSV.
 * SQL: {@code mapper/tenant/customer/CustomerDirectoryMapper.xml}.
 */
@TenantMapper
public interface CustomerDirectoryMapper {

    /**
     * Siyahının filtrləri. {@code all} — bütün filiallar, əks halda yalnız {@code branchIds}-də möhürü olanlar.
     * {@code orderBy} yalnız {@code CustomerDirectory.Sort}-un ağ siyahısındakı ifadədir (istifadəçi mətni deyil).
     */
    record Query(boolean all, List<UUID> branchIds, byte[] phoneHash, String namePattern, String language,
                 String status, Instant inactiveBefore, LocalDate registeredFrom, LocalDate registeredTo,
                 String timezone, String orderBy, boolean descending, int limit, long offset) {
    }

    /** Bazadakı sətir: nömrə şifrəlidir, servis onu açıb {@code Row}-a çevirir. */
    record Entry(UUID id, String name, byte[] phoneEnc, String language, String status, Instant registeredAt,
                 Instant lastVisitAt, int currentStamps, int totalStamps, int rewardsEarned, int rewardsRedeemed,
                 int openFraudFlags) {
    }

    List<Entry> list(Query query);

    long count(Query query);

    /** CSV üçün: bütün uyğun sətirlər axınla (yaddaşa yığılmadan), ən yenisi birinci. */
    void stream(Query query, ResultHandler<Entry> handler);

    Optional<Entry> find(UUID id, boolean all, List<UUID> branchIds);

    /** Möhürlər və hədiyyə kartları, yenidən köhnəyə; {@code before} — keyset səhifələmə. */
    List<TimelineItem> timeline(UUID customerId, int limit, long offset);

    long countTimeline(UUID customerId);
}
