-- Hədiyyə həddi dəyişəndə yarımçıq kartlar köhnə qayda ilə bitir (qərar 45).
-- NULL — kart aktiv qaydaya tabedir; doludursa, kart bu qayda ilə tamamlanır, hədiyyədən sonra NULL olur.
ALTER TABLE loyalty_accounts ADD COLUMN reward_rule_id uuid REFERENCES reward_rules (id);
CREATE INDEX loyalty_accounts_reward_rule_idx ON loyalty_accounts (reward_rule_id) WHERE reward_rule_id IS NOT NULL;
