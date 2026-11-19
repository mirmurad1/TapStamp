package az.tapstamp.feedback.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.TenantMapper;
import org.jspecify.annotations.Nullable;

/** Ziyarətdən sonra rəy: parametrlər, sorğular və cavablar. SQL: {@code mapper/tenant/feedback/FeedbackMapper.xml}. */
@TenantMapper
public interface FeedbackMapper {

    record FeedbackSettings(boolean enabled, int frequencyDays) {
    }

    /** Müştəriyə göstəriləcək sorğu: filial, ziyarət vaxtı və filialın Google rəy linki (varsa). */
    record OpenRequest(UUID id, UUID branchId, LocalizedText branchName, Instant visitAt, @Nullable String reviewUrl) {
    }

    /** Admin üçün rəy: müştərinin adı və şifrəli telefonu (servis açır). */
    record Row(UUID id, UUID customerId, @Nullable String customerName, byte[] phoneEnc, UUID branchId,
               LocalizedText branchName, String rating, @Nullable String reasons, @Nullable String comment,
               Instant createdAt, @Nullable Instant resolvedAt, @Nullable String resolutionNote) {
    }

    record SummaryRow(long good, long ok, long bad, long sent, long unresolvedBad, long reviewClicks) {
    }

    /** QR kartın hədəfi: filialın Google rəy linki, yoxdursa Google Maps axtarışı üçün şirkət və filial adı. */
    record ReviewTarget(@Nullable String googleReviewUrl, LocalizedText companyName, LocalizedText branchName,
                        @Nullable LocalizedText address) {
    }

    /** Platforma üçün çap kartı: aktiv filial, Google linki və QR skanları. */
    record ReviewCard(UUID branchId, LocalizedText name, @Nullable LocalizedText address,
                      @Nullable String googleReviewUrl, long recentScans, long totalScans) {
    }

    FeedbackSettings settings();

    /** Aktiv filial üçün QR hədəfi; filial yoxdursa və ya bağlıdırsa boş. */
    Optional<ReviewTarget> reviewTarget(UUID branchId);

    /** QR skanını günün sayğacına əlavə edir. */
    void recordQrScan(UUID branchId, LocalDate day);

    /** Aktiv filiallar: {@code recentScans} — {@code since} günündən bəri, {@code totalScans} — hamısı. */
    List<ReviewCard> reviewCards(LocalDate since);

    void updateSettings(boolean enabled, int frequencyDays, UUID staffId, Instant now);

    /** Son {@code since}-dən bəri bu müştəriyə sorğu yaradılıbmı (tezlik). */
    boolean requestedSince(UUID customerId, Instant since);

    void insertRequest(UUID id, UUID customerId, UUID branchId, Instant visitAt, Instant dueAt, Instant expiresAt,
                       Instant now);

    /** Vaxtı çatmış sorğuları göndərilmiş kimi qeyd edir; qeyd olunan müştərilər qaytarılır. */
    List<UUID> markDueSent(Instant now, int limit);

    /** Müştərinin cavab gözləyən ən son sorğusu (göndərilib, vaxtı keçməyib, cavab yoxdur). */
    Optional<OpenRequest> openRequest(UUID customerId, Instant now);

    /** Cavabı yazır; sorğu bu müştərinin deyilsə, vaxtı keçibsə və ya cavab varsa 0. */
    int answer(UUID id, UUID requestId, UUID customerId, String rating, @Nullable String reasons,
               @Nullable String comment, Instant now);

    /** Müştəri rəydən sonra Google rəy səhifəsini açdı (ilk dəfə); 0 — rəy tapılmadı və ya artıq qeyd olunub. */
    int markReviewClicked(UUID requestId, UUID customerId, Instant now);

    List<Row> list(@Nullable String rating, boolean unresolvedOnly, @Nullable UUID branchId, int limit, long offset);

    long count(@Nullable String rating, boolean unresolvedOnly, @Nullable UUID branchId);

    SummaryRow summary(Instant since, Instant until, @Nullable UUID branchId);

    int resolve(UUID id, @Nullable String note, UUID staffId, Instant now);
}
