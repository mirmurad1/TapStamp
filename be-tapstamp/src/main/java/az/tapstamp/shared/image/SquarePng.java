package az.tapstamp.shared.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
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

    /** JPEG keyfiyyəti: fotolar üçün gözlə fərq edilmir, həcm PNG-dən təxminən 6–8 dəfə kiçikdir. */
    static final float JPEG_QUALITY = 0.82f;

    /** Yoxlama (növ, həcm, ölçü həddləri), kvadrata gətirmə və PNG-yə çevirmə. */
    public byte[] normalize(byte[] file) {
        BufferedImage image = squared(decode(validated(file)), storedSize);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * Fotolar üçün (menyu şəkilləri): yoxlama, kvadrata gətirmə və JPEG-ə çevirmə. JPEG şəffaflığı saxlamır — şəffaf
     * hissələr ağ olur.
     */
    public byte[] normalizeJpeg(byte[] file) {
        return jpeg(squared(decode(validated(file)), storedSize));
    }

    /**
     * Artıq saxlanılmış (yoxlanılmış) kvadrat şəkildən kiçik JPEG variant — siyahılarda göstərmək üçün. Şəkil
     * {@code size}-dan kiçikdirsə böyüdülmür.
     */
    public static byte[] jpegThumbnail(byte[] stored, int size) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(stored));
            if (source == null) {
                throw new IllegalArgumentException("Unreadable stored image");
            }
            return jpeg(squared(source, size));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Baytlara görə şəklin növü (saxlanılmış şəkillər üçün): JPEG və ya PNG. */
    public static String contentType(byte[] image) {
        return startsWith(image, JPEG_MAGIC) ? "image/jpeg" : "image/png";
    }

    private byte[] validated(byte[] file) {
        if (file == null || file.length == 0 || file.length > maxBytes
                || !(startsWith(file, PNG_MAGIC) || startsWith(file, JPEG_MAGIC))) {
            throw new ApiException(invalid);
        }
        return file;
    }

    /** JPEG-ə çevirmə: şəffaf hissələr ağ fonla doldurulur (JPEG-də alfa kanal yoxdur). */
    private static byte[] jpeg(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
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

    /** Mərkəzdən kvadrat kəsir və lazım olsa {@code size} piksələ kiçildir (şəffaflıq saxlanılır). */
    private static BufferedImage squared(BufferedImage source, int size) {
        int side = Math.min(source.getWidth(), source.getHeight());
        BufferedImage crop = source.getSubimage((source.getWidth() - side) / 2, (source.getHeight() - side) / 2,
                side, side);
        int target = Math.min(side, size);
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
