package az.tapstamp.site.api;

import az.tapstamp.shared.web.ClientInfo;
import az.tapstamp.site.service.DemoRequestService;
import az.tapstamp.site.service.DemoRequestService.DemoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Şirkət saytı (trendsolutions.az): TapStamp demo müraciəti. Yalnız sayt domenində açıqdır. */
@RestController
@Tag(name = "Şirkət saytı", description = "trendsolutions.az saytının formaları")
public class SiteDemoRequestController {

    private final DemoRequestService demoRequests;

    public SiteDemoRequestController(DemoRequestService demoRequests) {
        this.demoRequests = demoRequests;
    }

    @PostMapping("/api/v1/site/demo-requests")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "TapStamp demo müraciəti",
            description = """
                    Müraciət saxlanılmır: məzmunu şirkətin poçt qutusuna (`app.site.inbox`) email kimi göndərilir. \
                    Autentifikasiya tələb etmir; IP və ümumi gündəlik limit var. `website` sahəsi botlar üçün \
                    tələdir (honeypot — insan görmədiyi gizli sahə): doludursa müraciət səssizcə atılır.""")
    void submit(@Valid @RequestBody DemoRequestBody body, HttpServletRequest request) {
        demoRequests.submit(new DemoRequest(body.name(), body.phone(), body.company(), body.businessType(),
                body.branches(), body.note(), body.language()), body.website(), ClientInfo.of(request));
    }

    record DemoRequestBody(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 30) @Pattern(regexp = "^[0-9+()\\-\\s]{5,30}$") String phone,
            @NotBlank @Size(max = 150) String company,
            @NotBlank @Pattern(regexp = "^(cafe|beauty|retail|other)$") String businessType,
            @NotBlank @Pattern(regexp = "^(1|2-5|6\\+)$") String branches,
            @Size(max = 1000) String note,
            @Pattern(regexp = "^(az|en|ru)$") String language,
            @Size(max = 200) String website) {
    }
}
