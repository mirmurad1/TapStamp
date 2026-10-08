package az.tapstamp.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.util.UUID;

import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.i18n.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TenantContextTest {

    private static TenantInfo tenant(String slug) {
        return new TenantInfo(UUID.randomUUID(), slug, TenantStatus.ACTIVE, Language.AZ, ZoneId.of("Asia/Baku"));
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void requireFailsClosedWithoutTenant() {
        assertThatThrownBy(TenantContext::require).isInstanceOf(ApiException.class);
    }

    @Test
    void callAsRestoresPreviousTenant() {
        TenantInfo outer = tenant("outer");
        TenantInfo inner = tenant("inner");
        TenantContext.runAs(outer, () -> {
            TenantContext.runAs(inner, () -> assertThat(TenantContext.require()).isEqualTo(inner));
            assertThat(TenantContext.require()).isEqualTo(outer);
        });
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void contextIsRestoredEvenWhenActionFails() {
        assertThatThrownBy(() -> TenantContext.runAs(tenant("x"), () -> {
            throw new IllegalStateException("boom");
        })).hasMessage("boom");
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void tenantCannotChangeInsideActiveTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> TenantContext.runAs(tenant("x"), () -> { }))
                .isInstanceOf(IllegalStateException.class);
    }
}
