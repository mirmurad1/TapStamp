package az.tapstamp.plan;

/** Paketə görə açılan imkanlar. Hansı paketdə hansının olduğu {@link Plan}-dadır. */
public enum Feature {
    /** Müştərinin telefonuna web push bildirişləri. */
    PUSH,
    /** Dost dəvəti proqramı. */
    REFERRALS,
    /** Müştərilərin CSV ixracı. */
    CSV_EXPORT,
    /** Göstəricilər panelinin qrafikləri (zaman sırası, isti xəritə, geri qayıtma, dillər). */
    FULL_ANALYTICS,
    /** Möhür kampaniyası (kartı boş olanlara SMS + möhür). */
    STAMP_CAMPAIGNS,
    /** Avtomatik xatırlatmalar. */
    REMINDERS,
    /** Əməliyyat (audit) jurnalı. */
    AUDIT_LOG,
    /** Göstəricilərin filiallar üzrə müqayisəsi. */
    BRANCH_COMPARISON,
    /** Dəvət statistikası. */
    REFERRAL_STATS
}
