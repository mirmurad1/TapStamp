package az.tapstamp.company.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.company.api.PublicCompanyResponse.BranchResponse;
import az.tapstamp.company.api.PublicCompanyResponse.RewardRuleResponse;
import az.tapstamp.company.api.PublicCompanyResponse.StampRulesResponse;
import az.tapstamp.company.service.CompanyAdminService.BranchPolicy;
import az.tapstamp.company.service.CompanyAdminService.Company;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.TenantMapper;
import org.jspecify.annotations.Nullable;

/**
 * Şirkətin profili ({@code company_profile}, tək sətir), filiallar və filial üzrə möhür qaydası.
 * SQL: {@code mapper/tenant/company/CompanyMapper.xml}.
 */
@TenantMapper
public interface CompanyMapper {

    /** Müştəri girişi üçün parametrlər; {@code allowedPhoneRegions} — vergüllə ayrılmış ölkə kodları. */
    record SettingsRow(LocalizedText name, String allowedPhoneRegions, String smsSenderName, String timezone,
                       String brandColor) {
    }

    /** Filial (iş saatları JSON mətni kimi). */
    record BranchRow(UUID id, LocalizedText name, LocalizedText address, String businessHours, String status,
                     boolean policyOverridden) {
    }

    // --- Profil ---

    Optional<SettingsRow> findSettings();

    Optional<Company> findCompany();

    void updateCompany(Company company, Instant now);

    // --- Loqo ---

    /** PNG baytları və versiya (ictimai ünvanda {@code ?v=}). */
    record Logo(byte[] png, int version) {
    }

    Optional<Logo> findLogo();

    /** Loqo yoxdursa boş. */
    Optional<Integer> findLogoVersion();

    /** {@code png} — null: loqo silinir. */
    void updateLogo(byte @Nullable [] png, Instant now);

    // --- İctimai məlumat (müştəri tətbiqi) ---

    List<BranchResponse> activeBranches();

    Optional<RewardRuleResponse> activeRewardRule();

    Optional<StampRulesResponse> companyStampRules();

    // --- Filiallar ---

    List<BranchRow> branches();

    int countOtherActiveBranches(UUID excludedId);

    void insertBranch(UUID id, LocalizedText name, LocalizedText address, String businessHours, Instant now);

    /**
     * {@code null} sahələr dəyişmir; {@code clearHours} — iş saatları silinir (hər zaman açıq).
     * {@code businessHours} — JSON mətni.
     */
    void updateBranch(UUID id, LocalizedText name, LocalizedText address, String businessHours, boolean clearHours,
                      String status, Instant now);

    // --- Filialın qaydası ---

    /** Filialın öz qaydası, yoxdursa şirkətin standartı ({@code overridden = false}). */
    BranchPolicy branchPolicy(UUID branchId);

    void upsertBranchPolicy(UUID id, UUID branchId, BranchPolicy policy, UUID staffId, Instant now);

    int deleteBranchPolicy(UUID branchId);
}
