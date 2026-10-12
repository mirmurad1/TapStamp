package az.tapstamp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Birdəfəlik CLI əmrləri tanınır: iş bitəndən sonra tətbiq bağlanmalıdır (əks halda əmr asılı qalır). */
class TapStampApplicationTest {

    @Test
    void recognisesOneShotCommands() {
        assertThat(TapStampApplication.isOneShotCommand(new String[] {
                "--spring.main.web-application-type=none", "--create-platform-admin=admin@example.az"})).isTrue();
        assertThat(TapStampApplication.isOneShotCommand(new String[] {"--provision-tenant=demo", "--domain=x.az"}))
                .isTrue();
        assertThat(TapStampApplication.isOneShotCommand(new String[] {"--provision-tenant"})).isTrue();
    }

    @Test
    void normalStartIsNotACommand() {
        assertThat(TapStampApplication.isOneShotCommand(new String[0])).isFalse();
        assertThat(TapStampApplication.isOneShotCommand(new String[] {"--server.port=8080"})).isFalse();
        assertThat(TapStampApplication.isOneShotCommand(new String[] {"--create-platform-admin-extra=x"})).isFalse();
    }
}
