package az.tapstamp.tier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.tier.repository.TierMapper.Tier;
import org.junit.jupiter.api.Test;

class TierServiceTest {

    private static Tier tier(String name, int minVisits) {
        return new Tier(UUID.randomUUID(), LocalizedText.of(name, null, null), "#CD7F32", minVisits, 0, false, 0);
    }

    private static final List<Tier> TIERS = List.of(tier("Bürünc", 3), tier("Gümüş", 10), tier("Qızıl", 25));

    @Test
    void highestReachedTierWins() {
        assertThat(TierService.tierFor(TIERS, 2)).isNull();
        assertThat(TierService.tierFor(TIERS, 3).name().az()).isEqualTo("Bürünc");
        assertThat(TierService.tierFor(TIERS, 24).name().az()).isEqualTo("Gümüş");
        assertThat(TierService.tierFor(TIERS, 100).name().az()).isEqualTo("Qızıl");
    }

    @Test
    void nextTierIsTheFirstNotReached() {
        assertThat(TierService.nextAfter(TIERS, 0).name().az()).isEqualTo("Bürünc");
        assertThat(TierService.nextAfter(TIERS, 10).name().az()).isEqualTo("Qızıl");
        assertThat(TierService.nextAfter(TIERS, 25)).isNull();
    }
}
