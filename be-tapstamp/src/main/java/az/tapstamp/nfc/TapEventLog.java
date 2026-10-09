package az.tapstamp.nfc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.nfc.repository.TapEventMapper;
import az.tapstamp.nfc.repository.TapEventMapper.TapEventRow;
import az.tapstamp.shared.id.Ids;
import org.springframework.stereotype.Component;

/**
 * Toxunuş jurnalı ({@code tap_events}): hər cəhd — qəbul olunan və rədd edilən — dəqiq səbəbi ilə. Cədvəl yalnız
 * əlavə olunandır (tətbiq rolunun UPDATE/DELETE hüququ yoxdur).
 */
@Component
public class TapEventLog {

    public enum Stage {
        VERIFY,
        CLAIM
    }

    /** Sorğunun mənbəyi: IP, brauzer identifikatorunun və User-Agent-in hash-ləri. */
    public record Source(String ip, byte[] browserIdHash, byte[] userAgentHash) {
    }

    /** {@code reason == null} — qəbul olunub, əks halda rədd edilib (dəqiq səbəb). */
    public record Event(Stage stage, UUID branchId, UUID tagId, String reason, Integer counter, Integer counterGap,
                        UUID customerId, UUID tapTokenId, UUID stampId, Source source) {

        public static Event of(Stage stage, UUID branchId, UUID tagId, String reason, Source source) {
            return new Event(stage, branchId, tagId, reason, null, null, null, null, null, source);
        }

        public Event withCounter(Integer value, Integer gap) {
            return new Event(stage, branchId, tagId, reason, value, gap, customerId, tapTokenId, stampId, source);
        }

        public Event withClaim(UUID customer, UUID token, UUID stamp) {
            return new Event(stage, branchId, tagId, reason, counter, counterGap, customer, token, stamp, source);
        }
    }

    private final TapEventMapper events;

    public TapEventLog(TapEventMapper events) {
        this.events = events;
    }

    public UUID record(Event event, Instant at) {
        UUID id = Ids.newId();
        Source source = event.source();
        events.insert(new TapEventRow(id, event.branchId(), event.tagId(), event.stage().name(),
                event.reason() == null ? "ACCEPTED" : "REJECTED", event.reason(), event.counter(), event.counterGap(),
                event.customerId(), event.tapTokenId(), event.stampId(),
                source == null ? null : source.ip(),
                source == null ? null : source.browserIdHash(),
                source == null ? null : source.userAgentHash(),
                at));
        return id;
    }

    /** Tokenin bu müştəri üçün son rədd səbəbi (səhifə yenilənəndə eyni xəta göstərilsin). */
    public Optional<String> lastClaimRejection(UUID tapTokenId, UUID customerId) {
        return Optional.ofNullable(events.lastClaimRejection(tapTokenId, customerId));
    }
}
