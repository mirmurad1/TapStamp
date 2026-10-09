package az.tapstamp.audit;

import java.time.Instant;
import java.util.UUID;

/** Platforma audit jurnalının sətri (icraçının adı ilə). */
public record PlatformAuditEntry(UUID id, UUID actorId, String actorName, String action, String targetType,
                                 String targetId, String details, String ip, Instant createdAt) {
}
