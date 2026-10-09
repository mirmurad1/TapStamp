package az.tapstamp.nfc;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.nfc.repository.FraudFlagMapper;
import az.tapstamp.shared.id.Ids;
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

    private final FraudFlagMapper flags;

    public FraudFlags(FraudFlagMapper flags) {
        this.flags = flags;
    }

    /** İşarə yaradır (son bir saatda eyni çip və müştəri üçün həll olunmamış eyni işarə yoxdursa). */
    public void raise(Type type, Severity severity, UUID tagId, UUID customerId, Map<String, ?> details,
                      Instant now) {
        if (flags.existsOpen(type.name(), tagId, customerId, now.minus(DEDUPLICATION_WINDOW))) {
            return;
        }
        flags.insert(Ids.newId(), customerId, tagId, type.name(), severity.name(),
                details == null || details.isEmpty() ? null : JSON.writeValueAsString(details), now);
    }
}
