package az.tapstamp.customer.consent;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.customer.repository.LegalDocumentMapper;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.web.ClientInfo;
import org.springframework.stereotype.Service;

/**
 * Hüquqi sənədlər (məxfilik siyasəti və istifadə şərtləri) və müştərinin razılıqları. Sənədlər versiyalıdır: yeni versiya
 * dərc olunanda müştəri növbəti girişdə yenidən razılıq verir. Razılıqda sənədin versiyası, dil, vaxt, IP və brauzer
 * qeyd olunur.
 */
@Service
public class LegalDocumentService {

    public enum DocumentType {
        PRIVACY_POLICY,
        TERMS;

        /** URL-dəki qısa ad: {@code privacy}, {@code terms}. */
        public String slug() {
            return this == PRIVACY_POLICY ? "privacy" : "terms";
        }

        public static Optional<DocumentType> fromSlug(String slug) {
            String value = slug == null ? "" : slug.toLowerCase(Locale.ROOT);
            for (DocumentType type : values()) {
                if (type.slug().equals(value)) {
                    return Optional.of(type);
                }
            }
            return Optional.empty();
        }
    }

    public record Document(UUID id, DocumentType type, int version, LocalizedText content, Instant publishedAt) {
    }

    /** Müştərinin razılıq verdiyi sənəd (növ + versiya): köhnə versiyaya razılıq yeni versiya üçün keçərli deyil. */
    public record DocumentRef(DocumentType type, int version) {
    }

    private final LegalDocumentMapper documents;
    private final Clock clock;

    public LegalDocumentService(LegalDocumentMapper documents, Clock clock) {
        this.documents = documents;
        this.clock = clock;
    }

    public List<Document> current() {
        return documents.currentDocuments();
    }

    public Optional<Document> current(DocumentType type) {
        return current().stream().filter(document -> document.type() == type).findFirst();
    }

    /** Müştərinin hələ razılıq vermədiyi cari sənədlər. */
    public List<Document> pendingFor(UUID customerId) {
        List<UUID> accepted = documents.acceptedDocumentIds(customerId);
        return current().stream().filter(document -> !accepted.contains(document.id())).toList();
    }

    /**
     * Razılıqları qeyd edir. Göndərilən siyahı bütün cari sənədləri (cari versiyaları ilə) əhatə etməlidir — əks halda
     * {@code CONSENT_REQUIRED}. Artıq razılıq verilmiş sənəd təkrar yazılmır.
     */
    public void accept(UUID customerId, Collection<DocumentRef> accepted, Language language, ClientInfo client) {
        List<Document> required = pendingFor(customerId);
        for (Document document : current()) {
            boolean covered = accepted.contains(new DocumentRef(document.type(), document.version()));
            if (!covered && required.contains(document)) {
                throw new ApiException(ErrorCode.CONSENT_REQUIRED);
            }
        }
        Instant now = clock.instant();
        for (Document document : required) {
            String userAgent = client.userAgent() == null || client.userAgent().length() <= 512
                    ? client.userAgent() : client.userAgent().substring(0, 512);
            documents.insertConsent(Ids.newId(), customerId, document, language, now, client.ip(), userAgent);
        }
    }

    /** Hesab silinəndə razılıqlar geri götürülmüş sayılır (qeyd tarixçə üçün qalır). */
    public void withdrawAll(UUID customerId) {
        documents.withdrawAll(customerId, clock.instant());
    }
}
