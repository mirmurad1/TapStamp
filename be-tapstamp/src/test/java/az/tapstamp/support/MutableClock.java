package az.tapstamp.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Testlər üçün saat: real vaxt + sürüşmə. Sessiyanın fəaliyyətsizlik limiti, linklərin ömrü və TOTP addımları
 * gözləmədən yoxlanılır. Saat yalnız irəli çəkilir — eyni kontekstdəki digər testlərə təsir etmir.
 */
public class MutableClock extends Clock {

    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

    public void advance(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Clock can only move forward");
        }
        offset.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(offset.get());
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
