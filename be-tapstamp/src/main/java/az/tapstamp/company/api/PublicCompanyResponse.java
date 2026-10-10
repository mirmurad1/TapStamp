package az.tapstamp.company.api;

import java.util.List;
import java.util.UUID;

import az.tapstamp.shared.i18n.LocalizedText;
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
        @Schema(description = "Brend rəngi (#RRGGBB): müştəri tətbiqinin əsas rəngi") String brandColor,
        @Schema(description = "Loqonun ünvanı (versiyalı); yüklənməyibsə null — standart ikon göstərilir")
        String logoUrl,
        List<BranchResponse> branches,
        @Schema(description = "Aktiv mükafat qaydası; təyin olunmayıbsa null") RewardRuleResponse rewardRule,
        @Schema(description = "Şirkətin möhür qaydası (istifadə şərtlərində göstərilir)") StampRulesResponse stampRules) {

    /** Möhür qaydası: iki möhür arasında ən az dəqiqə (0 — limitsiz) və gündə ən çox möhür. */
    public record StampRulesResponse(int minIntervalMinutes, int maxStampsPerDay) {
    }

    public record BranchResponse(UUID id, LocalizedText name, LocalizedText address, LocalizedText description) {
    }

    public record RewardRuleResponse(
            @Schema(description = "Hədiyyə üçün lazım olan möhür sayı") int stampsRequired,
            LocalizedText rewardName,
            LocalizedText rewardDescription) {
    }
}
