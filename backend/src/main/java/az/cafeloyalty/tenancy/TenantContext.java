package az.cafeloyalty.tenancy;

import java.util.Optional;
import java.util.function.Supplier;

import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cari sorğunun (və ya fon işinin) tenant-ı. Tenant yalnız domendən ({@code TenantResolutionFilter})
 * və ya fon işlərində açıq şəkildə ({@link #callAs}) təyin olunur — heç vaxt sorğunun body-sindən.
 *
 * <p>Tenant bazasına bağlantı bu kontekstə görə seçilir; kontekst yoxdursa əməliyyat rədd edilir (fail closed).
 */
public final class TenantContext {

    private static final ThreadLocal<TenantInfo> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<TenantInfo> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Tenant olmayan kontekstdə (məs. platforma domenində) tenant resursuna müraciət 404 ilə nəticələnir. */
    public static TenantInfo require() {
        TenantInfo tenant = CURRENT.get();
        if (tenant == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return tenant;
    }

    /**
     * Əməliyyatı göstərilən tenant adından icra edir və əvvəlki konteksti bərpa edir.
     * Aktiv tranzaksiya daxilində tenant-ı dəyişmək qadağandır: bağlantı artıq köhnə tenant-ın bazasındandır.
     */
    public static <T> T callAs(TenantInfo tenant, Supplier<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Tenant context cannot change inside an active transaction");
        }
        TenantInfo previous = CURRENT.get();
        CURRENT.set(tenant);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(TenantInfo tenant, Runnable action) {
        callAs(tenant, () -> {
            action.run();
            return null;
        });
    }

    /** Yalnız sorğu filtri üçün: sorğunun əvvəlində tenant-ı bağlayır. */
    public static void bind(TenantInfo tenant) {
        CURRENT.set(tenant);
    }

    /** Yalnız sorğu filtri üçün: sorğu bitəndə mütləq çağırılır (thread-lər arasında sızmanın qarşısı). */
    public static void clear() {
        CURRENT.remove();
    }
}
