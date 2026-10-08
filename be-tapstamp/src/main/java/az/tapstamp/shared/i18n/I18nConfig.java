package az.tapstamp.shared.i18n;

import java.nio.charset.StandardCharsets;

import az.tapstamp.config.AppProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.DispatcherServlet;

/** Backend mesajlarının (xətalar, validasiya, SMS) 3 dildə konfiqurasiyası. */
@Configuration
public class I18nConfig {

    @Bean
    MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // Sistem lokalına (serverin dilinə) heç vaxt düşmürük — nəticə həmişə az/en/ru-dur
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(Language.DEFAULT.locale());
        return source;
    }

    /** Bean adı {@code localeResolver} olmalıdır — DispatcherServlet onu bu adla axtarır. */
    @Bean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    AppLocaleResolver localeResolver(AppProperties properties) {
        Language defaultLanguage = Language.fromTag(properties.defaultLanguage()).orElse(Language.DEFAULT);
        return new AppLocaleResolver(defaultLanguage);
    }
}
