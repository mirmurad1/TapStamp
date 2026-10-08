package az.tapstamp.dev.web;

import java.util.List;
import java.util.UUID;

import az.tapstamp.dev.simulator.DevSimulatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** NFC simulyatoru — yalnız dev profilində və yalnız daxil olmuş işçi üçün (cari şirkətin çipləri). */
@RestController
@RequestMapping("/api/v1/dev/simulator")
@Profile("dev")
@Tag(name = "Dev alətləri")
public class DevSimulatorController {

    private final DevSimulatorService simulator;

    public DevSimulatorController(DevSimulatorService simulator) {
        this.simulator = simulator;
    }

    public record SimulatorTapRequest(@NotNull UUID tagId) {
    }

    @GetMapping("/tags")
    @Operation(summary = "Simulyasiya üçün çiplər (yalnız dev)")
    List<DevSimulatorService.SimulatedTag> tags() {
        return simulator.tags();
    }

    @PostMapping("/taps")
    @Operation(summary = "İmzalı toxunuş linki (yalnız dev)", description = """
            Çipin növbəti oxunuşunu təqlid edir: sayğac artır, link imzalanır. NTAG424_SUN çipləri real \
            kriptoqrafiya ilə emulyasiya olunur. Nisbi yol qaytarılır (cari domendə açılır).""")
    DevSimulatorService.SimulatedTap tap(@Valid @RequestBody SimulatorTapRequest request) {
        return simulator.tap(request.tagId());
    }
}
