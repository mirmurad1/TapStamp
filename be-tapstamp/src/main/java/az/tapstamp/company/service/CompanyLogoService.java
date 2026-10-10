package az.tapstamp.company.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.company.repository.CompanyMapper;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * Şirkətin loqosu (müştəri tətbiqinin başlığında). Yalnız PNG və JPEG qəbul olunur (SVG-də skript ola bilər).
 * Kvadrat olmayan şəkil mərkəzdən kvadrat kəsilir, {@value #STORED_SIZE} pikseldən böyükdürsə kiçildilir və PNG kimi
 * yenidən kodlanır — EXIF (məs. GPS) və faylın içinə gizlədilmiş məlumat saxlanılmır.
 */
@Service
public class CompanyLogoService {

    /** Yüklənən faylın ən böyük həcmi. */
    public static final int MAX_BYTES = 1024 * 1024;
    /** Qısa tərəfin ən kiçik ölçüsü. */
    public static final int MIN_SIZE = 192;
    /** Uzun tərəfin ən böyük ölçüsü (yaddaş: açılmış şəkil ən çox ~64 MB). */
    public static final int MAX_SIZE = 4096;
    /** Saxlanılan loqonun ölçüsü (başlıqda 40–64 px göstərilir, yüksək sıxlıqlı ekranlar üçün ehtiyatla). */
    public static final int STORED_SIZE = 512;

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final CompanyMapper companies;
    private final AuditLogger audit;
    private final Clock clock;

    public CompanyLogoService(CompanyMapper companies, AuditLogger audit, Clock clock) {
        this.companies = companies;
        this.audit = audit;
        this.clock = clock;
    }

    public Optional<CompanyMapper.Logo> logo() {
        return companies.findLogo();
    }

    public void upload(AdminPrincipal principal, byte[] file) {
        AccessPolicy.requireCompanyWide(principal);
        byte[] png = normalize(file);
        companies.updateLogo(png, clock.instant());
        audit.tenant("COMPANY_LOGO_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "company", null,
                Map.of("bytes", png.length));
    }

    public void remove(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        companies.updateLogo(null, clock.instant());
        audit.tenant("COMPANY_LOGO_REMOVED", AuditLogger.ActorType.STAFF, principal.userId(), "company", null,
                Map.of());
    }

    /** Yoxlama (növ, həcm, ölçü həddləri), kvadrata gətirmə və PNG-yə çevirmə. */
    static byte[] normalize(byte[] file) {
        if (file == null || file.length == 0 || file.length > MAX_BYTES
                || !(startsWith(file, PNG_MAGIC) || startsWith(file, JPEG_MAGIC))) {
            throw new ApiException(ErrorCode.LOGO_INVALID);
        }
        BufferedImage image = squared(decode(file));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * Ölçülər şəkil açılmazdan əvvəl başlıqdan oxunur: kiçik fayl böyük ölçü elan edib yaddaşı doldura bilməz
     * (decompression bomb).
     */
    private static BufferedImage decode(byte[] file) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(file))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ApiException(ErrorCode.LOGO_INVALID);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (Math.min(width, height) < MIN_SIZE || Math.max(width, height) > MAX_SIZE) {
                    throw new ApiException(ErrorCode.LOGO_INVALID);
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw new ApiException(ErrorCode.LOGO_INVALID);
        }
    }

    /** Mərkəzdən kvadrat kəsir və lazım olsa {@value #STORED_SIZE} piksələ kiçildir (şəffaflıq saxlanılır). */
    private static BufferedImage squared(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        BufferedImage crop = source.getSubimage((source.getWidth() - side) / 2, (source.getHeight() - side) / 2,
                side, side);
        int target = Math.min(side, STORED_SIZE);
        BufferedImage result = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(crop, 0, 0, target, target, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }
}
