package az.tapstamp.audit.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.tapstamp.audit.AuditLogQuery.Entry;
import az.tapstamp.shared.persistence.TenantMapper;

/** Şirkətin audit jurnalı ({@code audit_logs}). SQL: {@code mapper/tenant/audit/AuditLogMapper.xml}. */
@TenantMapper
public interface AuditLogMapper {

    void insert(AuditRecord record);

    /** Yenidən köhnəyə; bütün filtrlər istəyə bağlıdır, {@code before} — keyset səhifələmə. */
    List<Entry> list(String action, UUID actorId, Instant from, Instant to, Instant before, int limit);

    List<String> actions();
}
