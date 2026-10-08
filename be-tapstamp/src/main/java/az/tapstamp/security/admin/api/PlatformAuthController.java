package az.tapstamp.security.admin.api;

import az.tapstamp.security.admin.AdminAuthService;
import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.security.admin.AdminSessionCookies;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platforma adminlərinin girişi — yalnız platformanın domenində işləyir. */
@RestController
@RequestMapping("/api/v1/platform/auth")
@Tag(name = "Platforma: autentifikasiya")
public class PlatformAuthController extends AdminAuthEndpoints {

    public PlatformAuthController(AdminAuthService auth, AdminSessionCookies cookies) {
        super(auth, cookies);
    }

    @Override
    protected AdminRealm realm() {
        return AdminRealm.PLATFORM;
    }
}
