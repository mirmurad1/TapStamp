package az.tapstamp.support;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import az.tapstamp.notification.sms.SmsDeliveryException;
import az.tapstamp.notification.sms.SmsMessage;
import az.tapstamp.notification.sms.SmsProvider;

/**
 * Testlərdə SMS provayderi: adi halda konsol provayderinə ötürür; qeydə alınmış nömrə üçün növbəti göndərişlərdə
 * verilmiş xətaları (ardıcıl) atır — kampaniya göndərişinin təkrar cəhd, dayandırma və "nəticə məlum deyil" hallarını
 * sınamaq üçün.
 */
public class TestSmsFailures implements SmsProvider {

    private final SmsProvider delegate;
    private final Map<String, Deque<SmsDeliveryException.Kind>> failures = new ConcurrentHashMap<>();

    public TestSmsFailures(SmsProvider delegate) {
        this.delegate = delegate;
    }

    /** Bu nömrəyə növbəti göndərişlər verilmiş xətalarla bitəcək (sonra adi qaydada göndərilir). */
    public void failNext(String e164, SmsDeliveryException.Kind... kinds) {
        failures.computeIfAbsent(e164, k -> new ArrayDeque<>()).addAll(java.util.List.of(kinds));
    }

    public void clear() {
        failures.clear();
    }

    @Override
    public void send(SmsMessage message) {
        Deque<SmsDeliveryException.Kind> queue = failures.get(message.to());
        SmsDeliveryException.Kind kind = queue == null ? null : queue.pollFirst();
        if (kind == null) {
            delegate.send(message);
            return;
        }
        if (kind == SmsDeliveryException.Kind.UNKNOWN) {
            // Provayder SMS-i qəbul edib, amma cavab çatmayıb
            delegate.send(message);
        }
        throw new SmsDeliveryException(kind, "test failure " + kind);
    }
}
