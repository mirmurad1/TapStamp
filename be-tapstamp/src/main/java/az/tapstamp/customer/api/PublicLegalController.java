package az.tapstamp.customer.api;

import java.time.Instant;
import java.util.List;

import az.tapstamp.customer.consent.LegalDocumentService;
import az.tapstamp.customer.consent.LegalDocumentService.DocumentType;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Hüquqi sənədlərin cari versiyaları (3 dildə) — qeydiyyat ekranı və ayrıca səhifələr üçün. */
@RestController
@RequestMapping("/api/v1/public/legal")
@Tag(name = "İctimai")
public class PublicLegalController {

    private final LegalDocumentService legal;

    public PublicLegalController(LegalDocumentService legal) {
        this.legal = legal;
    }

    public record LegalDocumentSummary(DocumentType type, String slug, int version, Instant publishedAt) {
    }

    public record LegalDocumentResponse(DocumentType type, int version, LocalizedText content, Instant publishedAt) {
    }

    @GetMapping
    @Operation(summary = "Cari hüquqi sənədlər", description = "Qeydiyyatda razılıq verilməli sənədlərin növləri və versiyaları.")
    List<LegalDocumentSummary> list() {
        return legal.current().stream()
                .map(document -> new LegalDocumentSummary(document.type(), document.type().slug(), document.version(),
                        document.publishedAt()))
                .toList();
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Sənədin mətni", description = "`privacy` — məxfilik siyasəti, `terms` — istifadə şərtləri (3 dildə).")
    LegalDocumentResponse document(@PathVariable String slug) {
        DocumentType type = DocumentType.fromSlug(slug).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        LegalDocumentService.Document document = legal.current(type)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return new LegalDocumentResponse(document.type(), document.version(), document.content(), document.publishedAt());
    }
}
