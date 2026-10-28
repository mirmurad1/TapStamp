package az.tapstamp.bonus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import az.tapstamp.bonus.repository.BonusRuleMapper.BonusRule;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.i18n.LocalizedText;
import org.junit.jupiter.api.Test;

class BonusRuleServiceTest {

    private static final ZoneId BAKU = ZoneId.of("Asia/Baku");
    private static final UUID BRANCH = UUID.randomUUID();

    private static BonusRule rule(int mask, LocalTime start, LocalTime end, UUID branchId) {
        return new BonusRule(UUID.randomUUID(), LocalizedText.of("Xoşbəxt saat", null, null), 1, mask, start, end, null,
                null, branchId, true, false, null, Instant.EPOCH);
    }

    /** 2026-10-26 — bazar ertəsi. */
    private static ZonedDateTime monday(int hour, int minute) {
        return ZonedDateTime.of(2026, 10, 26, hour, minute, 0, 0, BAKU);
    }

    @Test
    void timeWindowIncludesStartAndExcludesEnd() {
        BonusRule happyHours = rule(0b11111, LocalTime.of(14, 0), LocalTime.of(17, 0), null);
        assertThat(BonusRuleService.appliesAt(happyHours, BRANCH, monday(13, 59))).isFalse();
        assertThat(BonusRuleService.appliesAt(happyHours, BRANCH, monday(14, 0))).isTrue();
        assertThat(BonusRuleService.appliesAt(happyHours, BRANCH, monday(16, 59))).isTrue();
        assertThat(BonusRuleService.appliesAt(happyHours, BRANCH, monday(17, 0))).isFalse();
    }

    @Test
    void dayAndBranchFilters() {
        BonusRule weekend = rule(0b1100000, null, null, null);
        assertThat(BonusRuleService.appliesAt(weekend, BRANCH, monday(12, 0))).isFalse();
        assertThat(BonusRuleService.appliesAt(weekend, BRANCH, monday(12, 0).plusDays(5))).isTrue();

        BonusRule oneBranch = rule(0b1111111, null, null, BRANCH);
        assertThat(BonusRuleService.appliesAt(oneBranch, BRANCH, monday(12, 0))).isTrue();
        assertThat(BonusRuleService.appliesAt(oneBranch, UUID.randomUUID(), monday(12, 0))).isFalse();
    }

    @Test
    void daysRoundTripThroughTheMask() {
        assertThat(BonusRuleService.toMask(List.of(1, 3, 7))).isEqualTo(0b1000101);
        assertThat(BonusRuleService.fromMask(0b1000101)).containsExactly(1, 3, 7);
        assertThatThrownBy(() -> BonusRuleService.toMask(List.of(8))).isInstanceOf(ApiException.class);
    }
}
