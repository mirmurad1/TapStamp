package az.cafeloyalty.tenancy;

public enum TenantStatus {
    /** Baza və rollar yaradılır; sorğu qəbul edilmir. */
    PROVISIONING,
    ACTIVE,
    /** Platforma tərəfindən dayandırılıb. */
    SUSPENDED,
    /** Miqrasiya uğursuz olub; xidmət göstərilmir (fail closed), digər tenant-lar işləyir. */
    MIGRATION_FAILED,
    DECOMMISSIONED;

    public boolean servesRequests() {
        return this == ACTIVE;
    }
}
