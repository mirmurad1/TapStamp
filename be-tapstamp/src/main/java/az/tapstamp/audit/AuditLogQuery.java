package az.tapstamp.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import az.tapstamp.audit.repository.AuditLogMapper;
import az.tapstamp.audit.repository.PlatformAuditLogMapper;
import az.tapstamp.company.service.CompanySettings;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import org.springframework.stereotype.Service;

/**
 * Audit jurnallarına baxış (yalnız oxuma; jurnal yalnız əlavə olunur). İcraçının adı işçilər/adminlər cədvəlindən
 * götürülür; şəxsi məlumat jurnalda saxlanılmır.
 */
@Service
public class AuditLogQuery {

    public record Entry(UUID id, String actorType, UUID actorId, String actorName, String action, String targetType,
                        String targetId, String details, String ip, Instant createdAt) {
    }

    private final AuditLogMapper tenantLog;
    private final PlatformAuditLogMapper platformLog;
    private final CompanySettings company;

    public AuditLogQuery(AuditLogMapper tenantLog, PlatformAuditLogMapper platformLog, CompanySettings company) {
        this.tenantLog = tenantLog;
        this.platformLog = platformLog;
        this.company = company;
    }

    /**
     * Şirkətin jurnalı, yenidən köhnəyə, səhifə ilə. Tarixlər şirkətin saat qurşağı ilə günlərdir.
     */
    public PageResult<Entry> list(String action, UUID actorId, LocalDate from, LocalDate to, PageQuery page) {
        ZoneId zone = company.current().timezone();
        Instant start = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        return page.result(tenantLog.list(action, actorId, start, end, page.limit(), page.offset()),
                tenantLog.count(action, actorId, start, end));
    }

    /** Filtr üçün jurnalda rast gəlinən əməliyyat adları. */
    public List<String> actions() {
        return tenantLog.actions();
    }

    /** Platformanın jurnalı, yenidən köhnəyə, səhifə ilə. */
    public PageResult<PlatformAuditEntry> platform(String action, PageQuery page) {
        return page.result(platformLog.list(action, page.limit(), page.offset()), platformLog.count(action));
    }
}
