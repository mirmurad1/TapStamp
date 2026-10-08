package az.cafeloyalty.loyalty.reward;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import az.cafeloyalty.security.crypto.CryptoProperties;
import az.cafeloyalty.security.crypto.Hkdf;
import org.springframework.stereotype.Component;

/**
 * Hədiyyə kartının daimi kodu: {@code 7K3M-9QX2} (oxunaqlı əlifba, 0/O və 1/I yoxdur, 40 bit).
 *
 * <p>Kod bazada saxlanılmır: o, tenant-a məxsus açarla {@code rewards.id} və {@code code_nonce}-dan törədilir
 * (HMAC). Axtarış üçün yalnız kodun öz HMAC-ı ({@code code_lookup}) saxlanılır — baza sızsa belə kodlar bilinmir,
 * açar isə env-dədir ({@code APP_REDEMPTION_CODE_KEY}).
 */
@Component
public class GiftCardCodes {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Pattern FORMAT = Pattern.compile("^[" + ALPHABET + "]{8}$");

    private final byte[] codeKey;
    private final byte[] lookupKey;

    public GiftCardCodes(CryptoProperties crypto) {
        byte[] master = Base64.getDecoder().decode(crypto.redemptionCodeKey());
        this.codeKey = Hkdf.derive(master, utf8("cafe-loyalty:gift-card-code:v1"), new byte[0], 32);
        this.lookupKey = Hkdf.derive(master, utf8("cafe-loyalty:gift-card-lookup:v1"), new byte[0], 32);
    }

    /** Kartın göstərilən kodu (həmişə eyni nəticə). */
    public String code(UUID tenantId, UUID rewardId, int nonce) {
        byte[] digest = Hkdf.hmac(codeKey, utf8(tenantId + ":" + rewardId + ":" + nonce));
        long bits = 0;
        for (int i = 0; i < 5; i++) {
            bits = (bits << 8) | (digest[i] & 0xFF);
        }
        StringBuilder code = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                code.append('-');
            }
            code.append(ALPHABET.charAt((int) ((bits >>> (35 - 5 * i)) & 0x1F)));
        }
        return code.toString();
    }

    /** Axtarış açarı: işçinin daxil etdiyi kodun HMAC-ı (tenant-a bağlı). */
    public byte[] lookup(UUID tenantId, String code) {
        return Hkdf.hmac(lookupKey, utf8(tenantId + ":" + code.replace("-", "")));
    }

    /** İşçinin yazdığı kodu normallaşdırır (boşluq və tire atılır, böyük hərflər). Format səhvdirsə boş nəticə. */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String compact = input.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        return FORMAT.matcher(compact).matches() ? Optional.of(compact) : Optional.empty();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
