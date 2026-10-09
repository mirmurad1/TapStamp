package az.tapstamp.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import az.tapstamp.audit.repository.AuditLogMapper;
import az.tapstamp.audit.repository.PlatformAuditLogMapper;
import az.tapstamp.company.service.CompanySettings;
import org.springframework.stereotype.Service;

/**
 * Audit jurnallarına baxış (yalnız oxuma; jurnal yalnız əlavə olunur). İcraçının adı işçilər/adminlər cədvəlindən
 * götürülür; şəxsi məlumat jurnalda saxlanılmır.
 */
@Service
public class AuditLogQuery {

    public static final int PAGE = 50;

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
     * Şirkətin jurnalı, yenidən köhnəyə. Tarixlər şirkətin saat qurşağı ilə günlərdir; {@code before} — əvvəlki
     * səhifənin son elementinin vaxtı (keyset səhifələmə).
     */
    public List<Entry> list(String action, UUID actorId, LocalDate from, LocalDate to, Instant before) {
        ZoneId zone = company.current().timezone();
        return tenantLog.list(action, actorId,
                from == null ? null : from.atStartOfDay(zone).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant(),
                before, PAGE);
    }

    /** Filtr üçün jurnalda rast gəlinən əməliyyat adları. */
    public List<String> actions() {
        return tenantLog.actions();
    }

    /** Platformanın jurnalı, yenidən köhnəyə (50-lik səhifələr). */
    public List<PlatformAuditEntry> platform(String action, Instant before) {
        return platformLog.list(action, before, PAGE);
    }
}
