package az.tapstamp.tenancy.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class DbIdentifiersTest {

    @Test
    void derivesNamesFromSlug() {
        assertThat(DbIdentifiers.databaseName("mockcoffee")).isEqualTo("loyalty_t_mockcoffee");
        assertThat(DbIdentifiers.ownerRole("mockcoffee")).isEqualTo("t_mockcoffee_owner");
        assertThat(DbIdentifiers.appRole("mockcoffee")).isEqualTo("t_mockcoffee_app");
        assertThat(DbIdentifiers.quote("t_mockcoffee_app")).isEqualTo("\"t_mockcoffee_app\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mock", "1coffee", "a", "mock-coffee", "mock\"; DROP DATABASE x; --", "mock coffee",
            "çay", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsUnsafeSlugs(String slug) {
        assertThatThrownBy(() -> DbIdentifiers.requireSlug(slug)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "has'quote-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "with space aaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsUnsafePasswordLiterals(String password) {
        assertThatThrownBy(() -> DbIdentifiers.passwordLiteral(password)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatedPasswordsAreSafeLiterals() {
        String password = TenantProvisioner.randomPassword();
        assertThat(DbIdentifiers.passwordLiteral(password)).isEqualTo("'" + password + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "a.b/../c", "bonus.mockcoffee.az:443", "UPPER.CASE.AZ", "-"})
    void rejectsInvalidHostnames(String host) {
        assertThatThrownBy(() -> DbIdentifiers.requireHostname(host)).isInstanceOf(IllegalArgumentException.class);
    }
}
