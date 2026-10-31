package az.tapstamp.company.web;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import az.tapstamp.company.api.PublicCompanyResponse;
import az.tapstamp.company.service.CompanyLogoService;
import az.tapstamp.company.service.CompanyQueryService;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.tenancy.TenantContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * PWA (telefona quraşdırılan veb tətbiq): hər şirkətin öz manifesti və ikonları. Ad, rəng və ikon domenə görə
 * (tenant) seçilir — {@code moyka.mockcoffe.com}-dan quraşdırılan tətbiq telefonda "Moyka" adı və öz rəngi ilə görünür.
 *
 * <p>İkonlar şrift tələb etmədən çəkilir (brend rəngində fon, ağ halqa və ulduz — möhür izi kimi), ona görə
 * minimal Docker image-də də işləyir. Şirkət loqo yükləyibsə, ikon loqodan hazırlanır (ana ekrana əlavə edəndə də
 * loqo görünür). Şirkət, rəng və loqonun versiyası üzrə yaddaşda keşlənir.
 */
@RestController
@Tag(name = "İctimai: PWA")
public class PwaController {

    /** İcazə verilən ikon ölçüləri: iPhone (180), Android (192, 512). */
    private static final List<Integer> SIZES = List.of(180, 192, 512);
    private static final CacheControl ICON_CACHE = CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

    private final CompanyQueryService company;
    private final CompanyLogoService logos;
    private final Cache<String, byte[]> icons = Caffeine.newBuilder().maximumSize(500).build();

    public PwaController(CompanyQueryService company, CompanyLogoService logos) {
        this.company = company;
        this.logos = logos;
    }

    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
    @Operation(summary = "Şirkətin PWA manifesti", description = "Ad şirkətin standart dilində, rəng — brend rəngi.")
    ResponseEntity<Map<String, Object>> manifest() {
        return manifest("/", "portrait");
    }

    /**
     * İdarəetmə panelinin manifesti: eyni ad və ikon (şirkətin loqosu), amma ana ekrandan {@code /admin/} açılır və
     * müştəri tətbiqindən ayrı tətbiq kimi quraşdırılır. Planşetdə üfüqi rejim də lazımdır — istiqamət sərbəstdir.
     */
    @GetMapping(value = "/api/v1/public/admin.webmanifest", produces = "application/manifest+json")
    @Operation(summary = "İdarəetmə panelinin PWA manifesti", description = "Şirkətin adı və ikonu; /admin/ ünvanında açılır.")
    ResponseEntity<Map<String, Object>> adminManifest() {
        return manifest("/admin/", null);
    }

    private ResponseEntity<Map<String, Object>> manifest(String start, String orientation) {
        PublicCompanyResponse info = company.publicCompany();
        Language language = Language.fromTag(info.defaultLanguage()).orElse(Language.DEFAULT);
        String name = info.name().resolve(language);
        String color = info.brandColor();
        String version = iconVersion(info);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("id", start);
        manifest.put("name", name);
        manifest.put("short_name", name.length() > 12 ? name.substring(0, 12).strip() : name);
        if (info.description() != null) {
            manifest.put("description", info.description().resolve(language));
        }
        manifest.put("lang", language.tag());
        manifest.put("start_url", start);
        manifest.put("scope", start);
        manifest.put("display", "standalone");
        if (orientation != null) {
            manifest.put("orientation", orientation);
        }
        manifest.put("theme_color", color);
        manifest.put("background_color", "#F6F7FB");
        manifest.put("icons", List.of(
                icon("/api/v1/public/icons/192.png?v=" + version, "192x192", "any"),
                icon("/api/v1/public/icons/512.png?v=" + version, "512x512", "any"),
                icon("/api/v1/public/icons/maskable-512.png?v=" + version, "512x512", "maskable")));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .contentType(MediaType.parseMediaType("application/manifest+json"))
                .body(manifest);
    }

    @GetMapping(value = "/api/v1/public/icons/{name}.png", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Tətbiqin ikonu", description = "180, 192, 512 və ya maskable-512 (Android adaptiv ikon).")
    ResponseEntity<byte[]> icon(@PathVariable String name) {
        boolean maskable = name.equals("maskable-512");
        int size;
        try {
            size = maskable ? 512 : Integer.parseInt(name);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (!SIZES.contains(size)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        PublicCompanyResponse info = company.publicCompany();
        String key = TenantContext.require().id() + ":" + iconVersion(info) + ":" + name;
        byte[] png = icons.get(key, k -> logos.logo()
                .filter(logo -> info.logoUrl() != null)
                .map(logo -> renderLogo(logo.png(), size, maskable))
                .orElseGet(() -> render(size, Color.decode(info.brandColor()), maskable)));
        return ResponseEntity.ok().cacheControl(ICON_CACHE).contentType(MediaType.IMAGE_PNG).body(png);
    }

    /** İkonun versiyası: rəng və ya loqo dəyişəndə dəyişir (telefon və brauzer yeni ikonu yükləyir). */
    private static String iconVersion(PublicCompanyResponse info) {
        String version = info.brandColor().substring(1).toLowerCase(java.util.Locale.ROOT);
        String logo = info.logoUrl();
        return logo == null ? version : version + "-l" + logo.substring(logo.indexOf("?v=") + 3);
    }

    /**
     * Loqodan ikon: adi variantda loqo bütün kvadratı tutur (künclər telefonun özü tərəfindən yuvarlaqlaşdırılır).
     * Maskable variantda (Android adaptiv ikon) loqo təhlükəsiz zonaya (mərkəzi 80%) yerləşdirilir, ətrafı ağ olur.
     * Şəffaf hissələr ağ fonda göstərilir (iPhone şəffaf ikonu qara edir).
     */
    static byte[] renderLogo(byte[] logoPng, int size, boolean maskable) {
        BufferedImage logo;
        try {
            logo = ImageIO.read(new ByteArrayInputStream(logoPng));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, size, size);
            int inner = maskable ? Math.round(size * 0.8f) : size;
            int offset = (size - inner) / 2;
            g.drawImage(logo, offset, offset, inner, inner, null);
        } finally {
            g.dispose();
        }
        return png(image);
    }

    private static byte[] png(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, String> icon(String src, String sizes, String purpose) {
        return Map.of("src", src, "sizes", sizes, "type", "image/png", "purpose", purpose);
    }

    /** Brend rəngində fon, ağ halqa və ulduz. Maskable variantda fon tam kvadratdır, şəkil təhlükəsiz zonadadır. */
    static byte[] render(int size, Color brand, boolean maskable) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(brand);
            if (maskable) {
                g.fillRect(0, 0, size, size);
            } else {
                double radius = size * 0.22;
                g.fill(new RoundRectangle2D.Double(0, 0, size, size, radius * 2, radius * 2));
            }
            double c = size / 2.0;
            double scale = maskable ? 0.32 : 0.38;
            double ring = size * scale;
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke((float) (size * 0.045)));
            g.draw(new Ellipse2D.Double(c - ring, c - ring, ring * 2, ring * 2));
            Path2D star = new Path2D.Double();
            double outer = ring * 0.62;
            double inner = outer * 0.43;
            for (int i = 0; i < 10; i++) {
                double r = i % 2 == 0 ? outer : inner;
                double angle = Math.PI / 5 * i - Math.PI / 2;
                double x = c + Math.cos(angle) * r;
                double y = c + Math.sin(angle) * r;
                if (i == 0) {
                    star.moveTo(x, y);
                } else {
                    star.lineTo(x, y);
                }
            }
            star.closePath();
            g.fill(star);
        } finally {
            g.dispose();
        }
        return png(image);
    }
}
