package az.tapstamp.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUIDv7 generatoru (RFC 9562): ilk 48 bit — millisaniyə ilə Unix vaxtı, qalanı təsadüfi.
 * Vaxta görə sıralandığı üçün B-tree indekslərdə səmərəlidir; təsadüfi hissə isə ID-lərin
 * təxmin edilməsinin qarşısını alır (IDOR-a qarşı əlavə qat).
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static UUID newId() {
        return newId(System.currentTimeMillis());
    }

    static UUID newId(long epochMillis) {
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        long randA = ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL);          // 12 bit
        long randB = 0L;
        for (int i = 2; i < 10; i++) {
            randB = (randB << 8) | (random[i] & 0xFFL);
        }
        long msb = ((epochMillis & 0xFFFF_FFFF_FFFFL) << 16) | (0x7L << 12) | randA; // versiya 7
        long lsb = (randB & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;     // variant 10
        return new UUID(msb, lsb);
    }

    /** UUIDv7-dən yaranma vaxtını (millisaniyə) çıxarır. */
    public static long timestampMillis(UUID id) {
        return id.getMostSignificantBits() >>> 16;
    }
}
