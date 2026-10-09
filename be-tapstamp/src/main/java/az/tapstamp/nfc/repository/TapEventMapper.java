package az.tapstamp.nfc.repository;

import java.time.Instant;
import java.util.UUID;

import az.tapstamp.shared.persistence.TenantMapper;

/** Toxunuş jurnalı ({@code tap_events}, yalnız əlavə olunur). SQL: {@code mapper/tenant/nfc/TapEventMapper.xml}. */
@TenantMapper
public interface TapEventMapper {

    record TapEventRow(UUID id, UUID branchId, UUID tagId, String stage, String outcome, String reason,
                       Integer counter, Integer counterGap, UUID customerId, UUID tapTokenId, UUID stampId, String ip,
                       byte[] browserIdHash, byte[] userAgentHash, Instant createdAt) {
    }

    void insert(TapEventRow row);

    /** Tokenin bu müştəri üçün son rədd səbəbi (yoxdursa null). */
    String lastClaimRejection(UUID tapTokenId, UUID customerId);
}
