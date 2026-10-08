package az.tapstamp.audit;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.shared.id.Ids;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
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

    private final JdbcClient tenantJdbc;
    private final JdbcClient platformJdbc;
    private final Clock clock;

    public AuditLogger(@Qualifier("tenantJdbc") JdbcClient tenantJdbc, @Qualifier("platformJdbc") JdbcClient platformJdbc,
                       Clock clock) {
        this.tenantJdbc = tenantJdbc;
        this.platformJdbc = platformJdbc;
        this.clock = clock;
    }

    /** Cari tenant-ın bazasına yazır (tenant konteksti tələb olunur). */
    public void tenant(String action, ActorType actorType, UUID actorId, String targetType, Object targetId,
                       Map<String, ?> details) {
        tenantJdbc.sql("""
                        INSERT INTO audit_logs (id, actor_type, actor_id, action, target_type, target_id, ip, user_agent,
                                                details, created_at)
                        VALUES (:id, :actorType, :actorId, :action, :targetType, :targetId, CAST(:ip AS inet), :ua,
                                CAST(:details AS jsonb), :createdAt)""")
                .params(params(action, actorType.name(), actorId, targetType, targetId, details))
                .update();
    }

    public void platform(String action, UUID actorId, String targetType, Object targetId, Map<String, ?> details) {
        platformJdbc.sql("""
                        INSERT INTO platform_audit_logs (id, actor_id, action, target_type, target_id, ip, user_agent,
                                                         details, created_at)
                        VALUES (:id, :actorId, :action, :targetType, :targetId, CAST(:ip AS inet), :ua,
                                CAST(:details AS jsonb), :createdAt)""")
                .params(params(action, null, actorId, targetType, targetId, details))
                .update();
    }

    private Map<String, Object> params(String action, String actorType, UUID actorId, String targetType, Object targetId,
                                       Map<String, ?> details) {
        HttpServletRequest request = currentRequest();
        Map<String, Object> values = new HashMap<>();
        values.put("id", Ids.newId());
        values.put("actorType", actorType);
        values.put("actorId", actorId);
        values.put("action", action);
        values.put("targetType", targetType);
        values.put("targetId", targetId == null ? null : targetId.toString());
        values.put("ip", request == null ? null : request.getRemoteAddr());
        values.put("ua", request == null ? null : truncate(request.getHeader("User-Agent")));
        values.put("details", details == null || details.isEmpty() ? null : JSON.writeValueAsString(details));
        values.put("createdAt", clock.instant().atOffset(ZoneOffset.UTC));
        return values;
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 512 ? value : value.substring(0, 512);
    }
}
