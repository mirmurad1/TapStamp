package az.tapstamp.nfc;

import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;

/**
 * Toxunuş URL-inin parametrləri: {@code /t/{uid}?picc=…&cmac=…}. Format yoxlaması DB-yə müraciətdən əvvəl aparılır.
 * {@code uid} yalnız çipi tapmaq üçün ipucudur — etibarlı UID deşifrə olunmuş PICC məlumatından götürülür.
 */
public record TapParams(byte[] uid, byte[] picc, byte[] cmac) {

    private static final Pattern UID = Pattern.compile("^[0-9A-Fa-f]{14}$");
    private static final Pattern PICC = Pattern.compile("^[0-9A-Fa-f]{32}$");
    private static final Pattern CMAC = Pattern.compile("^[0-9A-Fa-f]{16}$");
    private static final HexFormat HEX = HexFormat.of();

    public static Optional<TapParams> parse(String uid, String picc, String cmac) {
        if (uid == null || picc == null || cmac == null || !UID.matcher(uid).matches() || !PICC.matcher(picc).matches()
                || !CMAC.matcher(cmac).matches()) {
            return Optional.empty();
        }
        return Optional.of(new TapParams(HEX.parseHex(uid), HEX.parseHex(picc), HEX.parseHex(cmac)));
    }

    @Override
    public @NonNull String toString() {
        return "TapParams[uid=" + HEX.formatHex(uid) + "]";
    }
}
