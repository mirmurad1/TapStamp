package az.tapstamp.dev.seed;

import java.util.List;

import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.tenancy.provisioning.ProvisionTenantCommand;
import org.springframework.context.annotation.Profile;

/** Dev mühitinin test şirkətləri: Mock Coffee House (əsas nümayiş) və Demo Çay Evi (izolyasiya nümayişi). */
@Profile("dev")
public final class SeedCatalog {

    public static final String MOCK_COFFEE_DOMAIN = "mockcoffee.localhost";
    public static final String DEMO_TEA_DOMAIN = "demo-cay.localhost";

    private static final String BUSINESS_HOURS = """
            {"mon":[["08:00","22:00"]],"tue":[["08:00","22:00"]],"wed":[["08:00","22:00"]],\
            "thu":[["08:00","22:00"]],"fri":[["08:00","23:00"]],"sat":[["09:00","23:00"]],"sun":[["09:00","22:00"]]}""";

    private SeedCatalog() {
    }

    public record TagSeed(String label, String uidHex, String type) {
    }

    public record BranchSeed(LocalizedText name, LocalizedText address, LocalizedText description,
                             String businessHours, List<TagSeed> tags) {
    }

    public record RuleSeed(int stampsRequired, LocalizedText name, LocalizedText description) {
    }

    public record TenantSeed(ProvisionTenantCommand command, LocalizedText description, String smsSenderName,
                             String brandColor,
                             List<BranchSeed> branches, RuleSeed rule, int customerCount, long randomSeed) {
    }

    public static TenantSeed mockCoffee() {
        return new TenantSeed(
                new ProvisionTenantCommand("mockcoffee",
                        LocalizedText.of("Mock Coffee House", "Mock Coffee House", "Mock Coffee House"),
                        MOCK_COFFEE_DOMAIN, Language.AZ, "Asia/Baku"),
                LocalizedText.of(
                        "Bakının mərkəzində təzə qovrulmuş qəhvə və isti atmosfer. Hər fincan sizi hədiyyəyə bir addım yaxınlaşdırır.",
                        "Freshly roasted coffee and a warm atmosphere in the heart of Baku. Every cup brings you closer to a free one.",
                        "Свежеобжаренный кофе и уютная атмосфера в центре Баку. Каждая чашка приближает вас к подарочной."),
                "MockCoffee",
                "#7C3AED",
                List.of(
                        new BranchSeed(
                                LocalizedText.of("Mərkəz filialı", "City Centre", "Центральный филиал"),
                                LocalizedText.of("Nizami küçəsi 10, Bakı", "10 Nizami Street, Baku", "ул. Низами, 10, Баку"),
                                LocalizedText.of("Fəvvarələr meydanının yanında, terraslı kafe.",
                                        "Next to Fountain Square, with a terrace.",
                                        "Рядом с площадью Фонтанов, есть терраса."),
                                BUSINESS_HOURS,
                                List.of(new TagSeed("Piştaxta 1", "04A1B2C3D4E5F1", "SIMULATED"),
                                        new TagSeed("Piştaxta 2", "04A1B2C3D4E5F2", "NTAG424_SUN"))),
                        new BranchSeed(
                                LocalizedText.of("Gənclik filialı", "Ganjlik", "Филиал Гянджлик"),
                                LocalizedText.of("Atatürk prospekti 25, Bakı", "25 Ataturk Avenue, Baku", "пр. Ататюрка, 25, Баку"),
                                LocalizedText.of("Metroya yaxın, sakit iş guşəsi ilə.",
                                        "Close to the metro, with a quiet work corner.",
                                        "Рядом с метро, есть тихая рабочая зона."),
                                BUSINESS_HOURS,
                                List.of(new TagSeed("Piştaxta", "04A1B2C3D4E5F3", "SIMULATED")))),
                new RuleSeed(9,
                        LocalizedText.of("Hədiyyə qəhvə", "Free coffee", "Подарочный кофе"),
                        LocalizedText.of("9 möhür toplayın — 10-cu qəhvə bizdən!",
                                "Collect 9 stamps and your 10th coffee is on us!",
                                "Соберите 9 штампов — 10-й кофе за наш счёт!")),
                150,
                20261006L);
    }

    public static TenantSeed demoTea() {
        return new TenantSeed(
                new ProvisionTenantCommand("democay",
                        LocalizedText.of("Demo Çay Evi", "Demo Tea House", "Демо Чайный дом"),
                        DEMO_TEA_DOMAIN, Language.AZ, "Asia/Baku"),
                LocalizedText.of("İçərişəhərdə ənənəvi çay evi.", "A traditional tea house in the Old City.",
                        "Традиционный чайный дом в Старом городе."),
                "DemoCay",
                "#0D9488",
                List.of(new BranchSeed(
                        LocalizedText.of("İçərişəhər", "Old City", "Ичеришехер"),
                        LocalizedText.of("Qüllə küçəsi 3, Bakı", "3 Gulle Street, Baku", "ул. Гюлля, 3, Баку"),
                        null,
                        BUSINESS_HOURS,
                        List.of(new TagSeed("Piştaxta", "04B1B2C3D4E5F1", "SIMULATED")))),
                new RuleSeed(8,
                        LocalizedText.of("Hədiyyə çay dəsti", "Free tea set", "Подарочный чайный сет"),
                        null),
                12,
                777L);
    }
}
