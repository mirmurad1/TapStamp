package az.cafeloyalty.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import az.cafeloyalty.company.service.CompanySettings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Şirkətin audit jurnalına baxış (yalnız oxuma; jurnal yalnız əlavə olunur). İcraçının adı işçilər cədvəlindən
 * götürülür; şəxsi məlumat jurnalda saxlanılmır.
 */
@Service
public class AuditLogQuery {

    public static final int PAGE = 50;

    public record Entry(UUID id, String actorType, UUID actorId, String actorName, String action, String targetType,
                        String targetId, String details, String ip, Instant createdAt) {
    }

    private final JdbcClient jdbc;
    private final CompanySettings company;

    public AuditLogQuery(@Qualifier("tenantJdbc") JdbcClient jdbc, CompanySettings company) {
        this.jdbc = jdbc;
        this.company = company;
    }

    /** Yenidən köhnəyə; {@code before} — əvvəlki səhifənin son elementinin vaxtı (keyset səhifələmə). */
    public List<Entry> list(String action, UUID actorId, LocalDate from, LocalDate to, Instant before) {
        ZoneId zone = company.current().timezone();
        return jdbc.sql("""
                        SELECT a.id, a.actor_type, a.actor_id, coalesce(u.display_name, u.email) AS actor_name, a.action,
                               a.target_type, a.target_id, a.details::text AS details, host(a.ip) AS ip, a.created_at
                        FROM audit_logs a LEFT JOIN staff_users u ON a.actor_type = 'STAFF' AND u.id = a.actor_id
                        WHERE (CAST(:action AS text) IS NULL OR a.action = CAST(:action AS text))
                          AND (CAST(:actor AS uuid) IS NULL OR a.actor_id = CAST(:actor AS uuid))
                          AND (CAST(:from AS timestamptz) IS NULL OR a.created_at >= CAST(:from AS timestamptz))
                          AND (CAST(:to AS timestamptz) IS NULL OR a.created_at < CAST(:to AS timestamptz))
                          AND (CAST(:before AS timestamptz) IS NULL OR a.created_at < CAST(:before AS timestamptz))
                        ORDER BY a.created_at DESC, a.id DESC LIMIT :limit""")
                .param("action", action)
                .param("actor", actorId)
                .param("from", from == null ? null : from.atStartOfDay(zone).toOffsetDateTime())
                .param("to", to == null ? null : to.plusDays(1).atStartOfDay(zone).toOffsetDateTime())
                .param("before", before == null ? null : before.atOffset(ZoneOffset.UTC))
                .param("limit", PAGE)
                .query((rs, n) -> new Entry(rs.getObject("id", UUID.class), rs.getString("actor_type"),
                        rs.getObject("actor_id", UUID.class), rs.getString("actor_name"), rs.getString("action"),
                        rs.getString("target_type"), rs.getString("target_id"), rs.getString("details"),
                        rs.getString("ip"), instant(rs.getTimestamp("created_at"))))
                .list();
    }

    /** Filtr üçün jurnalda rast gəlinən əməliyyat adları. */
    public List<String> actions() {
        return jdbc.sql("SELECT DISTINCT action FROM audit_logs ORDER BY action").query(String.class).list();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
