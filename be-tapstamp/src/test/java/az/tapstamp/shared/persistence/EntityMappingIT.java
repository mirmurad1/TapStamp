package az.tapstamp.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import az.tapstamp.company.domain.Branch;
import az.tapstamp.company.repository.BranchRepository;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.support.IntegrationTest;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.registry.TenantDirectory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hibernate başlanğıcda bazaya qoşulmadığı üçün sxem yoxlaması burada aparılır: hər entity real tenant bazasında
 * sorğulanır — sütun adı və ya tip uyğunsuzluğu bu testi uğursuz edir.
 */
class EntityMappingIT extends IntegrationTest {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    BranchRepository branches;

    @Autowired
    TenantDirectory directory;

    @Test
    void everyEntityMatchesTheTenantSchema() {
        TenantContext.runAs(directory.findBySlug("alpha").orElseThrow().toInfo(), () ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    for (EntityType<?> entity : entityManagerFactory.getMetamodel().getEntities()) {
                        entityManager.createQuery("SELECT e FROM " + entity.getName() + " e")
                                .setMaxResults(1)
                                .getResultList();
                    }
                }));
        assertThat(entityManagerFactory.getMetamodel().getEntities()).isNotEmpty();
    }

    @Test
    void localizedTextAndUuidV7RoundTrip() {
        TenantContext.runAs(directory.findBySlug("beta").orElseThrow().toInfo(), () -> {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            Branch saved = tx.execute(status -> branches.save(new Branch(
                    LocalizedText.of("Sınaq filialı", "Test branch", "Тестовый филиал"), null, null, Instant.now())));
            Branch loaded = tx.execute(status -> branches.findById(saved.getId()).orElseThrow());

            assertThat(loaded.getId().version()).isEqualTo(7);
            assertThat(loaded.getName()).isEqualTo(LocalizedText.of("Sınaq filialı", "Test branch", "Тестовый филиал"));
            assertThat(loaded.isNew()).isFalse();
            tx.executeWithoutResult(status -> branches.deleteById(saved.getId()));
        });
    }
}
