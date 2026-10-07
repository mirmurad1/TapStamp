package az.cafeloyalty.support;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import az.cafeloyalty.notification.sms.SmsMessage;
import az.cafeloyalty.notification.sms.SmsSentEvent;
import org.awaitility.Awaitility;
import org.springframework.context.event.EventListener;

/** Testlərdə "göndərilən" SMS-ləri toplayır (konsol provayderi {@link SmsSentEvent} yayımlayır). */
public class TestSmsInbox {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    private final List<SmsMessage> messages = new CopyOnWriteArrayList<>();

    @EventListener
    void onSms(SmsSentEvent event) {
        messages.add(event.message());
    }

    public List<SmsMessage> to(String e164) {
        return messages.stream().filter(message -> message.to().equals(e164)).toList();
    }

    /** SMS arxa planda göndərilir — gözləyirik. */
    public SmsMessage await(String e164, int expectedCount) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> to(e164).size() >= expectedCount);
        return to(e164).get(expectedCount - 1);
    }

    public static String code(SmsMessage message) {
        Matcher matcher = CODE.matcher(message.text());
        if (!matcher.find()) {
            throw new AssertionError("No OTP code in SMS");
        }
        return matcher.group(1);
    }
}
