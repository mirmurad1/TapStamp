package az.tapstamp.customer.phone;

import java.util.Collection;
import java.util.Locale;

import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import org.springframework.stereotype.Component;

/**
 * Telefon nömrəsinin yoxlanması və E.164 formatına salınması (libphonenumber). Standart region AZ-dir:
 * {@code 050 123 45 67}, {@code +994501234567} və {@code 994 50 123-45-67} eyni {@code +994501234567} olur.
 *
 * <p>Yalnız mobil nömrələr və şirkətin icazə verdiyi ölkələr qəbul olunur — bahalı beynəlxalq nömrələrə SMS
 * göndərtməklə edilən fırıldağın (SMS pumping) qarşısı.
 */
@Component
public class PhoneNumberService {

    private static final String DEFAULT_REGION = "AZ";
    private static final int MAX_INPUT_LENGTH = 32;

    private final PhoneNumberUtil util = PhoneNumberUtil.getInstance();

    public String normalize(String raw, Collection<String> allowedRegions) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_INPUT_LENGTH) {
            throw new ApiException(ErrorCode.PHONE_INVALID);
        }
        PhoneNumber number;
        try {
            number = util.parse(withInternationalPrefix(raw.strip()), DEFAULT_REGION);
        } catch (NumberParseException e) {
            throw new ApiException(ErrorCode.PHONE_INVALID);
        }
        if (!util.isValidNumber(number)) {
            throw new ApiException(ErrorCode.PHONE_INVALID);
        }
        PhoneNumberType type = util.getNumberType(number);
        if (type != PhoneNumberType.MOBILE && type != PhoneNumberType.FIXED_LINE_OR_MOBILE) {
            throw new ApiException(ErrorCode.PHONE_INVALID);
        }
        String region = util.getRegionCodeForNumber(number);
        boolean allowed = allowedRegions.stream().anyMatch(r -> r.toUpperCase(Locale.ROOT).equals(region));
        if (!allowed) {
            throw new ApiException(ErrorCode.PHONE_REGION_NOT_ALLOWED);
        }
        return util.format(number, PhoneNumberFormat.E164);
    }

    /** "994 50 …" kimi "+" olmadan yazılmış beynəlxalq formatı tanımaq üçün (AZ milli formatı 0 ilə başlayır). */
    private static String withInternationalPrefix(String value) {
        String digits = value.replaceAll("[\\s().-]", "");
        return digits.startsWith("994") && digits.length() == 12 ? "+" + digits : value;
    }
}
