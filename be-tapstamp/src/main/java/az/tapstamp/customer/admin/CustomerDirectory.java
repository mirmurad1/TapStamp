package az.tapstamp.customer.admin;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import az.tapstamp.company.service.CompanySettings;
import az.tapstamp.customer.phone.PhoneNumberService;
import az.tapstamp.customer.repository.CustomerDirectoryMapper.Query;
import az.tapstamp.customer.repository.CustomerDirectoryMapper;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.admin.BranchScope;
import az.tapstamp.security.crypto.PhoneHasher;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.tenancy.TenantContext;
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

    private final CustomerDirectoryMapper customers;
    private final PhoneNumberService phoneNumbers;
    private final PhoneHasher phoneHasher;
    private final CompanySettings company;
    private final Clock clock;

    public CustomerDirectory(CustomerDirectoryMapper customers, PhoneNumberService phoneNumbers,
                             PhoneHasher phoneHasher, CompanySettings company, Clock clock) {
        this.customers = customers;
        this.phoneNumbers = phoneNumbers;
        this.phoneHasher = phoneHasher;
        this.company = company;
        this.clock = clock;
    }

    public Page list(AdminPrincipal principal, Filter filter, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        Query query = query(principal, filter, size, (long) page * size);
        if (query == null) {
            return new Page(List.of(), 0, page, size);
        }
        return new Page(customers.list(query), customers.count(query), page, size);
    }

    /** CSV ixracı üçün: filtrə uyğun bütün sətirlər axınla (yaddaşa yığılmadan) emal olunur. */
    public void forEach(AdminPrincipal principal, Filter filter, Consumer<Row> consumer) {
        Query query = query(principal, filter, 0, 0);
        if (query != null) {
            customers.stream(query, context -> consumer.accept(context.getResultObject()));
        }
    }

    public Row get(AdminPrincipal principal, UUID customerId) {
        BranchScope scope = BranchScope.of(principal, null);
        return customers.find(customerId, scope.all(), scope.branchIds())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** Möhürlər və hədiyyə kartları, yenidən köhnəyə; {@code before} — əvvəlki səhifənin son elementinin vaxtı. */
    public List<TimelineItem> timeline(AdminPrincipal principal, UUID customerId, Instant before) {
        get(principal, customerId);
        return customers.timeline(customerId, before, TIMELINE_PAGE);
    }

    /** Sorğunun parametrləri; telefon kimi axtarış etibarsızdırsa {@code null} (nəticə boşdur). */
    private Query query(AdminPrincipal principal, Filter filter, int limit, long offset) {
        BranchScope scope = BranchScope.of(principal, filter.branchId());
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
        Sort sort = filter.sort() == null ? Sort.REGISTERED_AT : filter.sort();
        return new Query(scope.all(), scope.branchIds(), phoneHash, name,
                filter.language() == null ? null : filter.language().toLowerCase(Locale.ROOT), filter.status(),
                filter.inactiveDays() == null ? null : clock.instant().minus(filter.inactiveDays(), ChronoUnit.DAYS),
                filter.registeredFrom(), filter.registeredTo(), company.current().timezone().getId(),
                sort.expression, filter.descending(), limit, offset);
    }
}
