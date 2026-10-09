package az.tapstamp.shared.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

import az.tapstamp.shared.i18n.LocalizedText;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link LocalizedText} ↔ JSONB mətni ({@code {"az": "...", "en": "...", "ru": "..."}}): boş tərcümələr yazılmır,
 * oxunanda yoxdursa null qalır. MyBatis tip çeviricisi ({@code LocalizedTextTypeHandler}) bu sinfi istifadə edir.
 */
public class LocalizedTextConverter {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    public String toJson(LocalizedText text) {
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

    public LocalizedText fromJson(String json) {
        if (json == null) {
            return null;
        }
        Map<String, String> map = JSON.readValue(json, MAP_TYPE);
        return new LocalizedText(map.get("az"), map.get("en"), map.get("ru"));
    }
}
