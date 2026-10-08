package az.tapstamp.tenancy.provisioning;

import java.util.List;

import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.i18n.LocalizedText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Prod-da yeni şirkəti işləyən tətbiqdən ayrı, birdəfəlik əmrlə yaratmaq üçün:
 *
 * <pre>
 * java -jar app.jar --spring.main.web-application-type=none \
 *   --provision-tenant=mockcoffee --domain=bonus.mockcoffee.az \
 *   --name-az="Mock Coffee House" [--name-en=...] [--name-ru=...] [--default-language=az] [--timezone=Asia/Baku]
 * </pre>
 */
@Component
public class ProvisioningCommandLine implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningCommandLine.class);

    private final TenantProvisioner provisioner;

    public ProvisioningCommandLine(TenantProvisioner provisioner) {
        this.provisioner = provisioner;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("provision-tenant")) {
            return;
        }
        ProvisionTenantCommand command = new ProvisionTenantCommand(
                single(args, "provision-tenant"),
                LocalizedText.of(single(args, "name-az"), optional(args, "name-en"), optional(args, "name-ru")),
                single(args, "domain"),
                Language.fromTag(optional(args, "default-language")).orElse(Language.DEFAULT),
                optionalOr(args, "timezone", "Asia/Baku"));
        var tenant = provisioner.provision(command);
        log.info("Tenant hazırdır: slug={}, baza={}", tenant.slug(), tenant.dbName());
    }

    private static String single(ApplicationArguments args, String name) {
        String value = optional(args, name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("--" + name + " is required");
        }
        return value;
    }

    private static String optional(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static String optionalOr(ApplicationArguments args, String name, String fallback) {
        String value = optional(args, name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
