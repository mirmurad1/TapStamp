package az.tapstamp.loyalty.stamp;

import java.time.Duration;

/**
 * Sui-istifadə qaydaları (filialın override-ı, yoxdursa şirkət standartı).
 *
 * @param minInterval          iki möhür arasında ən az vaxt (cooldown)
 * @param maxStampsPerDay      şirkətin saat qurşağında bir gündə ən çox möhür
 * @param requireStaffApproval möhür işçi təsdiqlənənə qədər PENDING qalır
 * @param pendingTimeout       təsdiqlənməyən möhür bu müddətdən sonra avtomatik rədd edilir
 * @param enforceBusinessHours filial bağlı olanda möhür verilmir
 */
public record StampPolicy(Duration minInterval, int maxStampsPerDay, boolean requireStaffApproval,
                          Duration pendingTimeout, boolean enforceBusinessHours) {

    public static final StampPolicy DEFAULT = new StampPolicy(Duration.ofMinutes(20), 3, false, Duration.ofMinutes(30),
            false);
}
