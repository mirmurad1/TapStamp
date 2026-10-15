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

    record Entry(UUID id, UUID tenantId, byte[] phoneEnc, String phoneMasked, String text, String purpose,
                 String encoding, int segments, String status, @Nullable String error, Instant createdAt) {
    }

    /** Şirkət üzrə cəm: mesaj, seqment (provayder seqmentə görə hesablayır) və uğursuz göndərişlər. */
    record Usage(UUID tenantId, String slug, LocalizedText displayName, long messages, long segments, long failed) {
    }

    void insert(Entry entry);

    /** [from, to) aralığında SMS göndərmiş şirkətlər, ən çox seqmentdən aza. */
    List<Usage> usage(Instant from, Instant to);
}
