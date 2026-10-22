package az.tapstamp.company.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.company.repository.CompanyMapper;
import az.tapstamp.plan.PlanService;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Şirkətin məzmunu (3 dildə ad və təsvir, saat qurşağı, standart dil, brend rəngi, SMS göndərən adı), filiallar
 * (ad, ünvan, iş saatları, status) və filial üzrə sui-istifadə qaydasının override-ı. Yalnız sahib; hər dəyişiklik
 * auditə yazılır.
 */
@Service
public class CompanyAdminService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> DAYS = Set.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");

    public record Company(LocalizedText name, LocalizedText description, String timezone, String defaultLanguage,
                          String brandColor, String smsSenderName) {
    }

    public record BranchView(UUID id, LocalizedText name, LocalizedText address, Map<String, List<List<String>>> businessHours,
                             String status, boolean policyOverridden) {
    }

    /** Filialın qaydası: {@code overridden=false} olduqda şirkətin standart qaydası göstərilir. */
    public record BranchPolicy(boolean overridden, int minIntervalMinutes, int maxStampsPerDay,
                               boolean requireStaffApproval, int pendingTimeoutMinutes, boolean enforceBusinessHours) {
    }

    private final CompanyMapper companies;
    private final AuditLogger audit;
    private final Clock clock;
    private final PlanService plans;

    public CompanyAdminService(CompanyMapper companies, AuditLogger audit, Clock clock, PlanService plans) {
        this.companies = companies;
        this.audit = audit;
        this.clock = clock;
        this.plans = plans;
    }

    // ------------------------------------------------------------------ Şirkət

    public Company company(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        return companies.findCompany().orElseThrow(() -> new IllegalStateException("company_profile is missing"));
    }

    public Company updateCompany(AdminPrincipal principal, Company company) {
        AccessPolicy.requireCompanyWide(principal);
        validateZone(company.timezone());
        if (trim(company.name()) == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (!List.of("az", "en", "ru").contains(company.defaultLanguage())
                || company.brandColor() == null || !company.brandColor().matches("^#[0-9A-Fa-f]{6}$")
                || company.smsSenderName() == null
                || !company.smsSenderName().strip().matches("^[A-Za-z0-9 ]{1,11}$")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        companies.updateCompany(new Company(trim(company.name()), trim(company.description()), company.timezone(),
                company.defaultLanguage(), company.brandColor().toUpperCase(java.util.Locale.ROOT),
                company.smsSenderName().strip()), clock.instant());
        audit.tenant("COMPANY_PROFILE_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "company", null,
                Map.of("timezone", company.timezone(), "defaultLanguage", company.defaultLanguage(),
                        "brandColor", company.brandColor()));
        return company(principal);
    }

    // ------------------------------------------------------------------ Filiallar

    public List<BranchView> branches(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        return companies.branches().stream()
                .map(row -> new BranchView(row.id(), row.name(), row.address(), parseHours(row.businessHours()),
                        row.status(), row.policyOverridden()))
                .toList();
    }

    public BranchView createBranch(AdminPrincipal principal, LocalizedText name, LocalizedText address,
                                   Map<String, List<List<String>>> hours) {
        AccessPolicy.requireCompanyWide(principal);
        if (trim(name) == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        plans.requireBranchCapacity(activeBranches());
        UUID id = Ids.newId();
        Instant now = clock.instant();
        companies.insertBranch(id, trim(name), trim(address), hoursJson(hours), now);
        audit.tenant("BRANCH_CREATED", AuditLogger.ActorType.STAFF, principal.userId(), "branch", id, Map.of());
        return branch(principal, id);
    }

    /** {@code null} sahələr dəyişmir; boş iş saatları ({@code {}}) — qrafik silinir (hər zaman açıq). */
    public BranchView updateBranch(AdminPrincipal principal, UUID branchId, LocalizedText name, LocalizedText address,
                                   Map<String, List<List<String>>> hours, boolean clearHours, String status) {
        AccessPolicy.requireCompanyWide(principal);
        BranchView current = branch(principal, branchId);
        if (status != null && !List.of("ACTIVE", "CLOSED").contains(status)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if ("ACTIVE".equals(status) && !"ACTIVE".equals(current.status())) {
            plans.requireBranchCapacity(activeBranches());
        }
        if ("CLOSED".equals(status)) {
            int active = companies.countOtherActiveBranches(branchId);
            if (active == 0) {
                throw new ApiException(ErrorCode.FORBIDDEN);
            }
        }
        companies.updateBranch(branchId, name == null ? null : trim(name), address == null ? null : trim(address),
                hours == null ? null : hoursJson(hours), clearHours, status, clock.instant());
        Map<String, Object> details = new LinkedHashMap<>();
        if (status != null) {
            details.put("status", status);
        }
        audit.tenant("BRANCH_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "branch", branchId, details);
        return branch(principal, branchId);
    }

    /** Paketin filial limiti aktiv filiallara görə yoxlanır (bağlanmış filial sayılmır). */
    private long activeBranches() {
        return companies.branches().stream().filter(row -> "ACTIVE".equals(row.status())).count();
    }

    // ------------------------------------------------------------------ Filialın qaydası

    public BranchPolicy branchPolicy(AdminPrincipal principal, UUID branchId) {
        branch(principal, branchId);
        return companies.branchPolicy(branchId);
    }

    /** Filial üçün ayrıca qayda (şirkət standartını əvəz edir). Cooldown yenə də bütün filiallar üzrə hesablanır. */
    public BranchPolicy overrideBranchPolicy(AdminPrincipal principal, UUID branchId, BranchPolicy policy) {
        branch(principal, branchId);
        companies.upsertBranchPolicy(Ids.newId(), branchId, policy, principal.userId(), clock.instant());
        audit.tenant("BRANCH_POLICY_OVERRIDDEN", AuditLogger.ActorType.STAFF, principal.userId(), "branch", branchId,
                Map.of("minIntervalMinutes", policy.minIntervalMinutes(), "maxStampsPerDay", policy.maxStampsPerDay(),
                        "requireStaffApproval", policy.requireStaffApproval(),
                        "enforceBusinessHours", policy.enforceBusinessHours()));
        return branchPolicy(principal, branchId);
    }

    /** Override silinir — filial yenidən şirkətin standart qaydası ilə işləyir. */
    public BranchPolicy resetBranchPolicy(AdminPrincipal principal, UUID branchId) {
        branch(principal, branchId);
        int removed = companies.deleteBranchPolicy(branchId);
        if (removed > 0) {
            audit.tenant("BRANCH_POLICY_RESET", AuditLogger.ActorType.STAFF, principal.userId(), "branch", branchId, Map.of());
        }
        return branchPolicy(principal, branchId);
    }

    // ------------------------------------------------------------------

    private BranchView branch(AdminPrincipal principal, UUID branchId) {
        return branches(principal).stream().filter(b -> b.id().equals(branchId)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private static void validateZone(String zone) {
        try {
            Objects.requireNonNull(ZoneId.of(zone == null ? "" : zone));
        } catch (DateTimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /** İş saatları: {"mon": [["08:00","22:00"]], ...}; gecəyarısını keçən interval (22:00–02:00) qəbul olunur. */
    static String hoursJson(Map<String, List<List<String>>> hours) {
        if (hours == null || hours.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, List<List<String>>> day : hours.entrySet()) {
            if (!DAYS.contains(day.getKey()) || day.getValue() == null || day.getValue().size() > 4) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED);
            }
            for (List<String> interval : day.getValue()) {
                if (interval == null || interval.size() != 2 || !interval.stream().allMatch(CompanyAdminService::isTime)
                        || interval.get(0).equals(interval.get(1))) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED);
                }
            }
        }
        return JSON.writeValueAsString(hours);
    }

    private static boolean isTime(String value) {
        if (value == null || !value.matches("^\\d{2}:\\d{2}$")) {
            return false;
        }
        try {
            // Yalnız yoxlama: format düzgün olsa da, saat mövcud olmalıdır (məs. 25:00 rədd edilir)
            LocalTime.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static Map<String, List<List<String>>> parseHours(String json) {
        return json == null ? null : JSON.readValue(json, new TypeReference<>() {
        });
    }

    private static LocalizedText trim(LocalizedText text) {
        if (text == null || text.az() == null || text.az().isBlank()) {
            return null;
        }
        return LocalizedText.of(text.az().strip(), blankToNull(text.en()), blankToNull(text.ru()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
