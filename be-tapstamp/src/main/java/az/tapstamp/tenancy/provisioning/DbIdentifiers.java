package az.tapstamp.tenancy.provisioning;

import java.util.regex.Pattern;

/**
 * Provisioning-də istifadə olunan DB obyekt adları və şifrələri. DDL əmrlərində bind parametrləri işləmədiyi
 * üçün bütün dəyərlər əvvəlcə ciddi ağ siyahı ilə yoxlanılır, sonra sitatlanır — SQL inyeksiyası mümkün deyil.
 */
public final class DbIdentifiers {

    /** Tenant slug-ı: kiçik latın hərfi ilə başlayır, yalnız a-z, 0-9 və alt xətt (2–31 simvol). */
    public static final Pattern SLUG = Pattern.compile("^[a-z][a-z0-9_]{1,30}$");
    public static final Pattern HOSTNAME = Pattern.compile("^(?=.{4,253}$)[a-z0-9-]+(\\.[a-z0-9-]+)+$");
    private static final Pattern PASSWORD = Pattern.compile("^[A-Za-z0-9_-]{32,128}$");

    private DbIdentifiers() {
    }

    public static String requireSlug(String slug) {
        if (slug == null || !SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("Invalid tenant slug");
        }
        return slug;
    }

    public static String requireHostname(String hostname) {
        if (hostname == null || !HOSTNAME.matcher(hostname).matches()) {
            throw new IllegalArgumentException("Invalid hostname");
        }
        return hostname;
    }

    public static String databaseName(String slug) {
        return "loyalty_t_" + requireSlug(slug);
    }

    public static String ownerRole(String slug) {
        return "t_" + requireSlug(slug) + "_owner";
    }

    public static String appRole(String slug) {
        return "t_" + requireSlug(slug) + "_app";
    }

    /** Ağ siyahıdan keçmiş identifikatoru sitatlayır: {@code "ad"}. */
    public static String quote(String identifier) {
        if (!identifier.matches("^[a-z][a-z0-9_]{1,62}$")) {
            throw new IllegalArgumentException("Unsafe identifier");
        }
        return '"' + identifier + '"';
    }

    /** Yalnız bizim yaratdığımız (base64url) şifrələr qəbul olunur, ona görə sitatlama təhlükəsizdir. */
    public static String passwordLiteral(String password) {
        if (password == null || !PASSWORD.matcher(password).matches()) {
            throw new IllegalArgumentException("Unsafe password literal");
        }
        return "'" + password + "'";
    }
}
