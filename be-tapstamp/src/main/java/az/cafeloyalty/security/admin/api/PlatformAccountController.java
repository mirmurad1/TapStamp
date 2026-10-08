package az.cafeloyalty.security.admin.api;

import az.cafeloyalty.config.AppProperties;
import az.cafeloyalty.security.admin.AdminAuthService;
import az.cafeloyalty.security.admin.AdminRealm;
import az.cafeloyalty.security.admin.AdminRealms;
import az.cafeloyalty.security.admin.AdminSessionCookies;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platforma admininin öz hesabı. */
@RestController
@RequestMapping("/api/v1/platform")
@Tag(name = "Platforma: hesab")
public class PlatformAccountController extends AdminAccountEndpoints {

    public PlatformAccountController(AdminAuthService auth, AdminRealms realms, AdminSessionCookies cookies,
                                     AppProperties appProperties) {
        super(auth, realms, cookies, appProperties);
    }

    @Override
    protected AdminRealm realm() {
        return AdminRealm.PLATFORM;
    }
}
