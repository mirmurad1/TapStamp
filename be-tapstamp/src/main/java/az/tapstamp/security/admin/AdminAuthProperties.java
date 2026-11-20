package az.tapstamp.security.admin;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Admin autentifikasiyasının parametrləri.
 *
 * @param sessionAbsolute       platforma sessiyasının mütləq ömrü (fəaliyyətdən asılı olmayaraq)
 * @param sessionIdle           platforma sessiyasının fəaliyyətsizlik limiti (fon sorğuları bunu uzatmır)
 * @param staffSessionAbsolute  şirkət admin panelinin sessiyasının mütləq ömrü
 * @param staffSessionIdle      şirkət admin panelinin fəaliyyətsizlik limiti (1 gün heç nə edilməsə çıxış)
 * @param pendingMfaTtl         şifrədən sonra TOTP üçün verilən müddət
 * @param stepUpValidity        həssas əməliyyat üçün son TOTP təsdiqinin etibarlı olduğu müddət
 * @param lockoutThreshold      bu qədər ardıcıl uğursuz cəhddən sonra hesab müvəqqəti bloklanır
 * @param maxLockout            artan bloklanma müddətinin yuxarı həddi
 * @param maxMfaFailures        bir sessiyada icazə verilən uğursuz TOTP cəhdi
 * @param passwordResetTtl      şifrə bərpası linkinin ömrü
 * @param invitationTtl         dəvət linkinin ömrü
 * @param linksHttpsPort        emaildəki linklər üçün HTTPS portu (443 olarsa göstərilmir)
 */
@ConfigurationProperties("app.admin-auth")
public record AdminAuthProperties(
        @DefaultValue("8h") Duration sessionAbsolute,
        @DefaultValue("30m") Duration sessionIdle,
        @DefaultValue("30d") Duration staffSessionAbsolute,
        @DefaultValue("24h") Duration staffSessionIdle,
        @DefaultValue("10m") Duration pendingMfaTtl,
        @DefaultValue("10m") Duration stepUpValidity,
        @DefaultValue("5") int lockoutThreshold,
        @DefaultValue("60m") Duration maxLockout,
        @DefaultValue("5") int maxMfaFailures,
        @DefaultValue("30m") Duration passwordResetTtl,
        @DefaultValue("72h") Duration invitationTtl,
        @DefaultValue("443") int linksHttpsPort) {

    /** Realm üzrə mütləq ömür: şirkət paneli uzun, platforma qısa. */
    public Duration sessionAbsolute(AdminRealm realm) {
        return realm == AdminRealm.STAFF ? staffSessionAbsolute : sessionAbsolute;
    }

    /** Realm üzrə fəaliyyətsizlik limiti. */
    public Duration sessionIdle(AdminRealm realm) {
        return realm == AdminRealm.STAFF ? staffSessionIdle : sessionIdle;
    }
}
