package az.cafeloyalty.customer.consent;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.shared.i18n.LocalizedText;
import az.cafeloyalty.shared.id.Ids;
import az.cafeloyalty.shared.persistence.LocalizedTextConverter;
import az.cafeloyalty.shared.web.ClientInfo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
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

    private static final LocalizedTextConverter TEXT = new LocalizedTextConverter();

    private final JdbcClient jdbc;
    private final Clock clock;

    public LegalDocumentService(@Qualifier("tenantJdbc") JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public List<Document> current() {
        return jdbc.sql("""
                        SELECT id, type, doc_version, content::text AS content, published_at FROM legal_documents
                        WHERE is_current ORDER BY type""")
                .query((rs, n) -> {
                    Timestamp published = rs.getTimestamp("published_at");
                    return new Document(rs.getObject("id", UUID.class), DocumentType.valueOf(rs.getString("type")),
                            rs.getInt("doc_version"), TEXT.convertToEntityAttribute(rs.getString("content")),
                            published.toInstant());
                })
                .list();
    }

    public Optional<Document> current(DocumentType type) {
        return current().stream().filter(document -> document.type() == type).findFirst();
    }

    /** Müştərinin hələ razılıq vermədiyi cari sənədlər. */
    public List<Document> pendingFor(UUID customerId) {
        List<UUID> accepted = jdbc.sql("SELECT document_id FROM consents WHERE customer_id = :id AND withdrawn_at IS NULL")
                .param("id", customerId)
                .query(UUID.class)
                .list();
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
            jdbc.sql("""
                            INSERT INTO consents (id, customer_id, document_id, document_type, document_version, language,
                                                  accepted_at, ip, user_agent)
                            VALUES (:id, :customerId, :documentId, :type, :version, :language, :now, CAST(:ip AS inet), :ua)""")
                    .param("id", Ids.newId())
                    .param("customerId", customerId)
                    .param("documentId", document.id())
                    .param("type", document.type().name())
                    .param("version", document.version())
                    .param("language", language.tag())
                    .param("now", now.atOffset(ZoneOffset.UTC))
                    .param("ip", client.ip())
                    .param("ua", client.userAgent() == null || client.userAgent().length() <= 512
                            ? client.userAgent() : client.userAgent().substring(0, 512))
                    .update();
        }
    }

    /** Hesab silinəndə razılıqlar geri götürülmüş sayılır (qeyd tarixçə üçün qalır). */
    public void withdrawAll(UUID customerId) {
        jdbc.sql("UPDATE consents SET withdrawn_at = :now WHERE customer_id = :id AND withdrawn_at IS NULL")
                .param("now", clock.instant().atOffset(ZoneOffset.UTC))
                .param("id", customerId)
                .update();
    }
}
