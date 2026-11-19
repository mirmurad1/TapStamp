package az.tapstamp.customer.account;

import java.util.List;
import java.util.UUID;

import az.tapstamp.customer.repository.CustomerMapper;
import az.tapstamp.customer.repository.CustomerMapper.LegacyMask;
import az.tapstamp.security.crypto.Aad;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.shared.pii.PhoneMasker;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.registry.TenantDirectory;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Bir dəfəlik yeniləmə: köhnə formatda saxlanmış maskalar ({@code +994 50 ***-**-67}) yeni formata
 * ({@code +994 50 *** 45 67}) keçirilir. Hər işə salınmada yoxlanır; yenilənəcək sətir qalmayıbsa heç nə etmir.
 * Testlərdə söndürülür ({@code app.jobs.enabled=false}) — test metodu birbaşa çağırır.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class PhoneMaskBackfill {

    private static final Logger log = LoggerFactory.getLogger(PhoneMaskBackfill.class);
    private static final int BATCH = 500;

    private final TenantDirectory directory;
    private final CustomerMapper customers;
    private final EnvelopeCipher cipher;

    public PhoneMaskBackfill(TenantDirectory directory, CustomerMapper customers,
                             @Qualifier("tenantCipher") EnvelopeCipher cipher) {
        this.directory = directory;
        this.customers = customers;
        this.cipher = cipher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        for (TenantRecord tenant : directory.findAll()) {
            if (tenant.status().outOfService()) {
                continue;
            }
            try {
                TenantContext.runAs(tenant.toInfo(), this::runForCurrentTenant);
            } catch (RuntimeException e) {
                log.warn("Phone mask backfill failed for tenant {}: {}", tenant.slug(), e.getClass().getSimpleName());
            }
        }
    }

    /** Cari şirkətin bütün köhnə maskalarını yeniləyir; yenilənən sətirlərin sayını qaytarır. */
    public int runForCurrentTenant() {
        UUID tenantId = TenantContext.require().id();
        int updated = 0;
        UUID after = null;
        List<LegacyMask> batch;
        do {
            batch = customers.findLegacyMasks(PhoneMasker.LEGACY_MARKER, after, BATCH);
            for (LegacyMask row : batch) {
                after = row.id();
                try {
                    String phone = cipher.decryptString(row.phoneEnc(),
                            Aad.tenantField(tenantId, "customers", "phone_enc", row.id()));
                    customers.updatePhoneMasked(row.id(), PhoneMasker.mask(phone));
                    updated++;
                } catch (RuntimeException e) {
                    log.warn("Phone mask not updated for customer {}: {}", row.id(), e.getClass().getSimpleName());
                }
            }
        } while (batch.size() == BATCH);
        if (updated > 0) {
            log.info("Phone masks updated to the new format: {}", updated);
        }
        return updated;
    }
}
