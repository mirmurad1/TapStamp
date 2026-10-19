import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, X } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatDateTime, formatNumber } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';
import { useConfirm } from '@/shared/ui/ConfirmProvider';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';

import { platformCampaignsApi, type PlatformCampaign } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { campaignStatus, campaignStatusTone } from '../campaigns/campaignLabels';

const FILTERS = ['PENDING', 'ALL'] as const;
type Filter = (typeof FILTERS)[number];

/** Şirkətlərin kampaniyaları: ödəniş alındıqdan sonra təsdiq (göndəriş başlayır) və ya rədd. */
export function PlatformCampaignsPage() {
  const { t } = useTranslation('admin');
  const [filter, setFilter] = useState<Filter>('PENDING');
  const campaigns = useQuery({
    queryKey: ['platform-campaigns', filter],
    queryFn: () => platformCampaignsApi.list(filter),
  });
  const items = campaigns.data ?? [];

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">{t('platform.campaigns.title')}</h1>
        <p className="mt-1 text-muted-foreground">{t('platform.campaigns.subtitle')}</p>
      </div>
      <div className="sm:max-w-xs">
        <Field id="platform-campaigns-filter" label={t('platform.campaigns.filter')}>
          <Select value={filter} onChange={(event) => setFilter(event.target.value as Filter)}>
            {FILTERS.map((value) => (
              <option key={value} value={value}>
                {t(`platform.campaigns.filters.${value}`)}
              </option>
            ))}
          </Select>
        </Field>
      </div>
      <ApiErrorAlert error={campaigns.error} />
      {campaigns.isPending ? <InlineLoading /> : null}
      {campaigns.data && items.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">{t(`platform.campaigns.empty.${filter}`)}</p>
      ) : null}
      {items.map((campaign) => (
        <CampaignCard key={`${campaign.tenantId}-${campaign.id}`} campaign={campaign} />
      ))}
    </div>
  );
}

function CampaignCard({ campaign }: { campaign: PlatformCampaign }) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const confirm = useConfirm();
  const queryClient = useQueryClient();
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['platform-campaigns'] });
  const tenantId = campaign.tenantId ?? '';
  const id = campaign.id ?? '';
  const approve = useMutation({ mutationFn: () => platformCampaignsApi.approve(tenantId, id), onSuccess: refresh });
  const reject = useMutation({ mutationFn: () => platformCampaignsApi.reject(tenantId, id, reason.trim()), onSuccess: refresh });
  const pending = campaign.status === 'PENDING_APPROVAL';
  const total = formatNumber(campaign.totalSms ?? 0, lang);

  return (
    <Card>
      <CardContent className="flex flex-col gap-3 pt-5 text-sm sm:pt-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{resolveText(campaign.displayName, lang)}</span>
            <span className="text-muted-foreground">{campaign.slug}</span>
            <Badge tone={campaignStatusTone(campaign.status)}>{t(`admin:campaigns.status.${campaignStatus(campaign.status)}`)}</Badge>
          </div>
          <span className="text-muted-foreground">{campaign.createdAt ? formatDateTime(campaign.createdAt, lang) : null}</span>
        </div>
        <p className="whitespace-pre-wrap rounded-lg bg-muted p-3">
          {campaign.personalized ? `${t('admin:campaigns.list.greetingPrefix')} ` : null}
          {campaign.text}
        </p>
        <p className="text-muted-foreground">
          {t(`admin:campaigns.audience.${campaign.audience ?? 'ALL'}`)}
          {campaign.audience === 'INACTIVE' ? ` (${t('admin:campaigns.new.days', { count: campaign.inactiveDays ?? 0 })})` : null}
        </p>
        <div className="flex flex-wrap gap-6">
          <div>
            <p className="text-muted-foreground">{t('admin:platform.campaigns.recipients')}</p>
            <p className="font-display text-xl font-semibold">{formatNumber(campaign.recipients ?? 0, lang)}</p>
          </div>
          <div>
            <p className="text-muted-foreground">{t('admin:platform.campaigns.perMessage')}</p>
            <p className="font-display text-xl font-semibold">{campaign.segmentsPerMessage}</p>
          </div>
          <div>
            <p className="text-muted-foreground">{t('admin:platform.campaigns.totalSms')}</p>
            <p className="font-display text-xl font-semibold">{total}</p>
          </div>
          {campaign.status === 'SENT' || campaign.status === 'SENDING' ? (
            <div>
              <p className="text-muted-foreground">{t('admin:platform.campaigns.delivery')}</p>
              <p className="font-display text-xl font-semibold">
                {campaign.sentCount} / {campaign.failedCount}
              </p>
            </div>
          ) : null}
        </div>
        {campaign.rejectReason ? (
          <p className="text-destructive">{t('admin:campaigns.list.rejectReason', { reason: campaign.rejectReason })}</p>
        ) : null}
        <ApiErrorAlert error={approve.error ?? reject.error} />
        {pending && !rejecting ? (
          <div className="flex flex-wrap gap-2">
            <Button
              disabled={approve.isPending}
              onClick={async () => {
                if (await confirm({ message: t('admin:platform.campaigns.approveConfirm', { total }) })) {
                  approve.mutate();
                }
              }}
            >
              <Check className="size-4" aria-hidden="true" />
              {t('admin:platform.campaigns.approve')}
            </Button>
            <Button variant="outline" onClick={() => setRejecting(true)}>
              <X className="size-4" aria-hidden="true" />
              {t('admin:platform.campaigns.reject')}
            </Button>
          </div>
        ) : null}
        {pending && rejecting ? (
          <form
            className="flex flex-wrap items-end gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              reject.mutate();
            }}
          >
            <div className="min-w-64 flex-1">
              <Field id={`reject-${id}`} label={t('admin:platform.campaigns.rejectReason')}>
                <Input value={reason} maxLength={300} required onChange={(event) => setReason(event.target.value)} />
              </Field>
            </div>
            <Button type="submit" disabled={!reason.trim() || reject.isPending}>
              {t('admin:platform.campaigns.reject')}
            </Button>
            <Button type="button" variant="ghost" onClick={() => setRejecting(false)}>
              {t('common:actions.cancel')}
            </Button>
          </form>
        ) : null}
      </CardContent>
    </Card>
  );
}
