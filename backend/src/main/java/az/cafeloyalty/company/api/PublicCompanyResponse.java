package az.cafeloyalty.company.api;

import java.util.List;
import java.util.UUID;

import az.cafeloyalty.company.domain.Branch;
import az.cafeloyalty.loyalty.rule.domain.RewardRule;
import az.cafeloyalty.shared.i18n.LocalizedText;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kafenin ictimai məlumatı. Mətnlər 3 dildə birlikdə qaytarılır ki, frontend dili dəyişəndə yenidən
 * sorğu göndərmək lazım olmasın; tərcümə yoxdursa az versiyası göstərilir.
 */
@Schema(description = "Kafenin ictimai məlumatı: ad, təsvir, filiallar və mükafat qaydası")
public record PublicCompanyResponse(
        LocalizedText name,
        LocalizedText description,
        @Schema(description = "IANA saat qurşağı, məs. Asia/Baku") String timezone,
        @Schema(description = "Şirkətin standart dili: az, en və ya ru") String defaultLanguage,
        List<BranchResponse> branches,
        @Schema(description = "Aktiv mükafat qaydası; təyin olunmayıbsa null") RewardRuleResponse rewardRule) {

    public record BranchResponse(UUID id, LocalizedText name, LocalizedText address, LocalizedText description) {
        public static BranchResponse from(Branch branch) {
            return new BranchResponse(branch.getId(), branch.getName(), branch.getAddress(), branch.getDescription());
        }
    }

    public record RewardRuleResponse(
            @Schema(description = "Hədiyyə üçün lazım olan möhür sayı") int stampsRequired,
            LocalizedText rewardName,
            LocalizedText rewardDescription) {
        public static RewardRuleResponse from(RewardRule rule) {
            return new RewardRuleResponse(rule.getStampsRequired(), rule.getRewardName(), rule.getRewardDescription());
        }
    }
}
