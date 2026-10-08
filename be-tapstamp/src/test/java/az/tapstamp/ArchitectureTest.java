package az.tapstamp;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RestController;

/** Arxitektura qaydaları: modul sərhədləri, dev kodunun izolyasiyası, lokal təhlükəsizliyi. */
@AnalyzeClasses(packages = "az.tapstamp", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule productionCodeMustNotDependOnDevPackage = noClasses()
            .that().resideOutsideOfPackage("az.tapstamp.dev..")
            .should().dependOnClassesThat().resideInAPackage("az.tapstamp.dev..")
            .because("dev kodu (simulyator, outbox, seed) prod-da mövcud olmamalıdır");

    @ArchTest
    static final ArchRule devComponentsAreDevProfileOnly = classes()
            .that().resideInAPackage("az.tapstamp.dev..")
            .and().areTopLevelClasses()
            .and().areNotInterfaces()
            .and().areNotRecords()
            .and().areNotEnums()
            .should().beAnnotatedWith(Profile.class)
            .allowEmptyShould(true)
            .because("dev bean-ləri yalnız @Profile(\"dev\") ilə yaradılmalıdır");

    @ArchTest
    static final ArchRule caseConversionMustUseExplicitLocale = noClasses()
            .should().callMethod(String.class, "toUpperCase")
            .orShould().callMethod(String.class, "toLowerCase")
            .because("az lokalında 'i'.toUpperCase() 'İ' verir; texniki sətirlərdə Locale.ROOT istifadə edin");

    @ArchTest
    static final ArchRule controllersLiveInApiOrWebPackages = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAnyPackage("..api..", "..web..")
            .because("controller-lər modulun api (və ya ümumi web) qatındadır");

    @ArchTest
    static final ArchRule noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noJavaUtilLogging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    /**
     * API-nin sorğu/cavab modelləri OpenAPI-də sadə adla (məs. {@code MeResponse}) sxemə çevrilir. Eyni adlı iki model
     * biri digərini səssizcə əvəz edər və frontend-in tipləri səhv olar — adlar unikal olmalıdır.
     */
    @Test
    void apiModelNamesAreUnique() {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("az.tapstamp");
        Map<String, List<String>> byName = new HashMap<>();
        for (JavaClass type : classes) {
            if (type.isRecord() && type.getPackageName().matches(".*\\.(api|web)$")) {
                byName.computeIfAbsent(type.getSimpleName(), name -> new ArrayList<>()).add(type.getFullName());
            }
        }
        assertThat(byName.entrySet().stream().filter(entry -> entry.getValue().size() > 1).map(Map.Entry::getValue))
                .as("eyni adlı API modelləri").isEmpty();
    }
}
