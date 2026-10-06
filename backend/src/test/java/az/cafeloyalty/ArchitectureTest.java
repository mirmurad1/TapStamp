package az.cafeloyalty;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RestController;

/** Arxitektura qaydaları: modul sərhədləri, dev kodunun izolyasiyası, lokal təhlükəsizliyi. */
@AnalyzeClasses(packages = "az.cafeloyalty", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule productionCodeMustNotDependOnDevPackage = noClasses()
            .that().resideOutsideOfPackage("az.cafeloyalty.dev..")
            .should().dependOnClassesThat().resideInAPackage("az.cafeloyalty.dev..")
            .because("dev kodu (simulyator, outbox, seed) prod-da mövcud olmamalıdır");

    @ArchTest
    static final ArchRule devComponentsAreDevProfileOnly = classes()
            .that().resideInAPackage("az.cafeloyalty.dev..")
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
}
