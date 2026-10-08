package az.cafeloyalty.platform.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import az.cafeloyalty.nfc.admin.TagAdminService;
import az.cafeloyalty.nfc.admin.TagAdminService.BranchOption;
import az.cafeloyalty.nfc.admin.TagAdminService.ProvisioningPackage;
import az.cafeloyalty.nfc.admin.TagAdminService.TagUsage;
import az.cafeloyalty.nfc.admin.TagAdminService.TagView;
import az.cafeloyalty.nfc.admin.TagAdminService.TapEvent;
import az.cafeloyalty.security.admin.AdminPrincipal;
import az.cafeloyalty.security.admin.AdminRealm;
import az.cafeloyalty.security.admin.AdminRealms;
import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import az.cafeloyalty.tenancy.TenantContext;
import az.cafeloyalty.tenancy.registry.TenantDirectory;
import az.cafeloyalty.tenancy.registry.TenantRecord;
import org.springframework.stereotype.Service;

/**
 * Platforma panelindən şirkətin NFC çipləri: hər əməliyyat həmin şirkətin bazasında ({@link TenantContext#callAs})
 * icra olunur. Şirkətin audit jurnalına (aktor — platforma) və platforma auditinə yazılır.
 */
@Service
public class PlatformTagService {

    public record TagOverview(TagUsage usage, List<BranchOption> branches, List<TagView> tags) {
    }

    private final TagAdminService tags;
    private final TenantDirectory directory;
    private final AdminRealms realms;

    public PlatformTagService(TagAdminService tags, TenantDirectory directory, AdminRealms realms) {
        this.tags = tags;
        this.directory = directory;
        this.realms = realms;
    }

    public TagOverview overview(AdminPrincipal principal, UUID tenantId) {
        return inTenant(tenantId, () -> new TagOverview(tags.usage(), tags.branches(), tags.list(principal, null)));
    }

    public TagView register(AdminPrincipal principal, UUID tenantId, String uid, UUID branchId, String label) {
        TagView tag = inTenant(tenantId, () -> tags.register(principal, uid, branchId, label));
        realms.audit(AdminRealm.PLATFORM, "TENANT_TAG_REGISTERED", principal.userId(), "tenant", tenantId,
                Map.of("uid", tag.uid(), "free", tag.free()));
        return tag;
    }

    public TagView update(AdminPrincipal principal, UUID tenantId, UUID tagId, String label, UUID branchId) {
        return inTenant(tenantId, () -> tags.update(principal, tagId, label, branchId));
    }

    public TagView setEnabled(AdminPrincipal principal, UUID tenantId, UUID tagId, boolean enabled) {
        return inTenant(tenantId, () -> tags.setEnabled(principal, tagId, enabled));
    }

    public TagView rotateKeys(AdminPrincipal principal, UUID tenantId, UUID tagId) {
        return inTenant(tenantId, () -> tags.rotateKeys(principal, tagId));
    }

    public ProvisioningPackage provisioningPackage(AdminPrincipal principal, UUID tenantId, UUID tagId) {
        ProvisioningPackage pkg = inTenant(tenantId, () -> tags.provisioningPackage(principal, tagId));
        realms.audit(AdminRealm.PLATFORM, "TENANT_TAG_PROVISIONING_EXPORTED", principal.userId(), "tenant", tenantId,
                Map.of("uid", pkg.uid(), "keyVersion", pkg.keyVersion()));
        return pkg;
    }

    public List<TapEvent> events(AdminPrincipal principal, UUID tenantId, UUID tagId) {
        return inTenant(tenantId, () -> tags.events(principal, tagId));
    }

    private <T> T inTenant(UUID tenantId, Supplier<T> action) {
        TenantRecord tenant = directory.findById(tenantId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!tenant.status().servesRequests()) {
            throw new ApiException(ErrorCode.TENANT_UNAVAILABLE);
        }
        return TenantContext.callAs(tenant.toInfo(), action);
    }
}
