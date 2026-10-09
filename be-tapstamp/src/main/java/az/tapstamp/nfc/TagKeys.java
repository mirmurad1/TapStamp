package az.tapstamp.nfc;

import java.util.Arrays;
import java.util.UUID;

import org.jspecify.annotations.NonNull;

/**
 * Çipin deşifrə olunmuş SUN açarları (K1 — SDMMetaReadKey, K2 — SDMFileReadKey). Yalnız yoxlama müddətində yaddaşda
 * saxlanılır: {@link #wipe()} istifadədən dərhal sonra çağırılır.
 */
public record TagKeys(UUID id, int version, KeyStatus status, byte[] metaReadKey, byte[] fileReadKey) {

    public enum KeyStatus {
        PENDING,
        ACTIVE,
        RETIRED
    }

    public void wipe() {
        Arrays.fill(metaReadKey, (byte) 0);
        Arrays.fill(fileReadKey, (byte) 0);
    }

    @Override
    public @NonNull String toString() {
        return "TagKeys[id=" + id + ", version=" + version + ", status=" + status + "]";
    }
}
