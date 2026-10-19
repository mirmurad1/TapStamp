package az.tapstamp.notification.sms;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import az.tapstamp.notification.sms.repository.SmsLogMapper;
import az.tapstamp.security.crypto.Aad;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.security.crypto.KeyPurpose;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.pii.PhoneMasker;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Göndərilən SMS-lərin jurnalı (platforma bazası, {@code sms_messages}): hansı şirkət, hansı nömrəyə, hansı mətn (OTP
 * kodu mətndə gizlədilir, özü ayrıca şifrəli saxlanılır), neçə seqment, nəticə. Platforma panelindəki "SMS hesabatı" buradan oxunur.
 *
 * <p>Jurnala yazmaq alınmasa, SMS göndərişi pozulmur — yalnız log yazılır.
 */
@Service
public class SmsLog {

    /** Hesabat üçün günlər Bakı vaxtı ilə sayılır. */
    public static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Baku");

    private static final Logger log = LoggerFactory.getLogger(SmsLog.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final SmsLogMapper mapper;
    private final EnvelopeCipher cipher;
    private final Clock clock;

    public SmsLog(SmsLogMapper mapper, @Qualifier("platformCipher") EnvelopeCipher cipher, Clock clock) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.clock = clock;
    }

    /**
     * @param sentText    göndərilən mətn (seqmentlər bununla hesablanır)
     * @param storedText  jurnalda saxlanılan mətn (OTP kodu gizlədilmiş)
     * @param code        mətndəki OTP kodu — yalnız şifrəli saxlanılır (kod yoxdursa null)
     * @param error       göndəriş alınmayıbsa — səbəb (provayderin xəta kodu; mətn və açar daxil deyil)
     */
    public void record(UUID tenantId, String phone, String sentText, String storedText, @Nullable String code,
                       @Nullable String error) {
        try {
            UUID id = Ids.newId();
            SmsSegments.Info info = SmsSegments.analyze(sentText);
            byte[] codeEnc = code == null ? null
                    : cipher.encryptString(KeyPurpose.PII, code, Aad.platformField("sms_messages", "code_enc", id));
            mapper.insert(new SmsLogMapper.Entry(id, tenantId, phone, storedText, codeEnc, "OTP",
                    info.encoding().name(), info.segments(),
                    error == null ? "SENT" : "FAILED",
                    error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH),
                    clock.instant()));
        } catch (RuntimeException e) {
            log.warn("SMS jurnalına yazılmadı: tenant={}, to={} ({})", tenantId, PhoneMasker.mask(phone),
                    e.getClass().getSimpleName());
        }
    }

    /** [from, to] günləri (hər ikisi daxil, Bakı vaxtı) üzrə şirkətlərin SMS istifadəsi. */
    public List<SmsLogMapper.Usage> usage(LocalDate from, LocalDate to) {
        return mapper.usage(start(from), start(to.plusDays(1)));
    }

    public record MessagePage(List<SmsLogMapper.Message> items, int page, int size, long total) {
    }

    /**
     * [from, to] günləri üzrə SMS-lər, səhifə-səhifə (page 0-dan başlayır).
     *
     * @param status SENT / FAILED; null — hamısı
     * @param phone  nömrə və ya onun hissəsi (məs. "+994 50 123", "0501234567"); null və ya boş — hamısı
     */
    public MessagePage messages(LocalDate from, LocalDate to, @Nullable String status, @Nullable String phone, int page,
                                int size) {
        String digits = phoneDigits(phone);
        Instant start = start(from);
        Instant end = start(to.plusDays(1));
        return new MessagePage(mapper.messages(start, end, status, digits, size, page * size), page, size,
                mapper.countMessages(start, end, status, digits));
    }

    /** Axtarış üçün nömrənin rəqəmləri; yerli yazılış (050…) beynəlxalqa (99450…) çevrilir. */
    static @Nullable String phoneDigits(@Nullable String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        return digits.length() == 10 && digits.startsWith("0") ? "994" + digits.substring(1) : digits;
    }

    private static Instant start(LocalDate day) {
        return day.atStartOfDay(REPORT_ZONE).toInstant();
    }
}
