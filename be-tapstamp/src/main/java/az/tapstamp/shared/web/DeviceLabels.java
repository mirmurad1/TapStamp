package az.tapstamp.shared.web;

/**
 * Cihazlar siyahısı üçün User-Agent-dən qısa ad: "Chrome · Android". Dəqiq olması vacib deyil — istifadəçi öz
 * cihazlarını tanıya bilsin deyə.
 */
public final class DeviceLabels {

    private DeviceLabels() {
    }

    public static String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String browser = userAgent.contains("Edg/") ? "Edge"
                : userAgent.contains("SamsungBrowser/") ? "Samsung Internet"
                : userAgent.contains("YaBrowser/") ? "Yandex"
                : userAgent.contains("Firefox/") || userAgent.contains("FxiOS/") ? "Firefox"
                : userAgent.contains("Chrome/") || userAgent.contains("CriOS/") ? "Chrome"
                : userAgent.contains("Safari/") ? "Safari"
                : null;
        String os = userAgent.contains("Android") ? "Android"
                : userAgent.contains("iPhone") || userAgent.contains("iPad") ? "iOS"
                : userAgent.contains("Windows") ? "Windows"
                : userAgent.contains("Mac OS X") ? "macOS"
                : userAgent.contains("Linux") ? "Linux"
                : null;
        if (browser == null && os == null) {
            return null;
        }
        return browser == null ? os : os == null ? browser : browser + " · " + os;
    }
}
