package az.cafeloyalty.company.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import az.cafeloyalty.audit.AuditLogger;
import az.cafeloyalty.security.admin.AccessPolicy;
import az.cafeloyalty.security.admin.AdminPrincipal;
import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import az.cafeloyalty.shared.i18n.LocalizedText;
import az.cafeloyalty.shared.id.Ids;
import az.cafeloyalty.shared.persistence.LocalizedTextConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
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

    private static final LocalizedTextConverter TEXT = new LocalizedTextConverter();
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

    private final JdbcClient jdbc;
    private final AuditLogger audit;
    private final Clock clock;

    public CompanyAdminService(@Qualifier("tenantJdbc") JdbcClient jdbc, AuditLogger audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ Şirkət

    public Company company(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        return jdbc.sql("""
                        SELECT name::text AS name, description::text AS description, timezone, default_language,
                               brand_color, sms_sender_name FROM company_profile WHERE singleton""")
                .query((rs, n) -> new Company(TEXT.convertToEntityAttribute(rs.getString("name")),
                        TEXT.convertToEntityAttribute(rs.getString("description")), rs.getString("timezone"),
                        rs.getString("default_language"), rs.getString("brand_color"), rs.getString("sms_sender_name")))
                .single();
    }

    public Company updateCompany(AdminPrincipal principal, Company company) {
        AccessPolicy.requireCompanyWide(principal);
        validateZone(company.timezone());
        if (trim(company.name()) == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (!List.of("az", "en", "ru").contains(company.defaultLanguage())
                || company.brandColor() == null || !company.brandColor().matches("^#[0-9A-Fa-f]{6}$")
                || (company.smsSenderName() != null && !company.smsSenderName().isBlank()
                    && !company.smsSenderName().matches("^[A-Za-z0-9 ]{1,11}$"))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        jdbc.sql("""
                        UPDATE company_profile
                           SET name = CAST(:name AS jsonb), description = CAST(:description AS jsonb), timezone = :tz,
                               default_language = :lang, brand_color = :color, sms_sender_name = :sender,
                               updated_at = :now, version = version + 1
                         WHERE singleton""")
                .param("name", TEXT.convertToDatabaseColumn(trim(company.name())))
                .param("description", TEXT.convertToDatabaseColumn(trim(company.description())))
                .param("tz", company.timezone())
                .param("lang", company.defaultLanguage())
                .param("color", company.brandColor().toUpperCase(java.util.Locale.ROOT))
                .param("sender", company.smsSenderName() == null || company.smsSenderName().isBlank()
                        ? null : company.smsSenderName().strip())
                .param("now", ts(clock.instant()))
                .update();
        audit.tenant("COMPANY_PROFILE_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "company", null,
                Map.of("timezone", company.timezone(), "defaultLanguage", company.defaultLanguage(),
                        "brandColor", company.brandColor()));
        return company(principal);
    }

    // ------------------------------------------------------------------ Filiallar

    public List<BranchView> branches(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        return jdbc.sql("""
                        SELECT b.id, b.name::text AS name, b.address::text AS address, b.business_hours::text AS hours,
                               b.status, EXISTS (SELECT 1 FROM stamp_policies p WHERE p.branch_id = b.id) AS overridden
                        FROM branches b ORDER BY b.created_at""")
                .query((rs, n) -> new BranchView(rs.getObject("id", UUID.class),
                        TEXT.convertToEntityAttribute(rs.getString("name")),
                        TEXT.convertToEntityAttribute(rs.getString("address")), parseHours(rs.getString("hours")),
                        rs.getString("status"), rs.getBoolean("overridden")))
                .list();
    }

    public BranchView createBranch(AdminPrincipal principal, LocalizedText name, LocalizedText address,
                                   Map<String, List<List<String>>> hours) {
        AccessPolicy.requireCompanyWide(principal);
        if (trim(name) == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        UUID id = Ids.newId();
        Instant now = clock.instant();
        jdbc.sql("""
                        INSERT INTO branches (id, name, address, business_hours, status, created_at, updated_at)
                        VALUES (:id, CAST(:name AS jsonb), CAST(:address AS jsonb), CAST(:hours AS jsonb), 'ACTIVE', :now, :now)""")
                .param("id", id)
                .param("name", TEXT.convertToDatabaseColumn(trim(name)))
                .param("address", TEXT.convertToDatabaseColumn(trim(address)))
                .param("hours", hoursJson(hours))
                .param("now", ts(now))
                .update();
        audit.tenant("BRANCH_CREATED", AuditLogger.ActorType.STAFF, principal.userId(), "branch", id, Map.of());
        return branch(principal, id);
    }

    /** {@code null} sahələr dəyişmir; boş iş saatları ({@code {}}) — qrafik silinir (hər zaman açıq). */
    public BranchView updateBranch(AdminPrincipal principal, UUID branchId, LocalizedText name, LocalizedText address,
                                   Map<String, List<List<String>>> hours, boolean clearHours, String status) {
        AccessPolicy.requireCompanyWide(principal);
        branch(principal, branchId);
        if (status != null && !List.of("ACTIVE", "CLOSED").contains(status)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if ("CLOSED".equals(status)) {
            int active = jdbc.sql("SELECT count(*) FROM branches WHERE status = 'ACTIVE' AND id <> :id")
                    .param("id", branchId).query(Integer.class).single();
            if (active == 0) {
                throw new ApiException(ErrorCode.FORBIDDEN);
            }
        }
        jdbc.sql("""
                        UPDATE branches
                           SET name = COALESCE(CAST(:name AS jsonb), name),
                               address = CASE WHEN CAST(:address AS jsonb) IS NULL THEN address ELSE CAST(:address AS jsonb) END,
                               business_hours = CASE WHEN :clearHours THEN NULL
                                                     WHEN CAST(:hours AS jsonb) IS NULL THEN business_hours
                                                     ELSE CAST(:hours AS jsonb) END,
                               status = COALESCE(:status, status), updated_at = :now, version = version + 1
                         WHERE id = :id""")
                .param("name", name == null ? null : TEXT.convertToDatabaseColumn(trim(name)))
                .param("address", address == null ? null : TEXT.convertToDatabaseColumn(trim(address)))
                .param("hours", hours == null ? null : hoursJson(hours))
                .param("clearHours", clearHours)
                .param("status", status)
                .param("now", ts(clock.instant()))
                .param("id", branchId)
                .update();
        Map<String, Object> details = new LinkedHashMap<>();
        if (status != null) {
            details.put("status", status);
        }
        audit.tenant("BRANCH_UPDATED", AuditLogger.ActorType.STAFF, principal.userId(), "branch", branchId, details);
        return branch(principal, branchId);
    }

    // ------------------------------------------------------------------ Filialın qaydası

    public BranchPolicy branchPolicy(AdminPrincipal principal, UUID branchId) {
        branch(principal, branchId);
        return jdbc.sql("""
                        SELECT branch_id IS NOT NULL AS overridden, min_interval_minutes, max_stamps_per_day,
                               require_staff_approval, pending_timeout_minutes, enforce_business_hours
                        FROM stamp_policies WHERE branch_id = :id OR branch_id IS NULL
                        ORDER BY branch_id NULLS LAST LIMIT 1""")
                .param("id", branchId)
                .query((rs, n) -> new BranchPolicy(rs.getBoolean("overridden"), rs.getInt("min_interval_minutes"),
                        rs.getInt("max_stamps_per_day"), rs.getBoolean("require_staff_approval"),
                        rs.getInt("pending_timeout_minutes"), rs.getBoolean("enforce_business_hours")))
                .single();
    }

    /** Filial üçün ayrıca qayda (şirkət standartını əvəz edir). Cooldown yenə də bütün filiallar üzrə hesablanır. */
    public BranchPolicy overrideBranchPolicy(AdminPrincipal principal, UUID branchId, BranchPolicy policy) {
        branch(principal, branchId);
        jdbc.sql("""
                        INSERT INTO stamp_policies (id, branch_id, min_interval_minutes, max_stamps_per_day,
                                                    require_staff_approval, pending_timeout_minutes,
                                                    enforce_business_hours, updated_by, updated_at)
                        VALUES (:id, :branchId, :interval, :max, :approval, :timeout, :hours, :staff, :now)
                        ON CONFLICT (branch_id) DO UPDATE
                           SET min_interval_minutes = EXCLUDED.min_interval_minutes,
                               max_stamps_per_day = EXCLUDED.max_stamps_per_day,
                               require_staff_approval = EXCLUDED.require_staff_approval,
                               pending_timeout_minutes = EXCLUDED.pending_timeout_minutes,
                               enforce_business_hours = EXCLUDED.enforce_business_hours,
                               updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at,
                               version = stamp_policies.version + 1""")
                .param("id", Ids.newId())
                .param("branchId", branchId)
                .param("interval", policy.minIntervalMinutes())
                .param("max", policy.maxStampsPerDay())
                .param("approval", policy.requireStaffApproval())
                .param("timeout", policy.pendingTimeoutMinutes())
                .param("hours", policy.enforceBusinessHours())
                .param("staff", principal.userId())
                .param("now", ts(clock.instant()))
                .update();
        audit.tenant("BRANCH_POLICY_OVERRIDDEN", AuditLogger.ActorType.STAFF, principal.userId(), "branch", branchId,
                Map.of("minIntervalMinutes", policy.minIntervalMinutes(), "maxStampsPerDay", policy.maxStampsPerDay(),
                        "requireStaffApproval", policy.requireStaffApproval(),
                        "enforceBusinessHours", policy.enforceBusinessHours()));
        return branchPolicy(principal, branchId);
    }

    /** Override silinir — filial yenidən şirkətin standart qaydası ilə işləyir. */
    public BranchPolicy resetBranchPolicy(AdminPrincipal principal, UUID branchId) {
        branch(principal, branchId);
        int removed = jdbc.sql("DELETE FROM stamp_policies WHERE branch_id = :id").param("id", branchId).update();
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
            ZoneId.of(zone == null ? "" : zone);
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
            LocalTime.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static Map<String, List<List<String>>> parseHours(String json) {
        return json == null ? null : JSON.readValue(json, new TypeReference<Map<String, List<List<String>>>>() {
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

    private static java.time.OffsetDateTime ts(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
