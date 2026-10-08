package az.tapstamp.security.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Sahə səviyyəsində şifrələmə (envelope encryption).
 *
 * <pre>
 * format: versiya(1) ‖ dek_id(16) ‖ nonce(12) ‖ şifrəli mətn ‖ teq(16)
 * AAD:    çağıranın verdiyi kontekst, məs. "t:&lt;tenant&gt;/customers.phone_enc:&lt;sətir id&gt;"
 * </pre>
 * AAD sayəsində şifrəli dəyəri başqa sətrə, sütuna və ya başqa tenant-a köçürüb açmaq mümkün deyil.
 */
public class EnvelopeCipher {

    private static final byte FORMAT_VERSION = 1;
    private static final int HEADER_LENGTH = 1 + 16;

    private final DataKeyProvider keys;

    public EnvelopeCipher(DataKeyProvider keys) {
        this.keys = keys;
    }

    public byte[] encrypt(KeyPurpose purpose, byte[] plaintext, String aad) {
        DataKeyProvider.DataKey key = keys.activeKey(purpose);
        byte[] sealed = AesGcm.encrypt(key.key(), plaintext, aad);
        return ByteBuffer.allocate(HEADER_LENGTH + sealed.length)
                .put(FORMAT_VERSION)
                .putLong(key.id().getMostSignificantBits())
                .putLong(key.id().getLeastSignificantBits())
                .put(sealed)
                .array();
    }

    public byte[] decrypt(byte[] envelope, String aad) {
        if (envelope == null || envelope.length <= HEADER_LENGTH || envelope[0] != FORMAT_VERSION) {
            throw new CryptoException("Unsupported envelope format");
        }
        ByteBuffer buffer = ByteBuffer.wrap(envelope);
        buffer.get();
        UUID keyId = new UUID(buffer.getLong(), buffer.getLong());
        byte[] sealed = new byte[buffer.remaining()];
        buffer.get(sealed);
        return AesGcm.decrypt(keys.keyById(keyId).key(), sealed, aad);
    }

    public byte[] encryptString(KeyPurpose purpose, String plaintext, String aad) {
        return encrypt(purpose, plaintext.getBytes(StandardCharsets.UTF_8), aad);
    }

    public String decryptString(byte[] envelope, String aad) {
        return new String(decrypt(envelope, aad), StandardCharsets.UTF_8);
    }
}
