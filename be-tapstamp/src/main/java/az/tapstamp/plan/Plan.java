package az.tapstamp.plan;

import java.util.EnumSet;
import java.util.Set;

/**
 * Paketlər, onların imkanları və limitləri. Hər üst paket alt paketin bütün imkanlarını əhatə edir. SMS kampaniyaları
 * bütün paketlərdədir (hər SMS ayrıca ödənilir), ona görə burada yoxdur.
 */
public enum Plan {
    BASIC(1, 3, EnumSet.noneOf(Feature.class)),
    STANDARD(3, 10, EnumSet.of(Feature.PUSH, Feature.REFERRALS, Feature.CSV_EXPORT, Feature.FULL_ANALYTICS,
            Feature.BONUS_RULES, Feature.TIERS)),
    PREMIUM(5, null, EnumSet.allOf(Feature.class));

    /** Sınaq müddəti (gün). */
    public static final int TRIAL_DAYS = 14;

    private final int includedBranches;
    private final Integer staffLimit;
    private final EnumSet<Feature> features;

    Plan(int includedBranches, Integer staffLimit, EnumSet<Feature> features) {
        this.includedBranches = includedBranches;
        this.staffLimit = staffLimit;
        this.features = features;
    }

    /** Paketə daxil olan filial sayı (Premium-da əlavə filiallar ayrıca). */
    public int includedBranches() {
        return includedBranches;
    }

    /** Sahiblərdən başqa işçilərin ən çox sayı; {@code null} — limitsiz. */
    public Integer staffLimit() {
        return staffLimit;
    }

    public boolean has(Feature feature) {
        return features.contains(feature);
    }

    public Set<Feature> features() {
        return features.clone();
    }
}
