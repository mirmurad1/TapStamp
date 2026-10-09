package az.tapstamp.nfc.repository;

import java.time.Instant;
import java.util.UUID;

import az.tapstamp.shared.persistence.TenantMapper;

/** Fırıldaq işarələri ({@code fraud_flags}). SQL: {@code mapper/tenant/nfc/FraudFlagMapper.xml}. */
@TenantMapper
public interface FraudFlagMapper {

    /** Həll olunmamış eyni işarə {@code since}-dən sonra eyni çip və müştəri üçün varmı. */
    boolean existsOpen(String type, UUID tagId, UUID customerId, Instant since);

    void insert(UUID id, UUID customerId, UUID tagId, String type, String severity, String details, Instant now);
}
