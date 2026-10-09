package az.tapstamp.staff.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.security.admin.AdminAccount;
import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.TenantMapper;
import az.tapstamp.staff.service.InvitationService.InvitationView;

/**
 * Komanda (işçilər və rolları) və işçi dəvətləri. Dəvət tokeninin yalnız hash-i saxlanılır.
 * SQL: {@code mapper/tenant/staff/StaffMapper.xml}.
 */
@TenantMapper
public interface StaffMapper {

    /** Komanda siyahısının sətri (dəvət gözləyən və silinmiş işçilər daxil deyil). */
    record MemberRow(UUID id, String email, String displayName, AdminAccount.AccountStatus status,
                     Instant totpEnabledAt, Instant lastLoginAt) {
    }

    /** İşçinin rolu (filial səviyyəli rollar üçün filialla). */
    record RoleRow(UUID staffUserId, AdminRole role, UUID branchId) {
    }

    /** Hələ qəbul olunmamış, etibarlı dəvət. */
    record OpenInvitation(UUID id, String email, AdminRole role, UUID branchId, Language language) {
    }

    // --- Komanda ---

    List<MemberRow> members();

    List<RoleRow> roles();

    void insertRole(UUID id, UUID staffUserId, AdminRole role, UUID branchId, Instant now);

    /** Aktiv sahiblərin sayı ({@code excluded} sayılmır) — son sahib bloklana və ya silinə bilməz. */
    int countActiveOwners(UUID excluded);

    // --- Dəvətlər ---

    List<InvitationView> pendingInvitations(Instant now);

    void revokeInvitation(UUID id, Instant now);

    /** Eyni email-ə yeni dəvət göndəriləndə köhnələri ləğv olunur. */
    void revokeInvitationsForEmail(String email, Instant now);

    void insertInvitation(UUID id, String email, AdminRole role, UUID branchId, byte[] tokenHash, Instant expiresAt,
                          UUID invitedBy, Instant now, Language language);

    /** {@code lock} — dəvət sətri kilidlənir (eyni link ilə paralel iki qəbul iki hesab yaratmasın). */
    Optional<OpenInvitation> findOpenInvitation(byte[] tokenHash, Instant now, boolean lock);

    void markInvitationAccepted(UUID id, Instant now);

    boolean activeBranchExists(UUID branchId);

    Optional<LocalizedText> findBranchName(UUID branchId);

    Optional<LocalizedText> findCompanyName();
}
