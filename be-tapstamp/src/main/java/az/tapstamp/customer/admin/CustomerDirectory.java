package az.tapstamp.customer.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import az.tapstamp.company.service.CompanySettings;
import az.tapstamp.customer.phone.PhoneNumberService;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.admin.BranchScope;
import az.tapstamp.security.crypto.PhoneHasher;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Admin paneli üçün müştəri sorğuları: siyahı (axtarış, filtrlər, sıralama, səhifələmə), detallar, zaman xətti və CSV
 * üçün sətirlər. Tam telefon nömrəsi heç vaxt qaytarılmır (yalnız maskalanmış). Menecer yalnız öz filiallarında
 * möhürü olan müştəriləri görür; başqa müştəri onun üçün "mövcud deyil" (404).
 */
@Service
public class CustomerDirectory {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int TIMELINE_PAGE = 50;

    /** Sıralama sahələri (ağ siyahı — SQL-ə yalnız bu ifadələr düşür). */
    public enum Sort {
        REGISTERED_AT("c.registered_at"),
        LAST_VISIT_AT("c.last_visit_at"),
        NAME("lower(c.name)"),
        TOTAL_STAMPS("coalesce(a.total_stamps, 0)");

        final String expression;

        Sort(String expression) {
            this.expression = expression;
        }
    }

    public record Filter(String search, String language, String status, Integer inactiveDays, UUID branchId,
                         LocalDate registeredFrom, LocalDate registeredTo, Sort sort, boolean descending) {
    }

    public record Row(UUID id, String name, String phoneMasked, String language, String status, Instant registeredAt,
                      Instant lastVisitAt, int currentStamps, int totalStamps, int rewardsEarned, int rewardsRedeemed,
                      int openFraudFlags) {
    }

    public record Page(List<Row> items, long total, int page, int size) {
    }

    /** Zaman xəttinin elementi: möhür (status, mənbə, səbəb) və ya hədiyyə kartı (status, ad). */
    public record TimelineItem(UUID id, String kind, String status, String source, UUID branchId, String reason,
                               String rewardName, Instant at, Instant redeemedAt) {
    }

    private static final Pattern PHONE_LIKE = Pattern.compile("^[+\\d][\\d\\s()-]{6,}$");

    private final JdbcClient jdbc;
    private final PhoneNumberService phoneNumbers;
    private final PhoneHasher phoneHasher;
    private final CompanySettings company;
    private final Clock clock;

    public CustomerDirectory(@Qualifier("tenantJdbc") JdbcClient jdbc, PhoneNumberService phoneNumbers,
                             PhoneHasher phoneHasher, CompanySettings company, Clock clock) {
        this.jdbc = jdbc;
        this.phoneNumbers = phoneNumbers;
        this.phoneHasher = phoneHasher;
        this.company = company;
        this.clock = clock;
    }

    public Page list(AdminPrincipal principal, Filter filter, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        Map<String, Object> params = filterParams(principal, filter);
        if (params == null) {
            return new Page(List.of(), 0, page, size);
        }
        params.put("limit", size);
        params.put("offset", (long) page * size);
        Sort sort = filter.sort() == null ? Sort.REGISTERED_AT : filter.sort();
        long[] total = {0};
        List<Row> rows = jdbc.sql(SELECT + "\n" + WHERE + " ORDER BY " + sort.expression
                        + (filter.descending() ? " DESC NULLS LAST" : " ASC NULLS LAST") + ", c.id LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, n) -> {
                    total[0] = rs.getLong("total_count");
                    return map(rs);
                })
                .list();
        return new Page(rows, total[0], page, size);
    }

    /** CSV ixracı üçün: filtrə uyğun bütün sətirlər axınla (yaddaşa yığılmadan) emal olunur. */
    public void forEach(AdminPrincipal principal, Filter filter, Consumer<Row> consumer) {
        Map<String, Object> params = filterParams(principal, filter);
        if (params == null) {
            return;
        }
        jdbc.sql(SELECT + "\n" + WHERE + " ORDER BY c.registered_at DESC, c.id")
                .params(params)
                .query(rs -> {
                    consumer.accept(map(rs));
                });
    }

    public Row get(AdminPrincipal principal, UUID customerId) {
        Map<String, Object> params = scopeParams(BranchScope.of(principal, null));
        params.put("id", customerId);
        return jdbc.sql(SELECT + " WHERE c.id = :id AND c.status <> 'DELETED' AND " + SCOPE)
                .params(params)
                .query((rs, n) -> map(rs))
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** Möhürlər və hədiyyə kartları, yenidən köhnəyə; {@code before} — əvvəlki səhifənin son elementinin vaxtı. */
    public List<TimelineItem> timeline(AdminPrincipal principal, UUID customerId, Instant before) {
        get(principal, customerId);
        return jdbc.sql("""
                        SELECT * FROM (
                            SELECT s.id, 'STAMP' AS kind, s.status, s.source, s.branch_id,
                                   coalesce(s.revoke_reason, s.reason) AS reason, NULL::text AS reward_name,
                                   s.created_at AS at, NULL::timestamptz AS redeemed_at
                            FROM stamps s WHERE s.customer_id = :id
                            UNION ALL
                            SELECT r.id, 'REWARD', r.status, NULL, r.redeemed_branch_id, NULL, r.reward_name_snapshot::text,
                                   r.earned_at, r.redeemed_at
                            FROM rewards r WHERE r.customer_id = :id
                        ) t
                        WHERE CAST(:before AS timestamptz) IS NULL OR t.at < CAST(:before AS timestamptz)
                        ORDER BY t.at DESC, t.id DESC LIMIT :limit""")
                .param("id", customerId)
                .param("before", before == null ? null : before.atOffset(ZoneOffset.UTC))
                .param("limit", TIMELINE_PAGE)
                .query((rs, n) -> new TimelineItem(rs.getObject("id", UUID.class), rs.getString("kind"),
                        rs.getString("status"), rs.getString("source"), rs.getObject("branch_id", UUID.class),
                        rs.getString("reason"), rs.getString("reward_name"), instant(rs, "at"), instant(rs, "redeemed_at")))
                .list();
    }

    // ------------------------------------------------------------------ SQL

    private static final String SELECT = """
            SELECT c.id, c.name, c.phone_masked, c.preferred_language, c.status, c.registered_at, c.last_visit_at,
                   coalesce(a.current_stamps, 0) AS current_stamps, coalesce(a.total_stamps, 0) AS total_stamps,
                   coalesce(a.rewards_earned, 0) AS rewards_earned, coalesce(a.rewards_redeemed, 0) AS rewards_redeemed,
                   (SELECT count(*) FROM fraud_flags f WHERE f.customer_id = c.id AND f.resolved_at IS NULL) AS open_flags,
                   count(*) OVER () AS total_count
            FROM customers c LEFT JOIN loyalty_accounts a ON a.customer_id = c.id""";

    private static final String SCOPE = """
            (:all OR EXISTS (SELECT 1 FROM stamps s WHERE s.customer_id = c.id AND s.branch_id IN (:branches)))""";

    private static final String WHERE = "WHERE c.status <> 'DELETED' AND " + SCOPE + "\n" + """
              AND (CAST(:phoneHash AS bytea) IS NULL OR c.phone_hash = CAST(:phoneHash AS bytea))
              AND (CAST(:name AS text) IS NULL OR c.name ILIKE CAST(:name AS text))
              AND (CAST(:lang AS text) IS NULL OR c.preferred_language = CAST(:lang AS text))
              AND (CAST(:status AS text) IS NULL OR c.status = CAST(:status AS text))
              AND (CAST(:inactiveBefore AS timestamptz) IS NULL
                   OR coalesce(c.last_visit_at, c.registered_at) < CAST(:inactiveBefore AS timestamptz))
              AND (CAST(:regFrom AS date) IS NULL OR (c.registered_at AT TIME ZONE :tz)::date >= CAST(:regFrom AS date))
              AND (CAST(:regTo AS date) IS NULL OR (c.registered_at AT TIME ZONE :tz)::date <= CAST(:regTo AS date))""";

    /** Filtr parametrləri; telefon kimi axtarış etibarsızdırsa {@code null} (nəticə boşdur). */
    private Map<String, Object> filterParams(AdminPrincipal principal, Filter filter) {
        Map<String, Object> params = scopeParams(BranchScope.of(principal, filter.branchId()));
        byte[] phoneHash = null;
        String name = null;
        String search = filter.search() == null ? "" : filter.search().strip();
        if (!search.isEmpty()) {
            if (PHONE_LIKE.matcher(search).matches()) {
                try {
                    String phone = phoneNumbers.normalize(search, company.current().allowedPhoneRegions());
                    phoneHash = phoneHasher.hash(TenantContext.require().id(), phone);
                } catch (ApiException invalid) {
                    return null;
                }
            } else {
                name = "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            }
        }
        if (filter.status() != null && !List.of("ACTIVE", "BLOCKED").contains(filter.status())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (filter.language() != null && !List.of("az", "en", "ru").contains(filter.language())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (filter.inactiveDays() != null && (filter.inactiveDays() < 1 || filter.inactiveDays() > 3650)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        params.put("phoneHash", phoneHash);
        params.put("name", name);
        params.put("lang", filter.language() == null ? null : filter.language().toLowerCase(Locale.ROOT));
        params.put("status", filter.status());
        params.put("inactiveBefore", filter.inactiveDays() == null ? null
                : clock.instant().minus(filter.inactiveDays(), ChronoUnit.DAYS).atOffset(ZoneOffset.UTC));
        params.put("regFrom", filter.registeredFrom());
        params.put("regTo", filter.registeredTo());
        params.put("tz", company.current().timezone().getId());
        return params;
    }

    private static Map<String, Object> scopeParams(BranchScope scope) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("all", scope.all());
        params.put("branches", scope.branchIds());
        return params;
    }

    private static Row map(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("phone_masked"),
                rs.getString("preferred_language"), rs.getString("status"), instant(rs, "registered_at"),
                instant(rs, "last_visit_at"), rs.getInt("current_stamps"), rs.getInt("total_stamps"),
                rs.getInt("rewards_earned"), rs.getInt("rewards_redeemed"), rs.getInt("open_flags"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
