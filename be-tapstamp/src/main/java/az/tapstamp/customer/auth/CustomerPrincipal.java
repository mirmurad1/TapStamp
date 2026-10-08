package az.tapstamp.customer.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.tapstamp.shared.i18n.Language;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Daxil olmuş müştəri və onun cari sessiyası. */
public record CustomerPrincipal(UUID customerId, UUID sessionId, Language language, Instant stepUpAt) {

    /** Spring Security authority-si: müştəri endpoint-ləri yalnız bununla açılır. */
    public static final String AUTHORITY = "CUSTOMER_SESSION";

    /** Spring Security autentifikasiyası (authority: {@link #AUTHORITY}). */
    public static class Authentication extends AbstractAuthenticationToken {

        private final CustomerPrincipal principal;

        public Authentication(CustomerPrincipal principal) {
            super(List.of(new SimpleGrantedAuthority(AUTHORITY)));
            this.principal = principal;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public CustomerPrincipal getPrincipal() {
            return principal;
        }
    }
}
