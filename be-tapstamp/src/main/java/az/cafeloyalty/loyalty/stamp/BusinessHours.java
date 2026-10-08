package az.cafeloyalty.loyalty.stamp;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Filialın iş saatları: {@code {"mon": [["08:00","22:00"]], ...}}. Gecəyarısını keçən interval ({@code ["20:00","02:00"]})
 * iki günə bölünür: başladığı gün sonuna qədər, növbəti gün səhərə qədər. Qrafik yoxdursa filial açıq sayılır.
 */
public final class BusinessHours {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, List<List<String>>>> TYPE = new TypeReference<>() {
    };

    private BusinessHours() {
    }

    public static boolean isOpen(String json, ZonedDateTime at) {
        if (json == null || json.isBlank()) {
            return true;
        }
        Map<String, List<List<String>>> schedule = JSON.readValue(json, TYPE);
        LocalTime time = at.toLocalTime();
        for (List<String> interval : schedule.getOrDefault(key(at.getDayOfWeek()), List.of())) {
            LocalTime open = LocalTime.parse(interval.get(0));
            LocalTime close = LocalTime.parse(interval.get(1));
            boolean overnight = !close.isAfter(open);
            if (overnight ? !time.isBefore(open) : !time.isBefore(open) && time.isBefore(close)) {
                return true;
            }
        }
        for (List<String> interval : schedule.getOrDefault(key(at.getDayOfWeek().minus(1)), List.of())) {
            LocalTime open = LocalTime.parse(interval.get(0));
            LocalTime close = LocalTime.parse(interval.get(1));
            if (!close.isAfter(open) && time.isBefore(close)) {
                return true;
            }
        }
        return false;
    }

    private static String key(DayOfWeek day) {
        return day.name().substring(0, 3).toLowerCase(Locale.ROOT);
    }
}
