package az.cafeloyalty.loyalty.rule.domain;

import java.time.Instant;
import java.util.UUID;

import az.cafeloyalty.shared.i18n.LocalizedText;
import az.cafeloyalty.shared.persistence.BaseEntity;
import az.cafeloyalty.shared.persistence.LocalizedTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Mükafat qaydası (məs. 9 möhür → 10-cu qəhvə pulsuz). Qayda dəyişəndə yeni versiya yaradılır,
 * köhnəsi dəyişdirilmir — qazanılmış mükafatlar öz snapshot-larını saxlayır.
 */
@Entity
@Table(name = "reward_rules")
public class RewardRule extends BaseEntity {

    @Column(name = "rule_version", nullable = false, updatable = false)
    private int ruleVersion;

    @Column(name = "stamps_required", nullable = false, updatable = false)
    private int stampsRequired;

    @Convert(converter = LocalizedTextConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "reward_name", nullable = false, updatable = false)
    private LocalizedText rewardName;

    @Convert(converter = LocalizedTextConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "reward_description", updatable = false)
    private LocalizedText rewardDescription;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RewardRule() {
    }

    public RewardRule(int ruleVersion, int stampsRequired, LocalizedText rewardName, LocalizedText rewardDescription,
                      UUID createdBy, Instant now) {
        super(true);
        this.ruleVersion = ruleVersion;
        this.stampsRequired = stampsRequired;
        this.rewardName = rewardName;
        this.rewardDescription = rewardDescription;
        this.active = true;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public int getRuleVersion() {
        return ruleVersion;
    }

    public int getStampsRequired() {
        return stampsRequired;
    }

    public LocalizedText getRewardName() {
        return rewardName;
    }

    public LocalizedText getRewardDescription() {
        return rewardDescription;
    }

    public boolean isActive() {
        return active;
    }
}
