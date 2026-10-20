package az.tapstamp.notification.sms.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.persistence.PlatformMapper;
import org.jspecify.annotations.Nullable;

/** SMS limitləri: şirkətin fərdi gündəlik limiti, platformanın günlük OTP sayı və xəbərdarlıqlar. SQL: {@code mapper/platform/notification/SmsLimitMapper.xml}. */
@PlatformMapper
public interface SmsLimitMapper {

    Optional<Integer> tenantLimit(UUID tenantId);

    void upsertTenantLimit(UUID tenantId, int limit, @Nullable UUID updatedBy, Instant now);

    void deleteTenantLimit(UUID tenantId);

    /** {@code since}-dən bəri göndərilən OTP SMS-lərinin sayı (bütün şirkətlər). */
    long countOtpSince(Instant since);

    /** Xəbərdarlıq bu gün, bu sahə və səviyyə üçün ilk dəfədirsə 1, artıq göndərilibsə 0. */
    int recordAlert(LocalDate day, String scope, int level, Instant now);
}
