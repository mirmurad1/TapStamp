package az.cafeloyalty.platform.api;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platforma audit jurnalı (şirkətlərin yaradılması, dayandırılması, domenlər, sahiblərin 2FA-sı) — yalnız oxuma. */
@RestController
@RequestMapping("/api/v1/platform/audit-logs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Platforma: audit jurnalı")
public class PlatformAuditController {

    static final int PAGE = 50;

    public record PlatformAuditEntry(UUID id, UUID actorId, String actorName, String action, String targetType,
                                     String targetId, String details, String ip, Instant createdAt) {
    }

    private final JdbcClient platform;

    public PlatformAuditController(@Qualifier("platformJdbc") JdbcClient platform) {
        this.platform = platform;
    }

    @GetMapping
    @Operation(summary = "Platforma audit jurnalı", description = "Yenidən köhnəyə, 50-lik səhifələr; növbəti səhifə üçün `before`.")
    List<PlatformAuditEntry> list(@RequestParam(required = false) @Pattern(regexp = "^[A-Z_]{1,64}$") String action,
                                  @RequestParam(required = false) Instant before) {
        return platform.sql("""
                        SELECT a.id, a.actor_id, coalesce(p.display_name, p.email) AS actor_name, a.action, a.target_type,
                               a.target_id, a.details::text AS details, host(a.ip) AS ip, a.created_at
                        FROM platform_audit_logs a LEFT JOIN platform_admins p ON p.id = a.actor_id
                        WHERE (CAST(:action AS text) IS NULL OR a.action = CAST(:action AS text))
                          AND (CAST(:before AS timestamptz) IS NULL OR a.created_at < CAST(:before AS timestamptz))
                        ORDER BY a.created_at DESC, a.id DESC LIMIT :limit""")
                .param("action", action)
                .param("before", before == null ? null : before.atOffset(ZoneOffset.UTC))
                .param("limit", PAGE)
                .query((rs, n) -> new PlatformAuditEntry(rs.getObject("id", UUID.class),
                        rs.getObject("actor_id", UUID.class), rs.getString("actor_name"), rs.getString("action"),
                        rs.getString("target_type"), rs.getString("target_id"), rs.getString("details"),
                        rs.getString("ip"), instant(rs.getTimestamp("created_at"))))
                .list();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
