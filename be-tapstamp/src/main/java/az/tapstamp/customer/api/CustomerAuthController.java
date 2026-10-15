package az.tapstamp.customer.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.customer.auth.CustomerCookies;
import az.tapstamp.customer.auth.CustomerPrincipal;
import az.tapstamp.customer.auth.CustomerSessionService;
import az.tapstamp.customer.auth.OtpService;
import az.tapstamp.customer.auth.RegistrationService;
import az.tapstamp.customer.consent.LegalDocumentService;
import az.tapstamp.customer.consent.LegalDocumentService.DocumentRef;
import az.tapstamp.customer.consent.LegalDocumentService.DocumentType;
import az.tapstamp.nfc.BrowserIdCookie;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.web.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Müştərinin girişi və qeydiyyatı: telefon + SMS kodu. Yalnız şirkətin domenində. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Müştəri: autentifikasiya")
public class CustomerAuthController {

    private final OtpService otp;
    private final RegistrationService registration;
    private final CustomerSessionService sessions;
    private final CustomerCookies cookies;
    private final LegalDocumentService legal;
    private final AuditLogger audit;
    private final BrowserIdCookie browserIds;

    public CustomerAuthController(OtpService otp, RegistrationService registration, CustomerSessionService sessions,
                                  CustomerCookies cookies, LegalDocumentService legal, AuditLogger audit,
                                  BrowserIdCookie browserIds) {
        this.otp = otp;
        this.registration = registration;
        this.sessions = sessions;
        this.cookies = cookies;
        this.legal = legal;
        this.audit = audit;
        this.browserIds = browserIds;
    }

    public record OtpRequest(
            @Schema(description = "Telefon nömrəsi istənilən formatda: 050 123 45 67, +994501234567 və s.")
            @NotBlank @Size(max = 32) String phone,
            @Schema(description = "SMS-in dili") @NotBlank @Pattern(regexp = "az|en|ru") String language,
            @Schema(description = "Toxunuşdan sonra giriş: toxunuş tokeni 10 dəqiqəlik bu sorğuya rezerv olunur")
            @Size(max = 128) String tapToken) {
        @Override
        public @NonNull String toString() {
            return "OtpRequest[***]";
        }
    }

    @Schema(description = "Kod göndərildi. Cavab nömrənin qeydiyyatda olub-olmamasından asılı deyil.")
    public record OtpChallengeResponse(UUID challengeId, Instant expiresAt, Instant resendAvailableAt) {
    }

    public record OtpVerifyRequest(@NotNull UUID challengeId, @NotBlank @Size(max = 12) String code) {
        @Override
        public @NonNull String toString() {
            return "OtpVerifyRequest[challengeId=" + challengeId + "]";
        }
    }

    @Schema(description = """
            AUTHENTICATED — mövcud müştəri, sessiya cookie-ləri qoyulub; REGISTRATION_REQUIRED — yeni nömrə, \
            registrationToken ilə 10 dəqiqə ərzində qeydiyyatı tamamlamaq lazımdır.""")
    public record OtpVerifyResponse(
            @Schema(allowableValues = {"AUTHENTICATED", "REGISTRATION_REQUIRED"}) String status,
            String registrationToken,
            Instant registrationExpiresAt) {
        @Override
        public @NonNull String toString() {
            return "OtpVerifyResponse[status=" + status + "]";
        }
    }

    public record DocumentAcceptance(@NotNull DocumentType type, @NotNull Integer version) {
        DocumentRef toRef() {
            return new DocumentRef(type, version);
        }
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 128) String registrationToken,
            @Size(max = 80) String name,
            @NotNull @Size(max = 10) List<@Valid @NotNull DocumentAcceptance> acceptedDocuments,
            @NotBlank @Pattern(regexp = "az|en|ru") String language) {
        @Override
        public @NonNull String toString() {
            return "RegisterRequest[***]";
        }
    }

    public record ConsentRequest(@NotNull @Size(max = 10) List<@Valid @NotNull DocumentAcceptance> acceptedDocuments) {
    }

    public record AuthStatusResponse(@Schema(allowableValues = {"AUTHENTICATED"}) String status) {
    }

    @Schema(description = "ACTIVE — access token etibarlıdır; REFRESH_REQUIRED — yalnız refresh token var, "
            + "əvvəlcə /refresh çağırılmalıdır; NONE — daxil olmayıb")
    public record CustomerSessionStateResponse(@Schema(allowableValues = {"ACTIVE", "REFRESH_REQUIRED", "NONE"}) String state) {
    }

    @GetMapping("/session")
    @Operation(summary = "Sessiyanın vəziyyəti", description = """
            Səhifə açılanda frontend bununla bilir: müştəri daxil olubmu, yoxsa əvvəlcə tokeni yeniləmək lazımdır. \
            Qonaq üçün xəta qaytarılmır.""")
    CustomerSessionStateResponse session(@AuthenticationPrincipal CustomerPrincipal principal, HttpServletRequest http) {
        if (principal != null) {
            return new CustomerSessionStateResponse("ACTIVE");
        }
        return new CustomerSessionStateResponse(cookies.readRefresh(http).isPresent() ? "REFRESH_REQUIRED" : "NONE");
    }

    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "SMS kodunun göndərilməsi", description = """
            Kod 5 dəqiqə etibarlıdır, yenisi 60 saniyədən sonra istənilə bilər. Limitlər: nömrə üzrə 15 dəqiqədə 3 və \
            gündə 10, IP üzrə saatda 20. Yalnız mobil nömrələr və şirkətin icazə verdiyi ölkələr qəbul olunur.""")
    OtpChallengeResponse requestOtp(@Valid @RequestBody OtpRequest request, HttpServletRequest http) {
        Language language = Language.fromTag(request.language()).orElse(Language.DEFAULT);
        OtpService.Issued issued = otp.requestLogin(request.phone(), language, ClientInfo.of(http), request.tapToken(),
                browserIds.read(http).orElse(null));
        return new OtpChallengeResponse(issued.challengeId(), issued.expiresAt(), issued.resendAvailableAt());
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "SMS kodunun yoxlanılması", description = """
            Bir kod üçün 5 cəhd; yanlış kodda cavabda `attemptsRemaining` var. Mövcud müştəri dərhal daxil olur \
            (HttpOnly cookie-lər), yeni nömrə üçün qeydiyyat tokeni qaytarılır.""")
    OtpVerifyResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request, HttpServletRequest http,
                                HttpServletResponse response) {
        ClientInfo client = ClientInfo.of(http);
        OtpService.LoginResult result = otp.verifyLogin(request.challengeId(), request.code(), client);
        return switch (result) {
            case OtpService.LoginResult.Existing existing -> {
                cookies.write(response, sessions.open(existing.customerId(), client));
                audit.tenant("CUSTOMER_LOGIN", AuditLogger.ActorType.CUSTOMER, existing.customerId(), "customer",
                        existing.customerId(), Map.of());
                yield new OtpVerifyResponse("AUTHENTICATED", null, null);
            }
            case OtpService.LoginResult.RegistrationRequired required ->
                    new OtpVerifyResponse("REGISTRATION_REQUIRED", required.registrationToken(), required.expiresAt());
        };
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Qeydiyyatın tamamlanması", description = """
            Ad istəyə bağlıdır. Məxfilik siyasəti və istifadə şərtlərinin cari versiyalarına razılıq məcburidir \
            (`CONSENT_REQUIRED`). Razılığın dili, vaxtı, IP-si və brauzeri qeyd olunur.""")
    AuthStatusResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http,
                                HttpServletResponse response) {
        ClientInfo client = ClientInfo.of(http);
        Language language = Language.fromTag(request.language()).orElse(Language.DEFAULT);
        UUID customerId = registration.register(request.registrationToken(), request.name(),
                request.acceptedDocuments().stream().map(DocumentAcceptance::toRef).toList(), language, client);
        cookies.write(response, sessions.open(customerId, client));
        return new AuthStatusResponse("AUTHENTICATED");
    }

    @PostMapping("/consents")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sənədlərin yeni versiyasına razılıq")
    void consent(@Valid @RequestBody ConsentRequest request, @AuthenticationPrincipal CustomerPrincipal principal,
                 HttpServletRequest http) {
        legal.accept(principal.customerId(), request.acceptedDocuments().stream().map(DocumentAcceptance::toRef).toList(),
                principal.language(), ClientInfo.of(http));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Sessiyanın yenilənməsi", description = """
            Yeni access cookie-si qoyulur. Refresh token dəyişmir, müddəti yenidən 180 gün olur (cookie də yenidən \
            yazılır). Sessiya müştəri çıxana, ləğv olunana və ya 180 gün istifadə olunmayana qədər qalır.""")
    AuthStatusResponse refresh(HttpServletRequest http, HttpServletResponse response) {
        String token = cookies.readRefresh(http).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        try {
            cookies.write(response, sessions.refresh(token, ClientInfo.of(http)));
        } catch (ApiException e) {
            if (e.code() == ErrorCode.SESSION_EXPIRED) {
                cookies.clear(response);
            }
            throw e;
        }
        return new AuthStatusResponse("AUTHENTICATED");
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Çıxış", description = "Cari sessiya ləğv olunur, cookie-lər silinir. Sessiya yoxdursa da 204.")
    void logout(HttpServletRequest http, HttpServletResponse response) {
        sessions.logout(cookies.readAccess(http), cookies.readRefresh(http));
        cookies.clear(response);
    }

    @PostMapping("/step-up/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Həssas əməliyyat üçün SMS kodu", description = "Hesabın silinməsindən əvvəl kimliyin təkrar təsdiqi.")
    OtpChallengeResponse requestStepUp(@AuthenticationPrincipal CustomerPrincipal principal, HttpServletRequest http) {
        OtpService.Issued issued = otp.requestStepUp(principal, ClientInfo.of(http));
        return new OtpChallengeResponse(issued.challengeId(), issued.expiresAt(), issued.resendAvailableAt());
    }

    @PostMapping("/step-up/otp/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Həssas əməliyyat üçün kodun yoxlanılması", description = "Təsdiq 10 dəqiqə etibarlıdır.")
    void verifyStepUp(@Valid @RequestBody OtpVerifyRequest request, @AuthenticationPrincipal CustomerPrincipal principal,
                      HttpServletRequest http) {
        otp.verifyStepUp(principal, request.challengeId(), request.code(), ClientInfo.of(http));
        sessions.markStepUp(principal);
    }
}
