package az.tapstamp.loyalty.rule.repository;

import java.util.Optional;
import java.util.UUID;

import az.tapstamp.loyalty.rule.domain.RewardRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardRuleRepository extends JpaRepository<RewardRule, UUID> {

    Optional<RewardRule> findByActiveTrue();
}
