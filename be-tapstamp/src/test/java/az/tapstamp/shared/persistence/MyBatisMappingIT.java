package az.tapstamp.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import az.tapstamp.company.repository.CompanyMapper;
import az.tapstamp.security.admin.repository.AdminAccountMapper;
import az.tapstamp.security.admin.repository.AdminSessionMapper;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.support.IntegrationTest;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.registry.TenantDirectory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * MyBatis xəritələmələri: hər mapper metodunun XML-də SQL-i var (səhv adlı və ya unudulmuş statement yalnız çağırılanda
 * aşkarlanardı) və tip çeviriciləri real bazada işləyir.
 */
class MyBatisMappingIT extends IntegrationTest {

    @Autowired
    @Qualifier("tenantSqlSessionFactory")
    SqlSessionFactory tenantSql;

    @Autowired
    @Qualifier("platformSqlSessionFactory")
    SqlSessionFactory platformSql;

    @Autowired
    CompanyMapper companies;

    @Autowired
    TenantDirectory directory;

    @Test
    void everyMapperMethodHasAStatement() throws Exception {
        List<String> missing = new ArrayList<>();
        for (Class<?> mapper : mappers(TenantMapper.class)) {
            missing.addAll(missingStatements(mapper, tenantSql.getConfiguration()));
        }
        for (Class<?> mapper : mappers(PlatformMapper.class)) {
            missing.addAll(missingStatements(mapper, platformSql.getConfiguration()));
        }
        // Admin hesabları və sessiyaları hər iki bazada eyni SQL ilə işləyir
        for (Class<?> mapper : List.of(AdminAccountMapper.class, AdminSessionMapper.class)) {
            missing.addAll(missingStatements(mapper, tenantSql.getConfiguration()));
            missing.addAll(missingStatements(mapper, platformSql.getConfiguration()));
        }
        assertThat(missing).isEmpty();
        assertThat(mappers(TenantMapper.class)).hasSizeGreaterThan(10);
    }

    @Test
    void localizedTextAndUuidV7RoundTrip() {
        TenantContext.runAs(directory.findBySlug("beta").orElseThrow().toInfo(), () -> {
            UUID id = Ids.newId();
            LocalizedText name = LocalizedText.of("Sınaq filialı", "Test branch", "Тестовый филиал");
            companies.insertBranch(id, name, null, null, Instant.now());
            var saved = companies.branches().stream().filter(branch -> branch.id().equals(id)).findFirst().orElseThrow();

            assertThat(saved.id().version()).isEqualTo(7);
            assertThat(saved.name()).isEqualTo(name);
            assertThat(saved.address()).isNull();
            companies.updateBranch(id, null, null, null, false, "CLOSED", Instant.now());
        });
    }

    private static List<Class<?>> mappers(Class<? extends java.lang.annotation.Annotation> marker) throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition bean) {
                return bean.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(marker));
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("az.tapstamp")) {
            result.add(Class.forName(candidate.getBeanClassName()));
        }
        return result;
    }

    private static List<String> missingStatements(Class<?> mapper, Configuration configuration) {
        List<String> missing = new ArrayList<>();
        for (Method method : mapper.getMethods()) {
            if (method.isDefault() || method.getDeclaringClass() == Object.class) {
                continue;
            }
            String id = mapper.getName() + "." + method.getName();
            if (!configuration.hasStatement(id)) {
                missing.add(id);
            }
        }
        return missing;
    }
}
