package az.tapstamp.security.admin;

import java.util.Optional;

import az.tapstamp.tenancy.RequestScope;

/**
 * Admin hesablarının iki "realm"-ı: şirkət işçiləri (şirkətin bazasında, şirkətin domenində) və platforma
 * adminləri (platforma bazasında, platformanın domenində). Giriş məntiqi eynidir, cədvəllər və cookie fərqlidir.
 * Cədvəl adları sabitdir və heç vaxt istifadəçi məlumatından qurulmur.
 */
public enum AdminRealm {
    STAFF("__Host-cl_staff", "staff_users", "staff_sessions", "staff_recovery_codes", "password_reset_tokens",
            "staff_user_id", "STAFF_SESSION"),
    PLATFORM("__Host-cl_platform", "platform_admins", "platform_admin_sessions", "platform_recovery_codes",
            "platform_password_reset_tokens", "user_id", "PLATFORM_SESSION");

    private final String cookieName;
    private final String usersTable;
    private final String sessionsTable;
    private final String recoveryCodesTable;
    private final String resetTokensTable;
    private final String userIdColumn;
    private final String sessionAuthority;

    AdminRealm(String cookieName, String usersTable, String sessionsTable, String recoveryCodesTable,
               String resetTokensTable, String userIdColumn, String sessionAuthority) {
        this.cookieName = cookieName;
        this.usersTable = usersTable;
        this.sessionsTable = sessionsTable;
        this.recoveryCodesTable = recoveryCodesTable;
        this.resetTokensTable = resetTokensTable;
        this.userIdColumn = userIdColumn;
        this.sessionAuthority = sessionAuthority;
    }

    public static Optional<AdminRealm> forScope(RequestScope scope) {
        return switch (scope) {
            case TENANT -> Optional.of(STAFF);
            case PLATFORM -> Optional.of(PLATFORM);
            case NONE -> Optional.empty();
        };
    }

    public String cookieName() {
        return cookieName;
    }

    String usersTable() {
        return usersTable;
    }

    String sessionsTable() {
        return sessionsTable;
    }

    String recoveryCodesTable() {
        return recoveryCodesTable;
    }

    String resetTokensTable() {
        return resetTokensTable;
    }

    String userIdColumn() {
        return userIdColumn;
    }

    /** Tam (MFA keçmiş) sessiyanın authority-si: STAFF_SESSION və ya PLATFORM_SESSION. */
    public String sessionAuthority() {
        return sessionAuthority;
    }
}
