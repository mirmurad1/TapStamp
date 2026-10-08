package az.cafeloyalty.customer.phone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Telefon nömrəsinin E.164 formatına salınması və yalnız icazəli ölkələrin mobil nömrələrinin qəbulu. */
class PhoneNumberServiceTest {

    private static final List<String> AZ_ONLY = List.of("AZ");

    private final PhoneNumberService service = new PhoneNumberService();

    @ParameterizedTest
    @ValueSource(strings = {"050 123 45 67", "+994501234567", "994 50 123-45-67", "(050) 123-45-67", "+994 50 123 45 67",
            "0501234567", " 050-123-45-67 "})
    void normalizesAzerbaijaniFormatsToE164(String input) {
        assertThat(service.normalize(input, AZ_ONLY)).isEqualTo("+994501234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"055 555 55 55", "070 123 45 67", "077 123 45 67", "099 123 45 67", "051 123 45 67",
            "010 123 45 67"})
    void acceptsAllMobileOperators(String input) {
        assertThat(service.normalize(input, AZ_ONLY)).startsWith("+994").hasSize(13);
    }

    @ParameterizedTest
    @ValueSource(strings = {"012 497 00 00", "+994124970000", "12345", "abc", "+994 50 123", "050 123 45 6789", ""})
    void rejectsLandlinesAndMalformedNumbers(String input) {
        assertCode(input, AZ_ONLY, ErrorCode.PHONE_INVALID);
    }

    @Test
    void rejectsMobileNumbersFromCountriesNotAllowedByTheCompany() {
        assertCode("+7 912 345 67 89", AZ_ONLY, ErrorCode.PHONE_REGION_NOT_ALLOWED);
        assertCode("+90 532 123 45 67", AZ_ONLY, ErrorCode.PHONE_REGION_NOT_ALLOWED);
        assertThat(service.normalize("+7 912 345 67 89", List.of("AZ", "ru"))).isEqualTo("+79123456789");
    }

    @Test
    void rejectsNullAndOverlongInput() {
        assertCode(null, AZ_ONLY, ErrorCode.PHONE_INVALID);
        assertCode("0".repeat(40), AZ_ONLY, ErrorCode.PHONE_INVALID);
    }

    private void assertCode(String input, List<String> regions, ErrorCode code) {
        assertThatThrownBy(() -> service.normalize(input, regions))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }
}
