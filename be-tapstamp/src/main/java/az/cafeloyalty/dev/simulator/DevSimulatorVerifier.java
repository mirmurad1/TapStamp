package az.cafeloyalty.dev.simulator;

import java.security.MessageDigest;
import java.util.Arrays;

import az.cafeloyalty.nfc.NfcTag;
import az.cafeloyalty.nfc.TagKeys;
import az.cafeloyalty.nfc.TagType;
import az.cafeloyalty.nfc.TagVerifier;
import az.cafeloyalty.nfc.TapParams;
import az.cafeloyalty.nfc.TapVerificationException;
import az.cafeloyalty.nfc.VerifiedTap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@code SIMULATED} çiplərin yoxlanılması (yalnız dev): {@code picc} = sayğac (3 bayt, LSB first) ‖ 13 təsadüfi bayt,
 * {@code cmac} = HMAC-SHA256(APP_DEV_SIMULATOR_KEY, uid ‖ picc)-in ilk 8 baytı. Prod-da bu bean yoxdur — SIMULATED
 * çipə toxunuş {@code TAP_SIGNATURE_INVALID} alır.
 */
@Component
@Profile("dev")
public class DevSimulatorVerifier implements TagVerifier {

    private final DevSimulatorKey key;

    public DevSimulatorVerifier(DevSimulatorKey key) {
        this.key = key;
    }

    @Override
    public boolean supports(TagType type) {
        return type == TagType.SIMULATED;
    }

    @Override
    public VerifiedTap verify(NfcTag tag, TagKeys keys, TapParams params) {
        if (!MessageDigest.isEqual(key.mac(tag.uid(), params.picc()), params.cmac())) {
            throw new TapVerificationException("CMAC_INVALID");
        }
        byte[] picc = params.picc();
        int counter = (picc[0] & 0xFF) | (picc[1] & 0xFF) << 8 | (picc[2] & 0xFF) << 16;
        return new VerifiedTap(Arrays.copyOf(tag.uid(), tag.uid().length), counter);
    }
}
