package az.tapstamp.audit.repository;

import java.time.Instant;
import java.util.UUID;

/** Audit jurnalına yazılan bir sətir (şirkət və platforma jurnalı üçün eyni forma; platformada {@code actorType} yoxdur). */
public record AuditRecord(UUID id, String actorType, UUID actorId, String action, String targetType, String targetId,
                          String ip, String userAgent, String details, Instant createdAt) {
}
