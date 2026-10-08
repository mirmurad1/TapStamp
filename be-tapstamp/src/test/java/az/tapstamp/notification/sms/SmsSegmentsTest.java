package az.tapstamp.notification.sms;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import az.tapstamp.notification.sms.SmsSegments.Encoding;
import az.tapstamp.shared.i18n.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * SMS seqmentləri: GSM-7 (160/153) və UCS-2 (70/67). Azərbaycan dilinin ə, ş, ğ, ı, ç hərfləri GSM-7-də yoxdur — az SMS-i
 * də, rus SMS-i kimi, 70 simvola sığmalıdır. OTP mesajı istənilən dildə və uzun şirkət adı ilə 1 seqment olmalıdır.
 */
class SmsSegmentsTest {

    /** Real həyatda rast gəlinə biləcək ən uzun adlar (3 dildə). */
    private static final String LONG_AZ = "Qəhvəxana və Şirniyyat Evi «Gənclik Bağçası» Məhdud Məsuliyyətli Cəmiyyəti";
    private static final String LONG_EN = "The Grand Old Baku Coffee Roasters & Pastry House International";
    private static final String LONG_RU = "Кофейня и кондитерская «Старый Баку» Общество с ограниченной ответственностью";

    @Test
    void latinTextUsesGsm7() {
        SmsSegments.Info info = SmsSegments.analyze("Mock Coffee code: 123456. Do not share it.");
        assertThat(info.encoding()).isEqualTo(Encoding.GSM_7);
        assertThat(info.segments()).isEqualTo(1);
    }

    @Test
    void azerbaijaniLettersForceUcs2() {
        assertThat(SmsSegments.analyze("Mock Coffee kodu: 123456. Heç kimə deməyin.").encoding()).isEqualTo(Encoding.UCS_2);
        for (String letter : new String[] {"ə", "ş", "ğ", "ı", "ç", "Ə", "Ş", "Ğ", "İ"}) {
            assertThat(SmsSegments.analyze("kod " + letter).encoding()).as(letter).isEqualTo(Encoding.UCS_2);
        }
        // ö, ü və Ç GSM-7 əlifbasındadır
        assertThat(SmsSegments.analyze("Göy Ünvan Çay").encoding()).isEqualTo(Encoding.GSM_7);
    }

    @Test
    void segmentBoundaries() {
        assertThat(SmsSegments.analyze("a".repeat(160)).segments()).isEqualTo(1);
        assertThat(SmsSegments.analyze("a".repeat(161)).segments()).isEqualTo(2);
        assertThat(SmsSegments.analyze("a".repeat(306)).segments()).isEqualTo(2);
        assertThat(SmsSegments.analyze("a".repeat(307)).segments()).isEqualTo(3);
        assertThat(SmsSegments.analyze("ə".repeat(70)).segments()).isEqualTo(1);
        assertThat(SmsSegments.analyze("ə".repeat(71)).segments()).isEqualTo(2);
        assertThat(SmsSegments.analyze("ə".repeat(134)).segments()).isEqualTo(2);
        assertThat(SmsSegments.analyze("ə".repeat(135)).segments()).isEqualTo(3);
    }

    @Test
    void extensionCharactersTakeTwoSeptets() {
        SmsSegments.Info info = SmsSegments.analyze("€".repeat(80));
        assertThat(info.encoding()).isEqualTo(Encoding.GSM_7);
        assertThat(info.units()).isEqualTo(160);
        assertThat(SmsSegments.analyze("€".repeat(81)).segments()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void otpMessageFitsOneSegmentEvenWithTheLongestCompanyName(Language language) {
        SmsTemplates templates = new SmsTemplates(messages());
        for (String name : new String[] {LONG_AZ, LONG_EN, LONG_RU, "Mock Coffee House", "Demo Çay Evi", ""}) {
            String text = templates.otp(language, name, "123456", "bonus.mockcoffee.az");
            assertThat(SmsSegments.analyze(text).segments()).as(language + ": " + text).isEqualTo(1);
            assertThat(text).contains("123456");
        }
    }

    @Test
    void messagesAreInTheCustomersLanguageAndIncludeTheCompanyName() {
        SmsTemplates templates = new SmsTemplates(messages());
        assertThat(templates.otp(Language.AZ, "Mock Coffee House", "123456", null))
                .isEqualTo("Mock Coffee House kodu: 123456. Heç kimə deməyin.");
        assertThat(templates.otp(Language.EN, "Mock Coffee House", "123456", null))
                .isEqualTo("Mock Coffee House code: 123456. Do not share it.");
        assertThat(templates.otp(Language.RU, "Mock Coffee House", "123456", null))
                .isEqualTo("Код Mock Coffee House: 123456. Никому не сообщайте.");
    }

    @Test
    void webOtpLineIsAddedOnlyWhenItFits() {
        SmsTemplates templates = new SmsTemplates(messages());
        // en (GSM-7, 160 simvol) — yer var
        assertThat(templates.otp(Language.EN, "Mock Coffee", "654321", "bonus.mockcoffee.az"))
                .endsWith("\n\n@bonus.mockcoffee.az #654321");
        // az (UCS-2, 70 simvol) — uzun domenlə yer qalmır, sətir əlavə olunmur
        String az = templates.otp(Language.AZ, "Mock Coffee House", "654321", "bonus.mockcoffee-international.az");
        assertThat(az).doesNotContain("#654321\n").doesNotContain("@bonus");
    }

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(Locale.of("az"));
        return source;
    }
}
