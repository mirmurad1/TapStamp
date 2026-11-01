package az.tapstamp.company.service;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.company.repository.CompanyMapper;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.image.SquarePng;
import org.springframework.stereotype.Service;

/**
 * Şirkətin loqosu (müştəri tətbiqinin başlığında). Yalnız PNG və JPEG qəbul olunur (SVG-də skript ola bilər).
 * Kvadrat olmayan şəkil mərkəzdən kvadrat kəsilir, {@value #STORED_SIZE} pikseldən böyükdürsə kiçildilir və PNG kimi
 * yenidən kodlanır — EXIF (məs. GPS) və faylın içinə gizlədilmiş məlumat saxlanılmır.
 */
@Service
public class CompanyLogoService {

    /** Yüklənən faylın ən böyük həcmi. */
    public static final int MAX_BYTES = 1024 * 1024;
    /** Qısa tərəfin ən kiçik ölçüsü. */
    public static final int MIN_SIZE = 192;
    /** Uzun tərəfin ən böyük ölçüsü (yaddaş: açılmış şəkil ən çox ~64 MB). */
    public static final int MAX_SIZE = 4096;
    /** Saxlanılan loqonun ölçüsü (başlıqda 40–64 px göstərilir, yüksək sıxlıqlı ekranlar üçün ehtiyatla). */
    public static final int STORED_SIZE = 512;

    private static final SquarePng IMAGE =
            new SquarePng(MAX_BYTES, MIN_SIZE, MAX_SIZE, STORED_SIZE, ErrorCode.LOGO_INVALID);

    private final CompanyMapper companies;
    private final AuditLogger audit;
    private final Clock clock;

    public CompanyLogoService(CompanyMapper companies, AuditLogger audit, Clock clock) {
        this.companies = companies;
        this.audit = audit;
        this.clock = clock;
    }

    public Optional<CompanyMapper.Logo> logo() {
        return companies.findLogo();
    }

    public void upload(AdminPrincipal principal, byte[] file) {
        AccessPolicy.requireCompanyWide(principal);
        byte[] png = normalize(file);
        companies.updateLogo(png, clock.instant());
        audit.tenant("COMPANY_LOGO_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "company", null,
                Map.of("bytes", png.length));
    }

    public void remove(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        companies.updateLogo(null, clock.instant());
        audit.tenant("COMPANY_LOGO_REMOVED", AuditLogger.ActorType.STAFF, principal.userId(), "company", null,
                Map.of());
    }

    /** Yoxlama (növ, həcm, ölçü həddləri), kvadrata gətirmə və PNG-yə çevirmə. */
    static byte[] normalize(byte[] file) {
        return IMAGE.normalize(file);
    }
}
