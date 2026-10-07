package az.cafeloyalty.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import az.cafeloyalty.tenancy.provisioning.TenantProvisioner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfig {

    /** MockMvc sorğuları standart olaraq alpha şirkətinin domeninə gedir (tenant domenə görə seçilir). */
    @Bean
    MockMvcBuilderCustomizer defaultTenantHost() {
        return builder -> builder.defaultRequest(get("/").with(request -> {
            request.setServerName(TestTenants.ALPHA_HOST);
            return request;
        }));
    }

    /** Real vaxt + idarə olunan sürüşmə (sessiya müddətləri və linklərin ömrü üçün). */
    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }

    @Bean
    TestMailbox testMailbox() {
        return new TestMailbox();
    }

    @Bean
    TestSmsInbox testSmsInbox() {
        return new TestSmsInbox();
    }

    @Bean
    ApplicationRunner testTenants(TenantProvisioner provisioner, @Qualifier("tenantJdbc") JdbcClient tenantJdbc) {
        return args -> TestTenants.provisionAll(provisioner, tenantJdbc);
    }
}
