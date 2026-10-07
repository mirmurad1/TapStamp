package az.cafeloyalty.nfc;

import static az.cafeloyalty.shared.persistence.SqlTime.ts;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import az.cafeloyalty.shared.id.Ids;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fırıldaq işarələri ({@code fraud_flags}, plan §9.6). Eyni növ işarə eyni çip/müştəri üçün qısa müddətdə
 * təkrarlanmır — hücum zamanı cədvəl dolmasın (hər cəhdin özü {@code tap_events}-dədir).
 */
@Component
public class FraudFlags {

    public enum Type {
        REPLAY_ATTEMPT,
        COUNTER_GAP,
        RAPID_MULTI_ACCOUNT,
        COOLDOWN_HAMMERING,
        BINDING_MISMATCH
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH
    }

    /** Eyni işarənin təkrar yaradılmadığı müddət. */
    static final Duration DEDUPLICATION_WINDOW = Duration.ofHours(1);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    public FraudFlags(@Qualifier("tenantJdbc") JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** İşarə yaradır (son bir saatda eyni çip və müştəri üçün həll olunmamış eyni işarə yoxdursa). */
    public boolean raise(Type type, Severity severity, UUID tagId, UUID customerId, Map<String, ?> details,
                         Instant now) {
        Boolean recent = jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM fraud_flags
                                       WHERE type = :type AND resolved_at IS NULL AND created_at > :since
                                         AND tag_id IS NOT DISTINCT FROM CAST(:tagId AS uuid)
                                         AND customer_id IS NOT DISTINCT FROM CAST(:customerId AS uuid))""")
                .param("type", type.name())
                .param("since", ts(now.minus(DEDUPLICATION_WINDOW)))
                .param("tagId", tagId)
                .param("customerId", customerId)
                .query(Boolean.class)
                .single();
        if (Boolean.TRUE.equals(recent)) {
            return false;
        }
        jdbc.sql("""
                        INSERT INTO fraud_flags (id, customer_id, tag_id, type, severity, details, created_at)
                        VALUES (:id, :customerId, :tagId, :type, :severity, CAST(:details AS jsonb), :now)""")
                .param("id", Ids.newId())
                .param("customerId", customerId)
                .param("tagId", tagId)
                .param("type", type.name())
                .param("severity", severity.name())
                .param("details", details == null || details.isEmpty() ? null : JSON.writeValueAsString(details))
                .param("now", ts(now))
                .update();
        return true;
    }
}
