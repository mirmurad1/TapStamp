package az.cafeloyalty.company.service;

import java.util.List;

import az.cafeloyalty.company.api.PublicCompanyResponse;
import az.cafeloyalty.company.domain.BranchStatus;
import az.cafeloyalty.company.repository.BranchRepository;
import az.cafeloyalty.company.repository.CompanyProfileRepository;
import az.cafeloyalty.loyalty.rule.repository.RewardRuleRepository;
import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kafenin ictimai məlumatı (müştəri tətbiqi və PWA manifesti üçün). */
@Service
public class CompanyQueryService {

    private final CompanyProfileRepository profiles;
    private final BranchRepository branches;
    private final RewardRuleRepository rewardRules;
    private final JdbcClient jdbc;

    public CompanyQueryService(CompanyProfileRepository profiles, BranchRepository branches,
                               RewardRuleRepository rewardRules, @Qualifier("tenantJdbc") JdbcClient jdbc) {
        this.profiles = profiles;
        this.branches = branches;
        this.rewardRules = rewardRules;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PublicCompanyResponse publicCompany() {
        var profile = profiles.findProfile().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        List<PublicCompanyResponse.BranchResponse> activeBranches = branches
                .findByStatusOrderByCreatedAtAsc(BranchStatus.ACTIVE).stream()
                .map(PublicCompanyResponse.BranchResponse::from)
                .toList();
        var rule = rewardRules.findByActiveTrue().map(PublicCompanyResponse.RewardRuleResponse::from).orElse(null);
        var stampRules = jdbc.sql("""
                        SELECT min_interval_minutes, max_stamps_per_day FROM stamp_policies WHERE branch_id IS NULL""")
                .query((rs, n) -> new PublicCompanyResponse.StampRulesResponse(rs.getInt("min_interval_minutes"),
                        rs.getInt("max_stamps_per_day")))
                .optional()
                .orElse(null);
        return new PublicCompanyResponse(profile.getName(), profile.getDescription(), profile.getTimezone(),
                profile.getDefaultLanguage(), profile.getBrandColor(), activeBranches, rule, stampRules);
    }
}
