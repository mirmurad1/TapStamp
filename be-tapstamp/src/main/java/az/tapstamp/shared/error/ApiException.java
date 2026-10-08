package az.tapstamp.shared.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Biznes xətası: sabit kod, lokallaşdırılmış mesaj üçün arqumentlər və Problem Details-ə əlavə ediləcək
 * sahələr (məs. {@code retryAfterSeconds}). Mesajın özü burada saxlanılmır — dil sorğuya görə seçilir.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Object[] messageArgs;
    private final transient Map<String, Object> properties;

    public ApiException(ErrorCode code) {
        this(code, new Object[0], Map.of());
    }

    public ApiException(ErrorCode code, Object[] messageArgs, Map<String, Object> properties) {
        super(code.name());
        this.code = code;
        this.messageArgs = messageArgs.clone();
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public ErrorCode code() {
        return code;
    }

    public Object[] messageArgs() {
        return messageArgs.clone();
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
