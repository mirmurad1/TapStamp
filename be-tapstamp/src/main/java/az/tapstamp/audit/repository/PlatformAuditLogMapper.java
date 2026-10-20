package az.tapstamp.audit.repository;

import java.time.Instant;
import java.util.List;

import az.tapstamp.audit.PlatformAuditEntry;
import az.tapstamp.shared.persistence.PlatformMapper;

/** Platformanın audit jurnalı ({@code platform_audit_logs}). SQL: {@code mapper/platform/audit/PlatformAuditLogMapper.xml}. */
@PlatformMapper
public interface PlatformAuditLogMapper {

    void insert(AuditRecord record);

    List<PlatformAuditEntry> list(String action, int limit, long offset);

    long count(String action);
}
