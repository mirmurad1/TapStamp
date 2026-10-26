package az.tapstamp.site.service;

import az.tapstamp.notification.mail.MailSender;
import az.tapstamp.notification.mail.MailTemplates;
import az.tapstamp.security.ratelimit.RateLimitPolicy;
import az.tapstamp.security.ratelimit.RateLimiter;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.web.ClientInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Saytdan gələn demo müraciətləri. Bazada saxlanılmır — şirkətin poçt qutusuna email kimi göndərilir. Email
 * sinxron göndərilir: göndərilmirsə istifadəçi xəta görür və müraciət səssizcə itmir.
 */
@Service
public class DemoRequestService {

    private static final Logger log = LoggerFactory.getLogger(DemoRequestService.class);
    private static final String SCOPE = "site";

    private final RateLimiter rateLimiter;
    private final MailSender mail;
    private final MailTemplates templates;
    private final String inbox;

    public DemoRequestService(RateLimiter rateLimiter, MailSender mail, MailTemplates templates,
                              @Value("${app.site.inbox:}") String inbox) {
        this.rateLimiter = rateLimiter;
        this.mail = mail;
        this.templates = templates;
        this.inbox = inbox;
    }

    /** Formun sahələri; dil — müraciət edənin saytda seçdiyi dil (email həmişə Azərbaycan dilindədir). */
    public record DemoRequest(String name, String phone, String company, String businessType, String branches,
                              String note, String language) {
    }

    public void submit(DemoRequest request, String honeypot, ClientInfo client) {
        rateLimiter.consume(RateLimitPolicy.SITE_DEMO_REQUEST_PER_IP, SCOPE, client.ip());
        if (honeypot != null && !honeypot.isBlank()) {
            // Bot: uğur cavabı verilir ki, forma yoxlamasını öyrənməsin
            log.info("Site demo request dropped: honeypot filled");
            return;
        }
        rateLimiter.consume(RateLimitPolicy.SITE_DEMO_REQUEST_GLOBAL, SCOPE, "all");
        if (inbox == null || inbox.isBlank()) {
            log.warn("Site demo request rejected: app.site.inbox is not configured");
            throw new ApiException(ErrorCode.SITE_INBOX_UNAVAILABLE);
        }
        try {
            mail.send(templates.demoRequest(inbox, clean(request.company()), clean(request.name()),
                    clean(request.phone()), request.businessType(), request.branches(),
                    request.language() == null ? "az" : request.language(), cleanMultiline(request.note())));
        } catch (RuntimeException e) {
            log.warn("Site demo request email failed: {}", e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.SITE_INBOX_UNAVAILABLE);
        }
        log.info("Site demo request sent to inbox");
    }

    /** Bir sətirlik sahələr: idarəetmə simvolları (sətir sonu daxil) boşluqla əvəz olunur — mövzu başlığına düşür. */
    static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\p{Cntrl}", " ").strip();
    }

    /** Qeyd: sətir sonları saxlanılır, qalan idarəetmə simvolları silinir. */
    static String cleanMultiline(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        return value.replace("\r\n", "\n").replaceAll("[\\p{Cntrl}&&[^\n]]", " ").strip();
    }
}
