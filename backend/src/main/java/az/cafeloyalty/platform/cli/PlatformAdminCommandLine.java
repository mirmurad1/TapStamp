package az.cafeloyalty.platform.cli;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import az.cafeloyalty.security.admin.AdminAccount;
import az.cafeloyalty.security.admin.AdminAccountStore;
import az.cafeloyalty.security.admin.AdminAuthService;
import az.cafeloyalty.security.admin.AdminRealm;
import az.cafeloyalty.security.admin.AdminRealms;
import az.cafeloyalty.security.password.PasswordHasher;
import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.shared.pii.EmailMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * İlk platforma admininin yaradılması (prod-da veb interfeysdən qeydiyyat yoxdur):
 *
 * <pre>
 * PLATFORM_ADMIN_PASSWORD='...' java -jar app.jar --spring.main.web-application-type=none \
 *   --create-platform-admin=admin@example.az [--display-name="Ad Soyad"]
 * </pre>
 *
 * Şifrə yalnız mühit dəyişənindən oxunur — əmr sətrində (və shell tarixçəsində) görünməsin deyə. İlk girişdə TOTP
 * qurulması məcburidir.
 */
@Component
public class PlatformAdminCommandLine implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminCommandLine.class);

    private final AdminRealms realms;
    private final AdminAuthService auth;
    private final PasswordHasher hasher;
    private final Environment environment;
    private final Clock clock;

    public PlatformAdminCommandLine(AdminRealms realms, AdminAuthService auth, PasswordHasher hasher,
                                    Environment environment, Clock clock) {
        this.realms = realms;
        this.auth = auth;
        this.hasher = hasher;
        this.environment = environment;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> emails = args.getOptionValues("create-platform-admin");
        if (emails == null || emails.isEmpty()) {
            return;
        }
        String email = AdminAccountStore.normalizeEmail(emails.getFirst());
        String password = environment.getProperty("PLATFORM_ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("PLATFORM_ADMIN_PASSWORD environment variable is required");
        }
        auth.checkPolicy(password, email);
        AdminAccountStore accounts = realms.accounts(AdminRealm.PLATFORM);
        if (accounts.findByEmail(email).isPresent()) {
            log.info("Platforma admini artıq mövcuddur: {}", EmailMasker.mask(email));
            return;
        }
        List<String> names = args.getOptionValues("display-name");
        String displayName = names == null || names.isEmpty() ? null : names.getFirst();
        var id = accounts.insert(email, hasher.hash(password), displayName, Language.DEFAULT,
                AdminAccount.AccountStatus.ACTIVE, clock.instant());
        realms.audit(AdminRealm.PLATFORM, "PLATFORM_ADMIN_CREATED", null, "account", id, Map.of("source", "CLI"));
        log.info("Platforma admini yaradıldı: {}", EmailMasker.mask(email));
    }
}
