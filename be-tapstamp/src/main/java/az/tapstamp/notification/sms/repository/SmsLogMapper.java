package az.tapstamp.notification.sms.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.PlatformMapper;
import org.jspecify.annotations.Nullable;

/** Göndərilən SMS-lərin jurnalı ({@code sms_messages}, platforma bazası). SQL: {@code mapper/platform/notification/SmsLogMapper.xml}. */
@PlatformMapper
public interface SmsLogMapper {

    /** {@code text} — kod gizlədilmiş mətn; {@code codeEnc} — OTP kodu şifrəli (mətndə kod yoxdursa null). */
    record Entry(UUID id, UUID tenantId, String phone, String text, byte @Nullable [] codeEnc, String purpose,
                 String encoding, int segments, String status, @Nullable String error, Instant createdAt) {
    }

    /** Şirkət üzrə uğurla göndərilmiş SMS-lərin sayı: cəmi, giriş kodları və kampaniyalar. */
    record Usage(UUID tenantId, String slug, LocalizedText displayName, long messages, long otp, long campaign) {
    }

    /** Jurnaldakı SMS: şirkət, nömrə, mətn (kod gizlədilmiş), nəticə və xəta. */
    record Message(UUID id, Instant createdAt, UUID tenantId, String slug, LocalizedText displayName, String phone,
                   String text, String purpose, String status, @Nullable String error) {
    }

    void insert(Entry entry);

    /**
     * [from, to) aralığında SMS-lər, yenidən köhnəyə.
     *
     * @param status      SENT / FAILED; null — hamısı
     * @param phoneDigits nömrənin rəqəmləri (tam və ya hissə); null — hamısı
     */
    List<Message> messages(Instant from, Instant to, @Nullable String status, @Nullable String phoneDigits, int limit,
                           int offset);

    long countMessages(Instant from, Instant to, @Nullable String status, @Nullable String phoneDigits);

    /** [from, to) aralığında uğurla SMS göndərmiş şirkətlər, ən çoxdan aza. */
    List<Usage> usage(Instant from, Instant to);
}
