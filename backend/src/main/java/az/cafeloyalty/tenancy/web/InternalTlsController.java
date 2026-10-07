package az.cafeloyalty.tenancy.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import az.cafeloyalty.config.AppProperties;
import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sertifikat alınmalı domenlərin siyahısı — yalnız daxili certbot konteyneri üçün (bax: docker-compose.prod.yml).
 *
 * <p>İki qat qoruma: nginx {@code /internal} yolunu xaricə heç vaxt açmır və endpoint {@code X-Internal-Token}
 * başlığında {@code APP_INTERNAL_TOKEN} sirrini tələb edir (sabit vaxtlı müqayisə). Sirr təyin olunmayıbsa, endpoint
 * ümumiyyətlə işləmir (404). Cavab — hər sətirdə bir domen (aktiv və dayandırılmış şirkətlər + platforma domeni).
 */
@Hidden
@RestController
public class InternalTlsController {

    private final JdbcClient platform;
    private final AppProperties properties;
    private final byte[] token;

    public InternalTlsController(@Qualifier("platformJdbc") JdbcClient platform, AppProperties properties,
                                 @Value("${app.internal-token:}") String token) {
        this.platform = platform;
        this.properties = properties;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/internal/tls/domains", produces = MediaType.TEXT_PLAIN_VALUE)
    String domains(@RequestHeader(name = "X-Internal-Token", required = false) String provided) {
        if (token.length == 0 || provided == null
                || !MessageDigest.isEqual(token, provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        List<String> domains = new ArrayList<>(platform.sql("""
                        SELECT d.hostname FROM tenant_domains d JOIN tenants t ON t.id = d.tenant_id
                        WHERE t.status IN ('ACTIVE', 'SUSPENDED') ORDER BY d.hostname""")
                .query(String.class)
                .list());
        domains.add(properties.platformDomain().toLowerCase(Locale.ROOT));
        return String.join("\n", domains.stream().distinct().toList()) + "\n";
    }
}
