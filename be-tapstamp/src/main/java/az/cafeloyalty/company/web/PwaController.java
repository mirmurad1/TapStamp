package az.cafeloyalty.company.web;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import az.cafeloyalty.company.api.PublicCompanyResponse;
import az.cafeloyalty.company.service.CompanyQueryService;
import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import az.cafeloyalty.shared.i18n.Language;
import az.cafeloyalty.tenancy.TenantContext;
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
 * minimal Docker image-də də işləyir. Şirkət və rəng üzrə yaddaşda keşlənir.
 */
@RestController
@Tag(name = "İctimai: PWA")
public class PwaController {

    /** İcazə verilən ikon ölçüləri: iPhone (180), Android (192, 512). */
    private static final List<Integer> SIZES = List.of(180, 192, 512);
    private static final CacheControl ICON_CACHE = CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

    private final CompanyQueryService company;
    private final Cache<String, byte[]> icons = Caffeine.newBuilder().maximumSize(500).build();

    public PwaController(CompanyQueryService company) {
        this.company = company;
    }

    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
    @Operation(summary = "Şirkətin PWA manifesti", description = "Ad şirkətin standart dilində, rəng — brend rəngi.")
    ResponseEntity<Map<String, Object>> manifest() {
        PublicCompanyResponse info = company.publicCompany();
        Language language = Language.fromTag(info.defaultLanguage()).orElse(Language.DEFAULT);
        String name = info.name().resolve(language);
        String color = info.brandColor();
        String version = color.substring(1).toLowerCase(java.util.Locale.ROOT);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("id", "/");
        manifest.put("name", name);
        manifest.put("short_name", name.length() > 12 ? name.substring(0, 12).strip() : name);
        if (info.description() != null) {
            manifest.put("description", info.description().resolve(language));
        }
        manifest.put("lang", language.tag());
        manifest.put("start_url", "/");
        manifest.put("scope", "/");
        manifest.put("display", "standalone");
        manifest.put("orientation", "portrait");
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
        String color = company.publicCompany().brandColor();
        String key = TenantContext.require().id() + ":" + color + ":" + name;
        byte[] png = icons.get(key, k -> render(size, Color.decode(color), maskable));
        return ResponseEntity.ok().cacheControl(ICON_CACHE).contentType(MediaType.IMAGE_PNG).body(png);
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
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
