package az.tapstamp.shared.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;

/**
 * Yüklənən şəkli təhlükəsiz kvadrat PNG-yə çevirir (loqo, kataloq şəkilləri). Yalnız PNG və JPEG qəbul olunur (SVG-də
 * skript ola bilər); kvadrat olmayan şəkil mərkəzdən kəsilir, böyükdürsə kiçildilir və PNG kimi yenidən kodlanır —
 * EXIF (məs. GPS) və faylın içinə gizlədilmiş məlumat saxlanılmır.
 *
 * @param maxBytes   faylın ən böyük həcmi
 * @param minSize    qısa tərəfin ən kiçik ölçüsü
 * @param maxSize    uzun tərəfin ən böyük ölçüsü (yaddaş: açılmış şəkil ən çox ~64 MB)
 * @param storedSize saxlanılan şəklin tərəfi
 * @param invalid    uyğun olmayan fayl üçün xəta kodu
 */
public record SquarePng(int maxBytes, int minSize, int maxSize, int storedSize, ErrorCode invalid) {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** Yoxlama (növ, həcm, ölçü həddləri), kvadrata gətirmə və PNG-yə çevirmə. */
    public byte[] normalize(byte[] file) {
        if (file == null || file.length == 0 || file.length > maxBytes
                || !(startsWith(file, PNG_MAGIC) || startsWith(file, JPEG_MAGIC))) {
            throw new ApiException(invalid);
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
    private BufferedImage decode(byte[] file) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(file))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ApiException(invalid);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (Math.min(width, height) < minSize || Math.max(width, height) > maxSize) {
                    throw new ApiException(invalid);
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw new ApiException(invalid);
        }
    }

    /** Mərkəzdən kvadrat kəsir və lazım olsa {@code storedSize} piksələ kiçildir (şəffaflıq saxlanılır). */
    private BufferedImage squared(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        BufferedImage crop = source.getSubimage((source.getWidth() - side) / 2, (source.getHeight() - side) / 2,
                side, side);
        int target = Math.min(side, storedSize);
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
