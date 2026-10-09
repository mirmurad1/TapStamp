package az.tapstamp.tenancy.datasource;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;

import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.PlatformMapper;
import az.tapstamp.shared.persistence.TenantMapper;
import az.tapstamp.shared.persistence.mybatis.InstantTypeHandler;
import az.tapstamp.shared.persistence.mybatis.LanguageTypeHandler;
import az.tapstamp.shared.persistence.mybatis.LocalizedTextTypeHandler;
import az.tapstamp.shared.persistence.mybatis.UuidTypeHandler;
import az.tapstamp.shared.persistence.mybatis.ZoneIdTypeHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * MyBatis: SQL sorğuları {@code resources/mapper/**}{@code /*.xml} fayllarındadır, Java tərəfində yalnız mapper
 * interfeysləri ({@code <modul>.repository} paketləri) var. İki ayrı {@link SqlSessionFactory}:
 * <ul>
 *   <li><b>tenant</b> — {@link TenantMapper}; bağlantı cari tenant-ın bazasından ({@link TenantRoutingDataSource});</li>
 *   <li><b>platforma</b> — {@link PlatformMapper}; platforma bazası.</li>
 * </ul>
 * {@code mapper/admin/*.xml} (admin hesabları və sessiyaları) hər iki fabrikdə yüklənir: cədvəl adları fabrikin
 * dəyişənlərindən gəlir ({@code ${admin.users}} və s., {@link AdminRealm#mapperVariables()}) — işçilər şirkətin
 * bazasında, platforma adminləri platforma bazasındadır.
 *
 * <p>Tranzaksiyalar Spring-in tranzaksiya menecerləri ilə idarə olunur (mapper eyni bağlantını istifadə edir).
 */
@org.springframework.context.annotation.Configuration
public class MyBatisConfig {

    @Bean
    SqlSessionFactory tenantSqlSessionFactory(@Qualifier("tenantDataSource") DataSource dataSource) throws Exception {
        return factory(dataSource, AdminRealm.STAFF, "classpath*:mapper/tenant/**/*.xml");
    }

    @Bean
    SqlSessionFactory platformSqlSessionFactory(@Qualifier("platformDataSource") DataSource dataSource)
            throws Exception {
        return factory(dataSource, AdminRealm.PLATFORM, "classpath*:mapper/platform/**/*.xml");
    }

    @Bean
    static MapperScannerConfigurer tenantMappers() {
        return scanner(TenantMapper.class, "tenantSqlSessionFactory");
    }

    @Bean
    static MapperScannerConfigurer platformMappers() {
        return scanner(PlatformMapper.class, "platformSqlSessionFactory");
    }

    private static SqlSessionFactory factory(DataSource dataSource, AdminRealm realm, String mappers) throws Exception {
        Configuration configuration = new Configuration();
        // snake_case sütunlar → camelCase sahələr; record-lar konstruktor parametrlərinin adı ilə doldurulur
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setArgNameBasedConstructorAutoMapping(true);
        configuration.setCallSettersOnNulls(true);
        configuration.setReturnInstanceForEmptyRow(false);
        // Sessiya keşi yoxdur: kilid (FOR UPDATE) və təkrar oxunuşlar həmişə bazanın cari vəziyyətini görsün
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.getTypeHandlerRegistry().register(Instant.class, InstantTypeHandler.class);
        configuration.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
        configuration.getTypeHandlerRegistry().register(LocalizedText.class, LocalizedTextTypeHandler.class);
        configuration.getTypeHandlerRegistry().register(Language.class, LanguageTypeHandler.class);
        configuration.getTypeHandlerRegistry().register(ZoneId.class, ZoneIdTypeHandler.class);
        Properties variables = realm.mapperVariables();
        configuration.setVariables(variables);

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] own = resolver.getResources(mappers);
        Resource[] admin = resolver.getResources("classpath*:mapper/admin/*.xml");
        Resource[] all = new Resource[own.length + admin.length];
        System.arraycopy(own, 0, all, 0, own.length);
        System.arraycopy(admin, 0, all, own.length, admin.length);

        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setConfiguration(configuration);
        bean.setConfigurationProperties(variables);
        bean.setMapperLocations(all);
        return bean.getObject();
    }

    private static MapperScannerConfigurer scanner(Class<? extends java.lang.annotation.Annotation> marker,
                                                   String factoryBeanName) {
        MapperScannerConfigurer scanner = new MapperScannerConfigurer();
        scanner.setBasePackage("az.tapstamp");
        scanner.setAnnotationClass(marker);
        scanner.setSqlSessionFactoryBeanName(factoryBeanName);
        return scanner;
    }
}
