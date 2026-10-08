package az.tapstamp.dev.simulator;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import az.tapstamp.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Simulyasiya olunan çiplərin imza açarı ({@code APP_DEV_SIMULATOR_KEY}). Təyin olunmayıbsa hər başlanğıcda
 * təsadüfi açar yaradılır (əvvəlki simulyator linkləri etibarsız olur). Yalnız dev.
 */
@Component
@Profile("dev")
public class DevSimulatorKey {

    private static final Logger log = LoggerFactory.getLogger(DevSimulatorKey.class);

    private final byte[] key;

    public DevSimulatorKey(AppProperties properties) {
        String configured = properties.dev().simulatorKey();
        if (configured == null || configured.isBlank()) {
            key = new byte[32];
            new SecureRandom().nextBytes(key);
            log.warn("APP_DEV_SIMULATOR_KEY is not set; using a random key for this run");
        } else {
            key = Base64.getDecoder().decode(configured.strip());
        }
    }

    /** cmac = HMAC-SHA256(açar, uid ‖ picc) dəyərinin ilk 8 baytı. */
    public byte[] mac(byte[] uid, byte[] picc) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(uid);
            return Arrays.copyOf(mac.doFinal(picc), 8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
