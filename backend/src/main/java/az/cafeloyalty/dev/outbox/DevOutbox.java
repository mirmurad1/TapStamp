package az.cafeloyalty.dev.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

import az.cafeloyalty.notification.mail.MailSentEvent;
import az.cafeloyalty.shared.id.Ids;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Dev "poçt qutusu": konsol provayderinin "göndərdiyi" son mesajlar yaddaşda saxlanılır ki, dəvət və şifrə bərpası
 * linklərini real email olmadan açmaq mümkün olsun. Yalnız dev profilində mövcuddur.
 */
@Component
@Profile("dev")
public class DevOutbox {

    public record Entry(UUID id, String channel, String to, String subject, String body, String tenantId,
                        Instant createdAt) {
    }

    private static final int CAPACITY = 200;

    private final Deque<Entry> entries = new ConcurrentLinkedDeque<>();
    private final Clock clock;

    public DevOutbox(Clock clock) {
        this.clock = clock;
    }

    @EventListener
    public void onMail(MailSentEvent event) {
        var message = event.message();
        entries.addFirst(new Entry(Ids.newId(), "EMAIL", message.to(), message.subject(), message.textBody(),
                message.tenantId(), clock.instant()));
        while (entries.size() > CAPACITY) {
            entries.pollLast();
        }
    }

    /** Yalnız göstərilən tenant-ın (platforma üçün null) mesajları — şirkətlər bir-birinin poçtunu görmür. */
    public List<Entry> list(String tenantId, String channel) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (Objects.equals(entry.tenantId(), tenantId) && (channel == null || channel.equals(entry.channel()))) {
                result.add(entry);
            }
        }
        return result;
    }
}
