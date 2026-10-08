package az.tapstamp.shared.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

import az.tapstamp.shared.i18n.LocalizedText;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link LocalizedText} ↔ JSONB. Sütun {@code @ColumnTransformer(write = "?::jsonb")} ilə yazılır,
 * oxunanda PostgreSQL JSONB dəyərini mətn kimi qaytarır.
 */
@Converter
public class LocalizedTextConverter implements AttributeConverter<LocalizedText, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(LocalizedText text) {
        if (text == null) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>();
        map.put("az", text.az());
        if (text.en() != null && !text.en().isBlank()) {
            map.put("en", text.en());
        }
        if (text.ru() != null && !text.ru().isBlank()) {
            map.put("ru", text.ru());
        }
        return JSON.writeValueAsString(map);
    }

    @Override
    public LocalizedText convertToEntityAttribute(String json) {
        if (json == null) {
            return null;
        }
        Map<String, String> map = JSON.readValue(json, MAP_TYPE);
        return new LocalizedText(map.get("az"), map.get("en"), map.get("ru"));
    }
}
