package az.cafeloyalty.tenancy.datasource;

import java.sql.Connection;
import java.sql.SQLException;

import az.cafeloyalty.tenancy.TenantContext;
import org.springframework.jdbc.datasource.AbstractDataSource;

/**
 * Bağlantını cari tenant-ın bazasından verir. Tenant konteksti yoxdursa bağlantı verilmir (fail closed) —
 * beləliklə tenant məlumatına "təsadüfən" kontekstsiz müraciət mümkün deyil.
 */
public class TenantRoutingDataSource extends AbstractDataSource {

    private final TenantDataSourceRegistry registry;

    public TenantRoutingDataSource(TenantDataSourceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return registry.dataSourceFor(TenantContext.require()).getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException("Tenant connections use the tenant's own database role");
    }
}
