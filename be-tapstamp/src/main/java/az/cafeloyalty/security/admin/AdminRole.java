package az.cafeloyalty.security.admin;

/** Admin rolları. SUPER_ADMIN platforma səviyyəsindədir, digərləri şirkət daxilində. */
public enum AdminRole {
    SUPER_ADMIN,
    COMPANY_OWNER,
    BRANCH_MANAGER,
    STAFF;

    public String authority() {
        return "ROLE_" + name();
    }

    /** MANAGER və STAFF filiala bağlıdır. */
    public boolean branchScoped() {
        return this == BRANCH_MANAGER || this == STAFF;
    }
}
