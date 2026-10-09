package az.tapstamp.audit;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.audit.repository.AuditLogMapper;
import az.tapstamp.audit.repository.AuditRecord;
import az.tapstamp.audit.repository.PlatformAuditLogMapper;
import az.tapstamp.shared.id.Ids;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Audit jurnalı: kim, nə etdi, nə vaxt, hansı IP-dən. Cədvəllər yalnız əlavə olunandır (tətbiq rolunun UPDATE/DELETE
 * hüququ yoxdur). {@code details}-ə şəxsi məlumat yazılmır — email və telefon yalnız maskalı şəkildə.
 */
@Component
public class AuditLogger {

    public enum ActorType {
        STAFF,
        CUSTOMER,
        PLATFORM,
        SYSTEM
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AuditLogMapper tenantLog;
    private final PlatformAuditLogMapper platformLog;
    private final Clock clock;

    public AuditLogger(AuditLogMapper tenantLog, PlatformAuditLogMapper platformLog, Clock clock) {
        this.tenantLog = tenantLog;
        this.platformLog = platformLog;
        this.clock = clock;
    }

    /** Cari tenant-ın bazasına yazır (tenant konteksti tələb olunur). */
    public void tenant(String action, ActorType actorType, UUID actorId, String targetType, Object targetId,
                       Map<String, ?> details) {
        tenantLog.insert(record(action, actorType.name(), actorId, targetType, targetId, details));
    }

    public void platform(String action, UUID actorId, String targetType, Object targetId, Map<String, ?> details) {
        platformLog.insert(record(action, null, actorId, targetType, targetId, details));
    }

    private AuditRecord record(String action, String actorType, UUID actorId, String targetType, Object targetId,
                               Map<String, ?> details) {
        HttpServletRequest request = currentRequest();
        return new AuditRecord(Ids.newId(), actorType, actorId, action, targetType,
                targetId == null ? null : targetId.toString(),
                request == null ? null : request.getRemoteAddr(),
                request == null ? null : truncate(request.getHeader("User-Agent")),
                details == null || details.isEmpty() ? null : JSON.writeValueAsString(details),
                clock.instant());
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 512 ? value : value.substring(0, 512);
    }
}
