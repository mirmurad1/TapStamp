package az.tapstamp.security.admin;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Admin autentifikasiyasının parametrləri.
 *
 * @param sessionAbsolute       sessiyanın mütləq ömrü (fəaliyyətdən asılı olmayaraq)
 * @param sessionIdle           fəaliyyətsizlik limiti (fon sorğuları bunu uzatmır)
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
        @DefaultValue("10m") Duration pendingMfaTtl,
        @DefaultValue("10m") Duration stepUpValidity,
        @DefaultValue("5") int lockoutThreshold,
        @DefaultValue("60m") Duration maxLockout,
        @DefaultValue("5") int maxMfaFailures,
        @DefaultValue("30m") Duration passwordResetTtl,
        @DefaultValue("72h") Duration invitationTtl,
        @DefaultValue("443") int linksHttpsPort) {
}
