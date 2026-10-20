package az.tapstamp.platform.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.tapstamp.campaign.CampaignService;
import az.tapstamp.campaign.repository.CampaignMapper.Campaign;
import az.tapstamp.security.admin.AdminPrincipal;
import az.tapstamp.security.admin.AdminRealm;
import az.tapstamp.security.admin.AdminRealms;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.paging.PageQuery;
import az.tapstamp.shared.paging.PageResult;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.registry.TenantDirectory;
import az.tapstamp.tenancy.registry.TenantRecord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Platforma panelindən kampaniyalar: bütün şirkətlərin kampaniyaları (hər şirkətin öz bazasından) və qərar —
 * ödənişdən sonra təsdiq və ya rədd. Qərar şirkətin və platformanın audit jurnalına yazılır.
 */
@Service
public class PlatformCampaignService {

    public record TenantCampaign(TenantRecord tenant, Campaign campaign) {
    }

    private static final Logger log = LoggerFactory.getLogger(PlatformCampaignService.class);

    private final CampaignService campaigns;
    private final TenantDirectory directory;
    private final AdminRealms realms;

    public PlatformCampaignService(CampaignService campaigns, TenantDirectory directory, AdminRealms realms) {
        this.campaigns = campaigns;
        this.directory = directory;
        this.realms = realms;
    }

    /**
     * Verilmiş statuslarda olan kampaniyalar (bütün aktiv şirkətlər), yenidən köhnəyə, səhifə ilə. Hər şirkətdən
     * ən yeni "offset + size" kampaniya götürülür, birləşdirilib sıralanır və lazım olan hissə kəsilir.
     */
    public PageResult<TenantCampaign> list(List<String> statuses, PageQuery page) {
        int needed = (int) Math.min(page.offset() + page.size(), Integer.MAX_VALUE);
        long total = 0;
        List<TenantCampaign> result = new ArrayList<>();
        for (TenantRecord tenant : directory.findAll()) {
            if (tenant.status().outOfService()) {
                continue;
            }
            try {
                total += TenantContext.callAs(tenant.toInfo(), () -> campaigns.countByStatus(statuses));
                TenantContext.callAs(tenant.toInfo(), () -> campaigns.byStatus(statuses, needed))
                        .forEach(campaign -> result.add(new TenantCampaign(tenant, campaign)));
            } catch (RuntimeException e) {
                log.warn("Campaigns could not be read for tenant {}: {}", tenant.slug(), e.getClass().getSimpleName());
            }
        }
        result.sort(Comparator.comparing((TenantCampaign c) -> c.campaign().createdAt()).reversed());
        int from = (int) Math.min(page.offset(), result.size());
        return page.result(List.copyOf(result.subList(from, Math.min(from + page.size(), result.size()))), total);
    }

    /** Hesab xətasına görə dayandırılmış kampaniyanı davam etdirmək. */
    public TenantCampaign resume(AdminPrincipal principal, UUID tenantId, UUID campaignId) {
        TenantRecord tenant = tenant(tenantId);
        Campaign campaign = TenantContext.callAs(tenant.toInfo(), () -> campaigns.resume(principal.userId(), campaignId));
        realms.audit(AdminRealm.PLATFORM, "TENANT_CAMPAIGN_RESUMED", principal.userId(), "tenant", tenantId,
                Map.of("campaignId", campaignId.toString()));
        return new TenantCampaign(tenant, campaign);
    }

    private TenantRecord tenant(UUID tenantId) {
        TenantRecord tenant = directory.findById(tenantId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (tenant.status().outOfService()) {
            throw new ApiException(ErrorCode.TENANT_UNAVAILABLE);
        }
        return tenant;
    }

    public TenantCampaign decide(AdminPrincipal principal, UUID tenantId, UUID campaignId, boolean approve,
                                 @Nullable String reason) {
        TenantRecord tenant = tenant(tenantId);
        Campaign campaign = TenantContext.callAs(tenant.toInfo(),
                () -> campaigns.decide(principal.userId(), campaignId, approve, reason));
        realms.audit(AdminRealm.PLATFORM, approve ? "TENANT_CAMPAIGN_APPROVED" : "TENANT_CAMPAIGN_REJECTED",
                principal.userId(), "tenant", tenantId,
                Map.of("campaignId", campaignId.toString(), "totalSms", campaign.totalSms()));
        return new TenantCampaign(tenant, campaign);
    }
}
