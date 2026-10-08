package az.tapstamp.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import az.tapstamp.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

/** 3 dilin mesaj fayllarında açarlar eyni olmalıdır; çatışmayan və ya boş tərcümə testi uğursuz edir. */
class MessagesParityTest {

    @Test
    void allLanguagesHaveTheSameKeys() throws IOException {
        Set<String> reference = keys(Language.AZ);
        for (Language language : Language.values()) {
            Set<String> current = keys(language);
            Set<String> missing = new TreeSet<>(reference);
            missing.removeAll(current);
            Set<String> extra = new TreeSet<>(current);
            extra.removeAll(reference);
            assertThat(missing).as("%s faylında çatışmayan açarlar", language.tag()).isEmpty();
            assertThat(extra).as("%s faylında artıq açarlar", language.tag()).isEmpty();
        }
    }

    @Test
    void noTranslationIsBlank() throws IOException {
        for (Language language : Language.values()) {
            Properties properties = load(language);
            for (String key : properties.stringPropertyNames()) {
                assertThat(properties.getProperty(key)).as("%s: %s", language.tag(), key).isNotBlank();
            }
        }
    }

    @Test
    void everyErrorCodeHasTitleAndDetail() throws IOException {
        Set<String> keys = keys(Language.AZ);
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(keys).contains(code.titleKey(), code.detailKey());
        }
    }

    private static Set<String> keys(Language language) throws IOException {
        return new TreeSet<>(load(language).stringPropertyNames());
    }

    private static Properties load(Language language) throws IOException {
        String path = "/i18n/messages_" + language.tag() + ".properties";
        var stream = MessagesParityTest.class.getResourceAsStream(path);
        assertThat(stream).as(path).isNotNull();
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        }
    }
}
