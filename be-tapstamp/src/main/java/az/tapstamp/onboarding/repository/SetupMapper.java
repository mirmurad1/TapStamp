package az.tapstamp.onboarding.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import az.tapstamp.shared.persistence.TenantMapper;
import org.jspecify.annotations.Nullable;

/** Başlanğıc siyahısının siqnalları (mövcud cədvəllərdən). SQL: {@code mapper/tenant/onboarding/SetupMapper.xml}. */
@TenantMapper
public interface SetupMapper {

    /**
     * Məcburi: loqo, aktiv hədiyyə qaydası, iş saatları yazılmış aktiv filial, sahibdən başqa işçi (və ya gözləyən
     * dəvət), aktiv filialda aktiv NFC çipi (platforma qeydiyyata alır). Tövsiyələr: ziyarətdən sonra rəy, Google rəy linki, dost dəvəti, səviyyələr, xatırlatmalar, bonus
     * kampaniyası, menyu.
     */
    record Signals(boolean branding, boolean rewardRule, boolean branch, boolean branchHours, boolean team,
                   boolean nfcTag, boolean feedback, boolean googleReview, boolean referrals, boolean tiers, boolean reminders,
                   boolean bonusRules, boolean catalog) {
    }

    /** {@code today} — şirkətin günü (bonus kampaniyasının bitmə tarixi üçün). */
    Signals signals(Instant now, LocalDate today);

    /** {@code brandingDoneAt} — loqo ilk dəfə yüklənəndə qeyd olunur (sonra silinsə də addım tamamlanmış qalır). */
    record State(@Nullable Instant recommendationsHiddenAt, @Nullable Instant brandingDoneAt) {
    }

    /** Sətirdə bütün sütunlar NULL-dursa MyBatis {@code null} qaytarır — çağıran bunu nəzərə alır. */
    @Nullable State state();

    /** Loqo addımı ilk dəfə tamamlandı (artıq qeyd olunubsa dəyişmir). */
    void markBrandingDone(Instant now);

    void setRecommendationsHidden(@Nullable Instant hiddenAt, UUID staffId);
}
