package az.tapstamp.tenancy;

public enum TenantStatus {
    /** Baza və rollar yaradılır; sorğu qəbul edilmir. */
    PROVISIONING,
    ACTIVE,
    /** Platforma tərəfindən dayandırılıb. */
    SUSPENDED,
    /** Miqrasiya uğursuz olub; xidmət göstərilmir (fail closed), digər tenant-lar işləyir. */
    MIGRATION_FAILED,
    DECOMMISSIONED;

    /** Yalnız ACTIVE tenant sorğulara xidmət göstərir. */
    public boolean outOfService() {
        return this != ACTIVE;
    }
}
