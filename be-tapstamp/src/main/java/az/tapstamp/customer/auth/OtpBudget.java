package az.tapstamp.customer.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.customer.repository.OtpChallengeMapper;
import az.tapstamp.notification.mail.MailSender;
import az.tapstamp.notification.mail.MailTemplates;
import az.tapstamp.notification.sms.SmsLog;
import az.tapstamp.notification.sms.repository.SmsLimitMapper;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.tenancy.TenantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Giriş kodlarının (OTP) gündəlik SMS büdcəsi — SMS xərcinin və hücumun (SMS pumping) qarşısı:
 * <ul>
 *   <li><b>Şirkət:</b> gündə ən çox N kod <b>yeni nömrələrə</b> (sistemdə olmayan); qeydiyyatlı müştərilərin kodları
 *       limitə düşmür. N — platformanın şirkət üçün təyin etdiyi dəyər, yoxdursa {@code app.sms-limits.tenant-daily-new}.
 *       Gün — şirkətin saat qurşağı ilə.</li>
 *   <li><b>Platforma:</b> bütün şirkətlər üzrə gündə ən çox {@code app.sms-limits.platform-daily} OTP SMS (sabit büdcə,
 *       Bakı vaxtı ilə gün).</li>
 * </ul>
 * Limitin 80%-i və 100%-i dolanda platformaya bir dəfə (gün ərzində) email xəbərdarlığı və audit yazısı gedir.
 * Sayğaclar bazadan oxunur: paralel sorğularda limit bir-iki kod aşıla bilər — bu, qəbul edilmiş haldır.
 */
@Component
public class OtpBudget {

    private static final Logger log = LoggerFactory.getLogger(OtpBudget.class);
    static final String PLATFORM_SCOPE = "platform";

    private final OtpChallengeMapper challenges;
    private final SmsLimitMapper limits;
    private final AuditLogger audit;
    private final MailSender mail;
    private final MailTemplates templates;
    private final TaskExecutor executor;
    private final Clock clock;
    private final int tenantDefault;
    // Testlər büdcəni dəyişə bilsin deyə final deyil
    private volatile long platformDaily;
    private final String alertEmail;

    public OtpBudget(OtpChallengeMapper challenges, SmsLimitMapper limits, AuditLogger audit, MailSender mail,
                     MailTemplates templates, @Qualifier("applicationTaskExecutor") TaskExecutor executor, Clock clock,
                     @Value("${app.sms-limits.tenant-daily-new:100}") int tenantDefault,
                     @Value("${app.sms-limits.platform-daily:1500}") long platformDaily,
                     @Value("${app.sms-limits.alert-email:}") String alertEmail) {
        this.challenges = challenges;
        this.limits = limits;
        this.audit = audit;
        this.mail = mail;
        this.templates = templates;
        this.executor = executor;
        this.clock = clock;
        this.tenantDefault = tenantDefault;
        this.platformDaily = platformDaily;
        this.alertEmail = alertEmail;
    }

    public int tenantDefault() {
        return tenantDefault;
    }

    public long platformDaily() {
        return platformDaily;
    }

    /** Şirkətin limiti: fərdi təyin olunubsa o, yoxdursa standart. */
    public int tenantLimit(TenantInfo tenant) {
        return limits.tenantLimit(tenant.id()).orElse(tenantDefault);
    }

    /** Şirkətin bu gün yeni nömrələrə göndərdiyi kodlar (cari tenant kontekstində). */
    public long tenantUsedToday(TenantInfo tenant) {
        ZoneId zone = tenant.timezone();
        return challenges.countNewNumbersSince(LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant());
    }

    public long platformUsedToday() {
        return limits.countOtpSince(LocalDate.now(clock.withZone(SmsLog.REPORT_ZONE)).atStartOfDay(SmsLog.REPORT_ZONE)
                .toInstant());
    }

    /**
     * Yeni kod göndərilməzdən əvvəl (cari tenant kontekstində; gün — şirkətin saat qurşağı ilə): limit dolubsa
     * {@code SMS_DAILY_LIMIT_REACHED}.
     *
     * @param newNumber nömrə bu şirkətdə qeydiyyatlı deyil (giriş kodu)
     */
    public void check(TenantInfo tenant, boolean newNumber) {
        ZoneId tenantZone = tenant.timezone();
        Instant now = clock.instant();
        long platformUsed = platformUsedToday();
        LocalDate platformDay = LocalDate.ofInstant(now, SmsLog.REPORT_ZONE);
        if (platformUsed >= platformDaily) {
            alert(platformDay, PLATFORM_SCOPE, null, 100, platformUsed, platformDaily);
            throw new ApiException(ErrorCode.SMS_DAILY_LIMIT_REACHED);
        }
        if (newNumber) {
            int limit = tenantLimit(tenant);
            long used = tenantUsedToday(tenant);
            LocalDate day = LocalDate.ofInstant(now, tenantZone);
            if (used >= limit) {
                alert(day, "tenant:" + tenant.id(), tenant, 100, used, limit);
                throw new ApiException(ErrorCode.SMS_DAILY_LIMIT_REACHED);
            }
            if ((used + 1) * 5 >= limit * 4L) {
                alert(day, "tenant:" + tenant.id(), tenant, 80, used + 1, limit);
            }
        }
        if ((platformUsed + 1) * 5 >= platformDaily * 4) {
            alert(platformDay, PLATFORM_SCOPE, null, 80, platformUsed + 1, platformDaily);
        }
    }

    /** Hər gün, sahə və səviyyə üçün bir dəfə: audit və (ünvan təyin olunubsa) email. */
    private void alert(LocalDate day, String scope, TenantInfo tenant, int level, long used, long limit) {
        if (limits.recordAlert(day, scope, level, clock.instant()) != 1) {
            return;
        }
        String target = tenant == null ? PLATFORM_SCOPE : tenant.slug();
        log.warn("SMS limit {}% reached: {} ({} / {})", level, target, used, limit);
        audit.platform("SMS_LIMIT_ALERT", null, "sms_limit", target,
                Map.of("level", level, "used", used, "limit", limit, "day", day.toString()));
        if (alertEmail == null || alertEmail.isBlank()) {
            return;
        }
        executor.execute(() -> {
            try {
                mail.send(templates.smsLimitAlert(alertEmail, Language.DEFAULT, target, level, used, limit, day));
            } catch (RuntimeException e) {
                log.warn("SMS limit alert email failed: {}", e.getClass().getSimpleName());
            }
        });
    }
}
