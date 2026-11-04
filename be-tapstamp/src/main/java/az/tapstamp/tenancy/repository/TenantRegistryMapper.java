package az.tapstamp.tenancy.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.platform.service.PlatformDomainService.Domain;
import az.tapstamp.shared.persistence.PlatformMapper;
import az.tapstamp.tenancy.TenantStatus;
import az.tapstamp.tenancy.registry.TenantRecord;

/**
 * Platforma reyestri ({@code loyalty_platform} bazası): şirkətlər ({@code tenants}) və onların domenləri
 * ({@code tenant_domains}). SQL: {@code mapper/platform/tenancy/TenantRegistryMapper.xml}.
 */
@PlatformMapper
public interface TenantRegistryMapper {

    // --- Şirkətlər ---

    /** Domen adına görə (kiçik hərflə, portsuz). */
    Optional<TenantRecord> findByHost(String host);

    Optional<TenantRecord> findById(UUID id);

    Optional<TenantRecord> findBySlug(String slug);

    List<TenantRecord> findAll();

    void updateStatus(UUID id, TenantStatus status, String error);

    /** Şirkət adminində ad dəyişəndə platforma panelində də yeni ad görünsün. */
    void updateDisplayName(UUID id, az.tapstamp.shared.i18n.LocalizedText name);

    // --- Domenlər ---

    Optional<String> findPrimaryHost(UUID tenantId);

    List<String> findHostnames(UUID tenantId);

    /** Əsas domen birinci; TLS sertifikatının vəziyyəti ilə. */
    List<Domain> findDomains(UUID tenantId);

    boolean hostnameExists(String hostname);

    void insertDomain(UUID id, UUID tenantId, String hostname, Instant now);

    void clearPrimary(UUID tenantId);

    void markPrimary(UUID domainId);

    void deleteDomain(UUID domainId);

    /** Sertifikat alınmalı domenlər: aktiv və dayandırılmış şirkətlərin bütün domenləri. */
    List<String> findCertificateHostnames();

    /** Sertifikatın vəziyyəti (certbot-un hesabatı): {@code ISSUED} və ya {@code FAILED}; dəyişməyibsə yazılmır. */
    void updateTlsStatus(String hostname, String status);
}
