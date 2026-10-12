package az.tapstamp.tenancy.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import az.tapstamp.config.AppProperties;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.tenancy.repository.TenantRegistryMapper;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sertifikat alınmalı domenlərin siyahısı — yalnız daxili certbot konteyneri üçün (bax: docker-compose.prod.yml).
 *
 * <p>İki qat qoruma: nginx {@code /internal} yolunu xaricə heç vaxt açmır və endpoint {@code X-Internal-Token}
 * başlığında {@code APP_INTERNAL_TOKEN} sirrini tələb edir (sabit vaxtlı müqayisə). Sirr təyin olunmayıbsa, endpoint
 * ümumiyyətlə işləmir (404). Cavab — hər sətirdə bir domen (aktiv və dayandırılmış şirkətlər + platforma domeni).
 *
 * <p>Certbot hər dövrün sonunda sertifikatların vəziyyətini geri göndərir ({@code POST /internal/tls/status}): platforma
 * panelində domenin yanında "Sertifikat var" / "Sertifikat xətası" görünür.
 */
@Hidden
@RestController
public class InternalTlsController {

    private static final Set<String> STATUSES = Set.of("ISSUED", "FAILED");
    private static final Pattern HOSTNAME = Pattern.compile("^[A-Za-z0-9.-]{1,253}$");

    private final TenantRegistryMapper registry;
    private final AppProperties properties;
    private final byte[] token;

    public InternalTlsController(TenantRegistryMapper registry, AppProperties properties,
                                 @Value("${app.internal-token:}") String token) {
        this.registry = registry;
        this.properties = properties;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/internal/tls/domains", produces = MediaType.TEXT_PLAIN_VALUE)
    String domains(@RequestHeader(name = "X-Internal-Token", required = false) String provided) {
        requireToken(provided);
        List<String> domains = new ArrayList<>(registry.findCertificateHostnames());
        domains.add(properties.platformDomain().toLowerCase(Locale.ROOT));
        return String.join("\n", domains.stream().distinct().toList()) + "\n";
    }

    /** Hər sətir: {@code <domen> ISSUED|FAILED}. Tanınmayan sətirlər nəzərə alınmır; reyestrdə olmayan domen dəyişmir. */
    @PostMapping(value = "/internal/tls/status", consumes = MediaType.TEXT_PLAIN_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void status(@RequestHeader(name = "X-Internal-Token", required = false) String provided,
                @RequestBody(required = false) String report) {
        requireToken(provided);
        if (report == null) {
            return;
        }
        for (String line : report.split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 2 && STATUSES.contains(parts[1]) && HOSTNAME.matcher(parts[0]).matches()) {
                registry.updateTlsStatus(parts[0].toLowerCase(Locale.ROOT), parts[1]);
            }
        }
    }

    private void requireToken(String provided) {
        if (token.length == 0 || provided == null
                || !MessageDigest.isEqual(token, provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }
}
