package az.tapstamp.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Kampaniya mətni: şirkət adı, fərdi müraciət və SMS sayının hər müştərinin adı ilə hesablanması. */
class CampaignTextsTest {

    @Test
    void composesCompanyNameAndOptionalGreeting() {
        assertThat(CampaignTexts.compose("Kafe", " Endirim var! ", false, "Aysel")).isEqualTo("Kafe: Endirim var!");
        assertThat(CampaignTexts.compose("Kafe", "Endirim var!", true, " Aysel ")).isEqualTo("Kafe: Dəyərli Aysel, Endirim var!");
        assertThat(CampaignTexts.compose("Kafe", "Endirim var!", true, null)).isEqualTo("Kafe: Dəyərli istifadəçi, Endirim var!");
        assertThat(CampaignTexts.compose("Kafe", "Endirim var!", true, "  ")).isEqualTo("Kafe: Dəyərli istifadəçi, Endirim var!");
    }

    @Test
    void latinVersionReplacesAzerbaijaniLetters() {
        assertThat(CampaignTexts.latin("Dəyərli Gülşən, ÇƏĞIŞ ı")).isEqualTo("Deyerli Gulsen, CEGIS i");
    }

    @Test
    void personalizedTotalsAreCountedPerRecipientName() {
        // "Dəyərli"-də ə var: hər mesaj UCS-2 (70 simvol); uzun ad mesajı 2 SMS edir
        String text = "x".repeat(40);
        List<String> names = Arrays.asList("Aysel", null, "Abdulrahmanzadə Gülnarə Məmmədəli qızı");
        CampaignTexts.Totals totals = CampaignTexts.totals("Kafe", text, true, names, false);
        assertThat(totals.minSegments()).isEqualTo(1);
        assertThat(totals.maxSegments()).isEqualTo(2);
        assertThat(totals.total()).isEqualTo(4);
        // Latın hərfləri ilə hamısı 1 SMS-ə sığır (160 simvol)
        assertThat(CampaignTexts.totals("Kafe", text, true, names, true).total()).isEqualTo(3);
        // Fərdi deyilsə: hamıya eyni mətn
        assertThat(CampaignTexts.totals("Kafe", text, false, names, false).total()).isEqualTo(3);
    }
}
