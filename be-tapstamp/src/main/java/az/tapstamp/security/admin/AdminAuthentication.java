package az.tapstamp.security.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Spring Security autentifikasiyası. Authority-lər sessiyanın vəziyyətindən asılıdır: MFA gözləyən sessiyanın
 * heç bir rolu yoxdur, yalnız MFA endpoint-lərinə çıxışı var.
 */
public class AdminAuthentication extends AbstractAuthenticationToken {

    public static final String MFA_PENDING = "MFA_PENDING";
    public static final String MFA_ENROLLMENT = "MFA_ENROLLMENT";

    private final AdminPrincipal principal;

    public AdminAuthentication(AdminPrincipal principal) {
        super(authorities(principal));
        this.principal = principal;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> authorities(AdminPrincipal principal) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        switch (principal.state()) {
            case PENDING_MFA -> authorities.add(new SimpleGrantedAuthority(MFA_PENDING));
            case MFA_ENROLLMENT -> authorities.add(new SimpleGrantedAuthority(MFA_ENROLLMENT));
            case ACTIVE -> {
                authorities.add(new SimpleGrantedAuthority(principal.realm().sessionAuthority()));
                principal.roles().forEach(grant -> authorities.add(new SimpleGrantedAuthority(grant.role().authority())));
            }
        }
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AdminPrincipal getPrincipal() {
        return principal;
    }
}
