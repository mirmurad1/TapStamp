package az.cafeloyalty.dev.seed;

import java.time.Instant;

import javax.sql.DataSource;

import az.cafeloyalty.config.AppProperties;
import az.cafeloyalty.dev.seed.SeedCatalog.TenantSeed;
import az.cafeloyalty.security.crypto.EnvelopeCipher;
import az.cafeloyalty.security.crypto.PhoneHasher;
import az.cafeloyalty.tenancy.TenantContext;
import az.cafeloyalty.tenancy.TenantInfo;
import az.cafeloyalty.tenancy.provisioning.TenantProvisioner;
import az.cafeloyalty.tenancy.registry.TenantRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dev mühitində test şirkətlərini (real provisioning servisi ilə) yaradır və məlumatla doldurur.
 * İdempotentdir: şirkətin artıq filialı varsa, məlumat yenidən yazılmır.
 */
@Component
@Profile("dev")
@Order(100)
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final AppProperties properties;
    private final TenantProvisioner provisioner;
    private final NamedParameterJdbcTemplate tenantJdbc;
    private final TransactionTemplate transaction;
    private final EnvelopeCipher tenantCipher;
    private final PhoneHasher phoneHasher;
    private final DevAccountSeeder accounts;

    public DevDataSeeder(AppProperties properties, TenantProvisioner provisioner,
                         @Qualifier("tenantDataSource") DataSource tenantDataSource,
                         PlatformTransactionManager transactionManager,
                         @Qualifier("tenantCipher") EnvelopeCipher tenantCipher, PhoneHasher phoneHasher,
                         DevAccountSeeder accounts) {
        this.properties = properties;
        this.provisioner = provisioner;
        this.tenantJdbc = new NamedParameterJdbcTemplate(tenantDataSource);
        this.transaction = new TransactionTemplate(transactionManager);
        this.tenantCipher = tenantCipher;
        this.phoneHasher = phoneHasher;
        this.accounts = accounts;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.dev().seedEnabled() || args.containsOption("provision-tenant")) {
            return;
        }
        seed(SeedCatalog.mockCoffee());
        seed(SeedCatalog.demoTea());
        accounts.seedPlatformAdmin();
    }

    public void seed(TenantSeed seed) {
        TenantRecord tenant = provisioner.provision(seed.command());
        TenantInfo info = tenant.toInfo();
        TenantContext.runAs(info, () -> {
            Integer branches = tenantJdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM branches", Integer.class);
            if (branches != null && branches > 0) {
                log.info("Test məlumatları artıq mövcuddur: {}", tenant.slug());
            } else {
                TenantSeedWriter.Summary summary = transaction.execute(status ->
                        new TenantSeedWriter(tenantJdbc, tenantCipher, phoneHasher, info, Instant.now(), seed.randomSeed())
                                .write(seed));
                log.info("Test məlumatları yazıldı: {} → {}", tenant.slug(), summary);
            }
            // Hesablar ayrıca və idempotent yaradılır (əvvəlki mərhələdə doldurulmuş bazalara da əlavə olunur)
            accounts.seedStaff(tenant.slug());
        });
    }
}
