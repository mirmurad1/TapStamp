package az.tapstamp.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class EnvelopeCipherTest {

    /** Yaddaşda saxlanan açarlar: DB olmadan şifrələmə formatını yoxlamaq üçün. */
    private static final class InMemoryKeys implements DataKeyProvider {
        final Map<UUID, DataKey> keys = new HashMap<>();
        DataKey active;

        InMemoryKeys rotate() {
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            active = new DataKey(UUID.randomUUID(), KeyPurpose.PII, new SecretKeySpec(raw, "AES"));
            keys.put(active.id(), active);
            return this;
        }

        @Override
        public DataKey activeKey(KeyPurpose purpose) {
            return active;
        }

        @Override
        public DataKey keyById(UUID id) {
            DataKey key = keys.get(id);
            if (key == null) {
                throw new CryptoException("Data key not found");
            }
            return key;
        }
    }

    private final InMemoryKeys keys = new InMemoryKeys().rotate();
    private final EnvelopeCipher cipher = new EnvelopeCipher(keys);
    private final UUID tenant = UUID.randomUUID();
    private final UUID row = UUID.randomUUID();
    private final String aad = Aad.tenantField(tenant, "customers", "phone_enc", row);

    @Test
    void roundTripAndRandomizedCiphertext() {
        byte[] first = cipher.encryptString(KeyPurpose.PII, "+994501234567", aad);
        byte[] second = cipher.encryptString(KeyPurpose.PII, "+994501234567", aad);

        assertThat(cipher.decryptString(first, aad)).isEqualTo("+994501234567");
        assertThat(first).as("eyni mətn hər dəfə fərqli şifrələnir (təsadüfi nonce)").isNotEqualTo(second);
        assertThat(new String(first)).doesNotContain("994501234567");
    }

    @Test
    void ciphertextCannotBeMovedToAnotherRowColumnOrTenant() {
        byte[] envelope = cipher.encryptString(KeyPurpose.PII, "+994501234567", aad);

        assertThatThrownBy(() -> cipher.decryptString(envelope,
                Aad.tenantField(tenant, "customers", "phone_enc", UUID.randomUUID()))).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> cipher.decryptString(envelope,
                Aad.tenantField(tenant, "customers", "name", row))).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> cipher.decryptString(envelope,
                Aad.tenantField(UUID.randomUUID(), "customers", "phone_enc", row))).isInstanceOf(CryptoException.class);
    }

    @Test
    void tamperingIsDetected() {
        byte[] envelope = cipher.encryptString(KeyPurpose.PII, "+994501234567", aad);
        envelope[envelope.length - 3] ^= 0x01;
        assertThatThrownBy(() -> cipher.decryptString(envelope, aad)).isInstanceOf(CryptoException.class);
    }

    @Test
    void oldDataRemainsReadableAfterKeyRotation() {
        byte[] beforeRotation = cipher.encryptString(KeyPurpose.PII, "köhnə", aad);
        keys.rotate();
        byte[] afterRotation = cipher.encryptString(KeyPurpose.PII, "yeni", aad);

        assertThat(cipher.decryptString(beforeRotation, aad)).isEqualTo("köhnə");
        assertThat(cipher.decryptString(afterRotation, aad)).isEqualTo("yeni");
    }

    @Test
    void unknownFormatIsRejected() {
        assertThatThrownBy(() -> cipher.decrypt(new byte[] {9, 1, 2}, aad)).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> cipher.decrypt(null, aad)).isInstanceOf(CryptoException.class);
    }

    @Test
    void kekWrapsAndRotates() {
        String oldKek = Base64.getEncoder().encodeToString(new byte[32]);
        byte[] newKeyBytes = new byte[32];
        newKeyBytes[0] = 1;
        String newKek = Base64.getEncoder().encodeToString(newKeyBytes);

        var before = new LocalKeyEncryptionService("kek-1:" + oldKek, "kek-1");
        var wrapped = before.wrap(new byte[] {1, 2, 3}, "dek:x:PII");
        var after = new LocalKeyEncryptionService("kek-1:" + oldKek + ",kek-2:" + newKek, "kek-2");

        assertThat(after.unwrap(wrapped, "dek:x:PII")).containsExactly(1, 2, 3);
        assertThat(after.wrap(new byte[] {4}, "dek:y:PII").kekId()).isEqualTo("kek-2");
        assertThatThrownBy(() -> after.unwrap(wrapped, "dek:other:PII")).isInstanceOf(CryptoException.class);
    }

    @Test
    void invalidKekConfigurationFailsFast() {
        assertThatThrownBy(() -> new LocalKeyEncryptionService("", "a")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LocalKeyEncryptionService("a:" + Base64.getEncoder().encodeToString(new byte[16]), "a"))
                .hasMessageContaining("32 bayt");
        assertThatThrownBy(() -> new LocalKeyEncryptionService("a:" + Base64.getEncoder().encodeToString(new byte[32]), "b"))
                .isInstanceOf(IllegalStateException.class);
    }
}
