package az.tapstamp.feedback;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import az.tapstamp.feedback.repository.FeedbackMapper;
import az.tapstamp.feedback.repository.FeedbackMapper.ReviewCard;
import az.tapstamp.feedback.repository.FeedbackMapper.ReviewTarget;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.tenancy.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Google rəyi üçün çap olunan QR kart (stend, platforma hazırlayır və satır). QR şirkətin domeninə aparır; buradan
 * filialın Google rəy səhifəsinə yönləndirilir. Link qoşulmayıbsa — şirkət və filial adı ilə Google Maps axtarışına,
 * ona görə çap olunmuş kart heç vaxt "ölü" qalmır; link sonradan dəyişsə də kartı yenidən çap etmək lazım deyil.
 */
@Service
public class ReviewLinkService {

    /** Platformanın göstərdiyi "son dövr" skanları. */
    public static final int RECENT_DAYS = 30;

    private final FeedbackMapper feedback;
    private final Clock clock;

    public ReviewLinkService(FeedbackMapper feedback, Clock clock) {
        this.feedback = feedback;
        this.clock = clock;
    }

    /**
     * QR skanının hədəfi (cari şirkət). {@code count} — skan sayılsın (HEAD sorğuları və link önizləmə botları
     * sayılmır). Filial tapılmırsa və ya bağlıdırsa {@code null} — çağıran tətbiqin ana səhifəsinə yönləndirir.
     */
    public @Nullable String target(UUID branchId, boolean count) {
        ReviewTarget target = feedback.reviewTarget(branchId).orElse(null);
        if (target == null) {
            return null;
        }
        if (count) {
            feedback.recordQrScan(branchId, LocalDate.now(clock.withZone(TenantContext.require().timezone())));
        }
        if (target.googleReviewUrl() != null) {
            return target.googleReviewUrl();
        }
        String query = target.companyName().az() + " " + place(target.branchName(), target.address());
        return "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(query.strip(), StandardCharsets.UTF_8);
    }

    /** Cari şirkətin aktiv filialları — platformanın çap ekranı üçün. */
    public List<ReviewCard> cards() {
        LocalDate today = LocalDate.now(clock.withZone(TenantContext.require().timezone()));
        return feedback.reviewCards(today.minusDays(RECENT_DAYS - 1L));
    }

    /** Axtarış üçün yer: ünvan varsa ünvan (daha dəqiqdir), yoxdursa filialın adı. */
    private static String place(LocalizedText branchName, @Nullable LocalizedText address) {
        return address != null && address.az() != null && !address.az().isBlank() ? address.az() : branchName.az();
    }
}
