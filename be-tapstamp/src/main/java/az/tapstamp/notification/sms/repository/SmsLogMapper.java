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

    /** Şirkət üzrə uğurla göndərilmiş SMS-lərin sayı. */
    record Usage(UUID tenantId, String slug, LocalizedText displayName, long messages) {
    }

    void insert(Entry entry);

    /** [from, to) aralığında uğurla SMS göndərmiş şirkətlər, ən çoxdan aza. */
    List<Usage> usage(Instant from, Instant to);
}
