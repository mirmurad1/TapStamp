package az.tapstamp.plan;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Şirkətin paketi. {@code TRIAL} — sınaq müddəti ({@code trialEndsAt}-ə qədər), {@code ACTIVE} — ödənişli.
 * Sınaq bitəndə (ödəniş qeyd olunmayıbsa) şirkətin xidməti dayandırılır.
 */
public record Subscription(Plan plan, Status status, @Nullable Instant trialEndsAt, int extraBranches) {

    public enum Status { TRIAL, ACTIVE }

    /** Paketlərdən əvvəl yaradılmış şirkətlər: Premium, ödənişli. */
    public static final Subscription LEGACY = new Subscription(Plan.PREMIUM, Status.ACTIVE, null, 0);

    public boolean expired(Instant now) {
        return status == Status.TRIAL && trialEndsAt != null && !now.isBefore(trialEndsAt);
    }

    public int branchLimit() {
        return plan.includedBranches() + extraBranches;
    }
}
