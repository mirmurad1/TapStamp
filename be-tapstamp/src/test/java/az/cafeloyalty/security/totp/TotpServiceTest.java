package az.cafeloyalty.security.totp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** TOTP (RFC 6238): rəsmi test vektorları, ±1 addım pəncərəsi və kodun təkrar istifadəsinin qarşısı. */
class TotpServiceTest {

    /** RFC 6238 Əlavə B: SHA1 üçün sirr "12345678901234567890". */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private static TotpService at(long epochSeconds) {
        return new TotpService(Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC));
    }

    @Test
    void matchesRfc6238TestVectors() {
        assertThat(TotpService.codeForStep(RFC_SECRET, 59 / 30, 8)).isEqualTo("94287082");
        assertThat(TotpService.codeForStep(RFC_SECRET, 1111111109L / 30, 8)).isEqualTo("07081804");
        assertThat(TotpService.codeForStep(RFC_SECRET, 1234567890L / 30, 8)).isEqualTo("89005924");
        assertThat(TotpService.codeForStep(RFC_SECRET, 2000000000L / 30, 8)).isEqualTo("69279037");
        // 6 rəqəmli kod 8 rəqəmlinin son 6 rəqəmidir
        assertThat(TotpService.codeForStep(RFC_SECRET, 59 / 30)).isEqualTo("287082");
    }

    @Test
    void acceptsCurrentAndAdjacentStepsOnly() {
        long now = 1_800_000_000L;
        long step = now / 30;
        TotpService totp = at(now);
        assertThat(totp.verify(RFC_SECRET, TotpService.codeForStep(RFC_SECRET, step), null)).hasValue(step);
        assertThat(totp.verify(RFC_SECRET, TotpService.codeForStep(RFC_SECRET, step - 1), null)).hasValue(step - 1);
        assertThat(totp.verify(RFC_SECRET, TotpService.codeForStep(RFC_SECRET, step + 1), null)).hasValue(step + 1);
        assertThat(totp.verify(RFC_SECRET, TotpService.codeForStep(RFC_SECRET, step - 2), null)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, TotpService.codeForStep(RFC_SECRET, step + 2), null)).isEmpty();
    }

    @Test
    void rejectsReusedOrOlderSteps() {
        long now = 1_800_000_000L;
        long step = now / 30;
        TotpService totp = at(now);
        String code = TotpService.codeForStep(RFC_SECRET, step);
        assertThat(totp.verify(RFC_SECRET, code, step)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, TotpService.codeForStep(RFC_SECRET, step - 1), step - 1)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, code, step - 1)).hasValue(step);
    }

    @Test
    void rejectsMalformedCodes() {
        TotpService totp = at(1_800_000_000L);
        assertThat(totp.verify(RFC_SECRET, null, null)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, "12345", null)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, "1234567", null)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, "12a456", null)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, "١٢٣٤٥٦", null)).isEmpty();
    }

    @Test
    void otpauthUriContainsBase32SecretAndEncodedLabel() {
        TotpService totp = at(0);
        String uri = totp.otpauthUri("Cafe Loyalty · mockcoffee", "owner@mockcoffee.az", RFC_SECRET);
        assertThat(uri).startsWith("otpauth://totp/Cafe%20Loyalty%20%C2%B7%20mockcoffee:owner%40mockcoffee.az?")
                .contains("secret=" + Base32.encode(RFC_SECRET))
                .contains("algorithm=SHA1&digits=6&period=30");
    }

    @Test
    void generatedSecretsAre160BitsAndRandom() {
        TotpService totp = at(0);
        byte[] first = totp.generateSecret();
        assertThat(first).hasSize(20);
        assertThat(first).isNotEqualTo(totp.generateSecret());
    }

    @Test
    void base32RoundTripsAndMatchesRfc4648() {
        assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(Base32.encode(RFC_SECRET)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Base32.decode("gezdgnbvgy3tqojqgezdgnbvgy3tqojq")).isEqualTo(RFC_SECRET);
        assertThat(Base32.decode("MZXW 6YTB OI")).isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
    }
}
