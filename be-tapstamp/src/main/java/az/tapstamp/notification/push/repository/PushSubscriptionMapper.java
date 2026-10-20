package az.tapstamp.notification.push.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.persistence.TenantMapper;
import org.jspecify.annotations.Nullable;

/** Müştərilərin web push abunələri (cari tenant-ın bazası). SQL: {@code mapper/tenant/push/PushSubscriptionMapper.xml}. */
@TenantMapper
public interface PushSubscriptionMapper {

    record Subscription(UUID id, String endpoint, String p256dh, String auth) {
    }

    /**
     * Ünvan üzrə əlavə və ya yeniləmə: eyni brauzer başqa nömrə ilə daxil olubsa, abunə yeni müştəriyə keçir.
     */
    void upsert(UUID id, UUID customerId, String endpoint, String p256dh, String auth, @Nullable String deviceLabel,
                Instant now);

    /** Müştərinin ən köhnə abunələri silinir — ən çox {@code keep} cihaz qalır. */
    void trim(UUID customerId, int keep);

    int delete(UUID customerId, String endpoint);

    void deleteById(UUID id);

    void deleteAll(UUID customerId);

    List<Subscription> forCustomer(UUID customerId);

    void markSent(UUID id, Instant now);

    /** Müştərinin dili — bildiriş mətni üçün (yalnız aktiv müştəri). */
    Optional<String> activeCustomerLanguage(UUID customerId);

    /** Push-u açıq olan (ən azı bir abunəsi olan) müştərilərin sayı. */
    long countCustomers();
}
