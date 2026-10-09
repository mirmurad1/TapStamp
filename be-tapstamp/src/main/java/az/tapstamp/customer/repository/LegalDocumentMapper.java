package az.tapstamp.customer.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.tapstamp.customer.consent.LegalDocumentService.Document;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.persistence.TenantMapper;

/** Hüquqi sənədlər və müştərinin razılıqları. SQL: {@code mapper/tenant/customer/LegalDocumentMapper.xml}. */
@TenantMapper
public interface LegalDocumentMapper {

    List<Document> currentDocuments();

    /** Müştərinin qüvvədə olan razılıqlarının sənəd ID-ləri. */
    List<UUID> acceptedDocumentIds(UUID customerId);

    void insertConsent(UUID id, UUID customerId, Document document, Language language, Instant now, String ip,
                       String userAgent);

    void withdrawAll(UUID customerId, Instant now);
}
