package az.cafeloyalty.nfc.web;

import java.net.URI;
import java.util.regex.Pattern;

import az.cafeloyalty.nfc.BrowserIdCookie;
import az.cafeloyalty.nfc.TapVerificationService;
import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import az.cafeloyalty.shared.web.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * NFC çipinin yazdığı link: {@code GET /t/{uid}?picc=…&cmac=…}. Bu, brauzer naviqasiyasıdır — cavab həmişə
 * {@code 303} yönləndirmədir: uğurda {@code /tap/{token}}, xətada {@code /tap/error?code=…}. İmzalı parametrlər
 * növbəti səhifənin ünvanında və Referer başlığında qalmır ({@code no-referrer}, {@code no-store}).
 */
@RestController
@Tag(name = "Toxunuş (NFC)")
public class TapController {

    /** Link önizləməsi yaradan botlar (mesajlaşma tətbiqləri) sayğacı istehlak etməməlidir. */
    private static final Pattern PREVIEW_BOTS = Pattern.compile(
            "(?i)facebookexternalhit|facebot|twitterbot|slackbot|slack-imgproxy|telegrambot|whatsapp|discordbot|"
                    + "linkedinbot|skypeuripreview|googlebot|bingbot|applebot|redditbot|pinterest|vkshare|viber|"
                    + "snapchat|embedly|iframely|yandexbot");

    private static final Logger log = LoggerFactory.getLogger(TapController.class);

    private final TapVerificationService verification;
    private final BrowserIdCookie browserIds;

    public TapController(TapVerificationService verification, BrowserIdCookie browserIds) {
        this.verification = verification;
        this.browserIds = browserIds;
    }

    @GetMapping("/t/{uid}")
    @Operation(summary = "NFC toxunuşu", description = """
            Çipin imzasını yoxlayır və `303 /tap/{token}` (60 saniyəlik birdəfəlik token, brauzerə bağlı) və ya \
            `303 /tap/error?code=` cavabı verir. HEAD sorğuları və link-preview botları sayğacı istehlak etmir.""")
    ResponseEntity<Void> tap(@PathVariable String uid, @RequestParam(required = false) String picc,
                             @RequestParam(required = false) String cmac, HttpServletRequest request,
                             HttpServletResponse response) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if ("HEAD".equals(request.getMethod()) || (userAgent != null && PREVIEW_BOTS.matcher(userAgent).find())) {
            return redirect("/");
        }
        String browserId = browserIds.ensure(request, response);
        try {
            TapVerificationService.Accepted accepted =
                    verification.verify(uid, picc, cmac, ClientInfo.of(request), browserId);
            return redirect("/tap/" + accepted.token());
        } catch (ApiException e) {
            return redirect("/tap/error?code=" + e.code().name());
        } catch (RuntimeException e) {
            log.error("Tap verification failed unexpectedly", e);
            return redirect("/tap/error?code=" + ErrorCode.INTERNAL_ERROR.name());
        }
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create(location))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }
}
