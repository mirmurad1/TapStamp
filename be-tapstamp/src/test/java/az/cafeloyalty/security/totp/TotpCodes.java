package az.cafeloyalty.security.totp;

import java.time.Instant;

/** Testlər üçün: verilmiş andakı TOTP kodu (autentifikator tətbiqinin göstərdiyi kimi). */
public final class TotpCodes {

    private TotpCodes() {
    }

    public static String at(byte[] secret, Instant instant) {
        return TotpService.codeForStep(secret, instant.getEpochSecond() / TotpService.STEP_SECONDS);
    }
}
