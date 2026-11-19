package az.tapstamp.feedback.web;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

import az.tapstamp.feedback.ReviewLinkService;
import az.tapstamp.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Çap olunmuş QR kartın ünvanı: skanı sayır və filialın Google rəy səhifəsinə yönləndirir. */
@RestController
@Tag(name = "İctimai: Google rəyi")
public class ReviewRedirectController {

    /** Link önizləməsi (WhatsApp, Telegram və s.) skan sayılmır. */
    private static final Pattern PREVIEW_BOTS = Pattern.compile(
            "(?i)facebookexternalhit|whatsapp|telegrambot|twitterbot|slackbot|discordbot|linkedinbot|skypeuripreview|bot\\b");

    private final ReviewLinkService reviews;

    public ReviewRedirectController(ReviewLinkService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/api/v1/public/review/{branchId}")
    @Operation(summary = "QR kartdan Google rəyinə keçid", description = """
            Filialın Google rəy səhifəsinə `302` (link qoşulmayıbsa — şirkət və filial adı ilə Google Maps \
            axtarışına). Skan gün üzrə sayılır (HEAD və önizləmə botları sayılmır). Filial tapılmırsa — tətbiqin ana \
            səhifəsinə.""")
    ResponseEntity<Void> review(@PathVariable UUID branchId, HttpServletRequest request) {
        TenantContext.require();
        String agent = request.getHeader(HttpHeaders.USER_AGENT);
        boolean count = !"HEAD".equals(request.getMethod()) && (agent == null || !PREVIEW_BOTS.matcher(agent).find());
        String target = reviews.target(branchId, count);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target == null ? "/" : target))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
