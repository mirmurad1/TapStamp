package az.tapstamp.security.admin;

import java.util.UUID;

import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;

/**
 * Obyekt səviyyəsində giriş nəzarəti (IDOR-un qarşısı). Rol yoxlaması ({@code @PreAuthorize}) "bu əməliyyatı edə
 * bilərmi?" sualına, bu sinif isə "məhz bu obyektlə?" sualına cavab verir.
 *
 * <p>İcazəsi olmayan obyekt üçün {@code NOT_FOUND} qaytarılır (403 yox) — başqa filialın obyektinin mövcud olub-olmadığı
 * da açıqlanmır. Tenant-lar arası izolyasiya isə ayrıca bazalarla fiziki təmin olunur.
 */
public final class AccessPolicy {

    private AccessPolicy() {
    }

    /** Filiala bağlı obyekt (müştəri toxunuşu, mükafat, işçi): MANAGER/STAFF yalnız öz filiallarını görür. */
    public static void requireBranch(AdminPrincipal principal, UUID branchId) {
        if (branchId == null || !principal.canAccessBranch(branchId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    /** Yalnız göstərilən rollardan biri olan istifadəçi; əks halda 403. */
    public static void requireAnyRole(AdminPrincipal principal, AdminRole... roles) {
        for (AdminRole role : roles) {
            if (principal.hasRole(role)) {
                return;
            }
        }
        throw new ApiException(ErrorCode.FORBIDDEN);
    }

    /** Şirkət səviyyəsində məlumat (parametrlər, bütün filialların analitikası) yalnız sahibə açıqdır. */
    public static void requireCompanyWide(AdminPrincipal principal) {
        requireAnyRole(principal, AdminRole.COMPANY_OWNER);
    }
}
