package az.cafeloyalty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tətbiqə xas konfiqurasiya ({@code app.*}). Sirr dəyərləri burada saxlanılmır — onlar yalnız env-dən gəlir.
 *
 * @param platformDomain platforma panelinin domeni (məs. {@code platform.localhost}); tenant domeni deyil
 */
@ConfigurationProperties("app")
public record AppProperties(
        @DefaultValue("az") String defaultLanguage,
        @DefaultValue("platform.localhost") String platformDomain,
        @DefaultValue Security security,
        @DefaultValue Notifications notifications,
        @DefaultValue Dev dev) {

    /**
     * @param cookieSecure cookie-lərdə {@code Secure} atributu; prod-da söndürülə bilməz
     * @param hstsEnabled  HSTS başlığı; dev-də *.localhost domenlərini pozmamaq üçün söndürülür
     */
    public record Security(
            @DefaultValue("true") boolean cookieSecure,
            @DefaultValue("true") boolean hstsEnabled) {
    }

    /** SMS və email provayderlərinin adı: {@code console} yalnız dev üçündür. */
    public record Notifications(
            @DefaultValue("console") String smsProvider,
            @DefaultValue("console") String mailProvider) {
    }

    /**
     * Yalnız dev mühitində işləyən imkanlar; prod-da aktiv olarsa tətbiq başlamır.
     *
     * @param seedAdminPassword dev test hesablarının şifrəsi (yalnız {@code application-dev.yml}-də təyin olunur)
     * @param simulatorKey      simulyasiya olunan çiplərin HMAC açarı (base64, yalnız dev)
     */
    public record Dev(
            @DefaultValue("false") boolean simulatorEnabled,
            @DefaultValue("false") boolean seedEnabled,
            String seedAdminPassword,
            String simulatorKey) {

        @Override
        public String toString() {
            return "Dev[simulatorEnabled=" + simulatorEnabled + ", seedEnabled=" + seedEnabled + "]";
        }
    }
}
