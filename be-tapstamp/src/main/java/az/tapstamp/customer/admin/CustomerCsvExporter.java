package az.tapstamp.customer.admin;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.company.service.CompanySettings;
import az.tapstamp.security.admin.AccessPolicy;
import az.tapstamp.security.admin.AdminAuthService;
import az.tapstamp.security.admin.AdminPrincipal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Müştərilərin CSV ixracı (yalnız sahib, step-up ilə). Başlıqlar adminin dilindədir, fayl UTF-8 BOM ilə başlayır
 * (Excel Azərbaycan və rus hərflərini düzgün açsın). Telefon nömrəsi tam, yalnız rəqəmlərlə yazılır.
 *
 * <p><b>CSV injection</b> (formula yeridilməsi) qorunması: {@code = + - @}, tab və ya CR ilə başlayan xana
 * apostrofla başlayır ki, Excel onu formula kimi icra etməsin.
 */
@Service
public class CustomerCsvExporter {

    private static final List<String> COLUMNS = List.of("id", "name", "phone", "language", "status", "registeredAt",
            "lastVisitAt", "currentStamps", "totalStamps", "rewardsEarned", "rewardsRedeemed");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private final CustomerDirectory directory;
    private final AdminAuthService adminAuth;
    private final MessageSource messages;
    private final CompanySettings company;
    private final AuditLogger audit;

    public CustomerCsvExporter(CustomerDirectory directory, AdminAuthService adminAuth,
                               @Qualifier(AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME) MessageSource messages,
                               CompanySettings company, AuditLogger audit) {
        this.directory = directory;
        this.adminAuth = adminAuth;
        this.messages = messages;
        this.company = company;
        this.audit = audit;
    }

    /** İcazələri yoxlayır — cavab axını başlamazdan əvvəl çağırılır (xəta adi JSON cavabı kimi qayıtsın). */
    public void authorize(AdminPrincipal principal) {
        AccessPolicy.requireCompanyWide(principal);
        adminAuth.requireStepUp(principal);
    }

    public void write(AdminPrincipal principal, CustomerDirectory.Filter filter, OutputStream output) {
        Locale locale = principal.language().locale();
        ZoneId zone = company.current().timezone();
        int[] count = {0};
        try {
            Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
            writer.write('﻿');
            writeLine(writer, COLUMNS.stream().map(c -> messages.getMessage("csv.customers." + c, null, locale)).toList());
            directory.forEach(principal, filter, row -> {
                try {
                    writeLine(writer, List.of(row.id().toString(), nullToEmpty(row.name()), phone(row.phone()),
                            row.language(), Objects.requireNonNullElse(
                                    messages.getMessage("csv.customers.status." + row.status(), null, row.status(),
                                            locale), row.status()),
                            format(row.registeredAt(), zone), format(row.lastVisitAt(), zone),
                            Integer.toString(row.currentStamps()), Integer.toString(row.totalStamps()),
                            Integer.toString(row.rewardsEarned()), Integer.toString(row.rewardsRedeemed())));
                    count[0]++;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        audit.tenant("CUSTOMERS_EXPORTED", AuditLogger.ActorType.STAFF, principal.userId(), "customers", null,
                Map.of("rows", count[0]));
    }

    static String cell(String value) {
        String safe = value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r") || safe.contains(";")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static void writeLine(Writer writer, List<String> cells) throws IOException {
        writer.write(String.join(",", cells.stream().map(CustomerCsvExporter::cell).toList()));
        writer.write("\r\n");
    }

    private static String format(Instant instant, ZoneId zone) {
        return instant == null ? "" : DATE_TIME.format(instant.atZone(zone));
    }

    /** Nömrə yalnız rəqəmlərlə, "+" olmadan ({@code +994513102967} → {@code 994513102967}): apostrof əlavə olunmur. */
    static String phone(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
