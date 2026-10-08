package az.tapstamp.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import az.tapstamp.notification.mail.MailMessage;
import az.tapstamp.notification.mail.MailSentEvent;
import org.springframework.context.event.EventListener;

/** Testlərdə "göndərilən" emailləri toplayır (konsol provayderi {@link MailSentEvent} yayımlayır). */
public class TestMailbox {

    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");
    private static final Pattern LINK = Pattern.compile("(https://\\S+#token=[A-Za-z0-9_-]+)");

    private final List<MailMessage> messages = new CopyOnWriteArrayList<>();

    @EventListener
    void onMail(MailSentEvent event) {
        messages.add(event.message());
    }

    public List<MailMessage> to(String email) {
        return messages.stream().filter(message -> message.to().equalsIgnoreCase(email)).toList();
    }

    /** Email arxa planda göndərilir — gözləyirik. */
    public MailMessage awaitMessage(String email, int expectedCount) {
        await().atMost(Duration.ofSeconds(10)).until(() -> to(email).size() >= expectedCount);
        return to(email).get(expectedCount - 1);
    }

    public static String token(MailMessage message) {
        Matcher matcher = TOKEN.matcher(message.textBody());
        if (!matcher.find()) {
            throw new AssertionError("No token in mail: " + message.subject());
        }
        return matcher.group(1);
    }

    public static String link(MailMessage message) {
        Matcher matcher = LINK.matcher(message.textBody());
        if (!matcher.find()) {
            throw new AssertionError("No link in mail: " + message.subject());
        }
        return matcher.group(1);
    }
}
