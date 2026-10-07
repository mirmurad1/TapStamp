package az.cafeloyalty.customer.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import az.cafeloyalty.customer.account.CustomerAccountService;
import az.cafeloyalty.customer.auth.CustomerCookies;
import az.cafeloyalty.customer.auth.CustomerPrincipal;
import az.cafeloyalty.customer.auth.CustomerSessionService;
import az.cafeloyalty.customer.consent.LegalDocumentService.DocumentType;
import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.shared.i18n.LocalizedText;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Daxil olmuş müştərinin profili, möhür kartı, cihazları və hesabın silinməsi. */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Müştəri: profil")
public class CustomerMeController {

    private final CustomerAccountService accounts;
    private final CustomerSessionService sessions;
    private final CustomerCookies cookies;

    public CustomerMeController(CustomerAccountService accounts, CustomerSessionService sessions,
                                CustomerCookies cookies) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.cookies = cookies;
    }

    public record CardResponse(int currentStamps, int totalStamps, int stampsRequired, LocalizedText rewardName,
                               LocalizedText rewardDescription) {
    }

    public record RewardResponse(UUID id, LocalizedText name, Instant earnedAt) {
    }

    public record PendingConsentResponse(DocumentType type, int version) {
    }

    @Schema(description = "Müştərinin profili. Telefon yalnız maskalı göstərilir.")
    public record CustomerMeResponse(UUID id, String name, String phoneMasked, String preferredLanguage, Instant registeredAt,
                             CardResponse card, List<RewardResponse> availableRewards,
                             @Schema(description = "Razılıq verilməli sənədlər (yeni versiya dərc olunubsa)")
                             List<PendingConsentResponse> pendingConsents) {
    }

    public record UpdateRequest(
            @Schema(description = "Ad; boş sətir adı silir, göndərilməzsə dəyişmir") @Size(max = 80) String name,
            @Pattern(regexp = "az|en|ru") String preferredLanguage) {
    }

    public record DeviceResponse(UUID id, String deviceLabel, Instant createdAt, Instant lastSeenAt, boolean current) {
    }

    public record RevokedResponse(int revoked) {
    }

    @GetMapping
    @Operation(summary = "Profil və möhür kartı", description = "Kart, mövcud mükafatlar və razılıq gözləyən sənədlər.")
    CustomerMeResponse me(@AuthenticationPrincipal CustomerPrincipal principal) {
        CustomerAccountService.Profile profile = accounts.profile(principal);
        CustomerAccountService.Card card = profile.card();
        return new CustomerMeResponse(profile.id(), profile.name(), profile.phoneMasked(), profile.language().tag(),
                profile.registeredAt(),
                card == null ? null : new CardResponse(card.currentStamps(), card.totalStamps(), card.stampsRequired(),
                        card.rewardName(), card.rewardDescription()),
                profile.availableRewards().stream()
                        .map(reward -> new RewardResponse(reward.id(), reward.name(), reward.earnedAt())).toList(),
                profile.pendingConsents().stream()
                        .map(ref -> new PendingConsentResponse(ref.type(), ref.version())).toList());
    }

    @PatchMapping
    @Operation(summary = "Ad və dilin dəyişdirilməsi", description = "Dil SMS-lərə və bütün cihazlara tətbiq olunur.")
    CustomerMeResponse update(@Valid @RequestBody UpdateRequest request, @AuthenticationPrincipal CustomerPrincipal principal) {
        Language language = request.preferredLanguage() == null ? null
                : Language.fromTag(request.preferredLanguage()).orElse(null);
        accounts.update(principal, request.name(), language);
        return me(new CustomerPrincipal(principal.customerId(), principal.sessionId(),
                language == null ? principal.language() : language, principal.stepUpAt()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Hesabın silinməsi", description = """
            Əvvəlcə SMS kodu ilə təkrar təsdiq lazımdır (`/api/v1/auth/step-up/otp/*`), əks halda `STEP_UP_REQUIRED`. \
            Şəxsi məlumat silinir, bütün sessiyalar bağlanır; anonim statistika qalır.""")
    void delete(@AuthenticationPrincipal CustomerPrincipal principal, HttpServletResponse response) {
        accounts.delete(principal);
        cookies.clear(response);
    }

    @GetMapping("/sessions")
    @Operation(summary = "Aktiv cihazlar")
    List<DeviceResponse> sessions(@AuthenticationPrincipal CustomerPrincipal principal) {
        return sessions.list(principal).stream()
                .map(session -> new DeviceResponse(session.id(), session.deviceLabel(), session.createdAt(),
                        session.lastSeenAt(), session.id().equals(principal.sessionId())))
                .toList();
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cihazdan çıxış")
    void revoke(@PathVariable UUID sessionId, @AuthenticationPrincipal CustomerPrincipal principal) {
        sessions.revoke(principal, sessionId);
    }

    @PostMapping("/sessions/revoke-others")
    @Operation(summary = "Digər bütün cihazlardan çıxış")
    RevokedResponse revokeOthers(@AuthenticationPrincipal CustomerPrincipal principal) {
        return new RevokedResponse(sessions.revokeOthers(principal));
    }
}
