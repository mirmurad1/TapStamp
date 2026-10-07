package az.cafeloyalty.nfc;

import java.security.MessageDigest;

import az.cafeloyalty.nfc.sun.Ntag424Sun;
import org.springframework.stereotype.Component;

/**
 * NTAG 424 DNA SUN yoxlaması (plan §6.3): PICCData K1 ilə deşifrə olunur, UID çipin UID-i ilə müqayisə edilir,
 * CMAC K2-dən törədilən sessiya açarı ilə sabit zamanlı yoxlanılır. Sayğac yalnız CMAC doğru olduqdan sonra
 * nəzərə alınır — saxta sorğu sayğacı "irəli itələyə" bilməz.
 */
@Component
public class Ntag424SunVerifier implements TagVerifier {

    @Override
    public boolean supports(TagType type) {
        return type == TagType.NTAG424_SUN;
    }

    @Override
    public VerifiedTap verify(NfcTag tag, TagKeys keys, TapParams params) {
        if (keys == null) {
            throw new TapVerificationException("KEYS_MISSING");
        }
        Ntag424Sun.PiccData data = Ntag424Sun.decryptPicc(keys.metaReadKey(), params.picc());
        if (data == null) {
            throw new TapVerificationException("PICC_INVALID");
        }
        if (!MessageDigest.isEqual(data.uid(), tag.uid())) {
            throw new TapVerificationException("UID_MISMATCH");
        }
        if (!Ntag424Sun.verifyMac(keys.fileReadKey(), data.uid(), data.counter(), params.cmac())) {
            throw new TapVerificationException("CMAC_INVALID");
        }
        return new VerifiedTap(data.uid(), data.counter());
    }
}
