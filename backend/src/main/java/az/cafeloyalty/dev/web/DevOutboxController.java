package az.cafeloyalty.dev.web;

import java.util.List;

import az.cafeloyalty.dev.outbox.DevOutbox;
import az.cafeloyalty.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dev outbox — yalnız dev profilində. Cari domenin (şirkət və ya platforma) mesajlarını göstərir. */
@RestController
@RequestMapping("/api/v1/dev/outbox")
@Profile("dev")
@Tag(name = "Dev alətləri")
public class DevOutboxController {

    private final DevOutbox outbox;

    public DevOutboxController(DevOutbox outbox) {
        this.outbox = outbox;
    }

    @GetMapping
    @Operation(summary = "Göndərilmiş email/SMS-lər (yalnız dev)",
            description = "Real provayder əvəzinə konsol provayderinin \"göndərdiyi\" son mesajlar, ən yenisi birinci.")
    List<DevOutbox.Entry> list(@RequestParam(required = false) String channel) {
        String tenantId = TenantContext.current().map(tenant -> tenant.id().toString()).orElse(null);
        return outbox.list(tenantId, channel);
    }
}
