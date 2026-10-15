package az.tapstamp.notification.sms;

import java.time.Clock;
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
        return mapper.usage(from.atStartOfDay(REPORT_ZONE).toInstant(),
                to.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant());
    }
}
