package az.cafeloyalty.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.UUID;

import az.cafeloyalty.shared.pii.PhoneMasker;
import org.junit.jupiter.api.Test;

class PhoneProtectionTest {

    private final PhoneHasher hasher = new PhoneHasher(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void hashIsDeterministicWithinTenantButDiffersAcrossTenants() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        assertThat(hasher.hash(tenantA, "+994501234567")).isEqualTo(hasher.hash(tenantA, "+994501234567"));
        assertThat(hasher.hash(tenantA, "+994501234567"))
                .as("eyni nömrə iki şirkətdə fərqli hash alır — bazalar arasında bağlantı yoxdur")
                .isNotEqualTo(hasher.hash(tenantB, "+994501234567"));
        assertThat(hasher.hash(tenantA, "+994501234567")).isNotEqualTo(hasher.hash(tenantA, "+994501234568"));
    }

    @Test
    void masksAzerbaijaniAndForeignNumbers() {
        assertThat(PhoneMasker.mask("+994501234567")).isEqualTo("+994 50 ***-**-67");
        assertThat(PhoneMasker.mask("+447911123456")).isEqualTo("+447 ***56");
        assertThat(PhoneMasker.mask("not a phone")).isEqualTo("***");
        assertThat(PhoneMasker.mask(null)).isEqualTo("***");
    }
}
