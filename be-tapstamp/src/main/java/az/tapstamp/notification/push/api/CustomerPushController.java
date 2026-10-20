package az.tapstamp.notification.push.api;

import az.tapstamp.customer.auth.CustomerPrincipal;
import az.tapstamp.notification.push.PushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Web push (müştəri tətbiqi): açıq açar və cihazın abunəsi. */
@RestController
@Tag(name = "Müştəri: bildirişlər")
public class CustomerPushController {

    private final PushService push;

    public CustomerPushController(PushService push) {
        this.push = push;
    }

    public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
    }

    @Schema(description = "Brauzerin PushSubscription obyekti (toJSON): ünvan və açarlar")
    public record SubscriptionRequest(@NotBlank @Size(max = 1000) String endpoint, @NotNull @Valid Keys keys) {
    }

    public record EndpointRequest(@NotBlank @Size(max = 1000) String endpoint) {
    }

    @GetMapping("/api/v1/public/push")
    @Operation(summary = "Web push parametrləri", description = """
            `enabled` — serverdə push qurulubmu; `publicKey` — brauzerin abunəsi üçün VAPID açıq açarı (base64url).""")
    PushService.Config config() {
        return push.config();
    }

    @PostMapping("/api/v1/me/push-subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Bu cihazda bildirişləri açmaq", description = """
            Ünvan yalnız tanınmış push xidmətinə (Google, Apple, Mozilla, Microsoft) ola bilər. Eyni ünvan təkrar \
            göndəriləndə yenilənir; müştərinin ən çox 10 cihazı saxlanılır. Push qurulmayıbsa `PUSH_UNAVAILABLE`.""")
    void subscribe(@AuthenticationPrincipal CustomerPrincipal principal, @Valid @RequestBody SubscriptionRequest request,
                   HttpServletRequest http) {
        push.subscribe(principal.customerId(), request.endpoint(), request.keys().p256dh(), request.keys().auth(),
                http.getHeader(HttpHeaders.USER_AGENT));
    }

    @PostMapping("/api/v1/me/push-subscriptions/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Bu cihazda bildirişləri söndürmək")
    void unsubscribe(@AuthenticationPrincipal CustomerPrincipal principal, @Valid @RequestBody EndpointRequest request) {
        push.unsubscribe(principal.customerId(), request.endpoint());
    }
}
