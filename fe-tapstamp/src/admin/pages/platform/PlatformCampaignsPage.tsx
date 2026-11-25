import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, Megaphone, Play, X } from 'lucide-react';
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
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { platformCampaignsApi, type PlatformCampaign } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { EmptyState } from '../../components/EmptyState';
import { InlineLoading } from '../../components/FullPageState';
import { campaignStatus, campaignStatusTone } from '../campaigns/campaignLabels';

const FILTERS = ['PENDING', 'ALL'] as const;
type Filter = (typeof FILTERS)[number];

/** Şirkətlərin kampaniyaları: ödəniş alındıqdan sonra təsdiq (göndəriş başlayır) və ya rədd. */
export function PlatformCampaignsPage() {
  const { t } = useTranslation('admin');
  const [filter, setFilter] = useState<Filter>('PENDING');
  const paging = usePagination(filter);
  const campaigns = useQuery({
    queryKey: ['platform-campaigns', filter, paging.page, paging.size],
    queryFn: () => platformCampaignsApi.list(filter, { page: paging.page, size: paging.size }),
    placeholderData: keepPreviousData,
  });
  const items = campaigns.data?.items ?? [];

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
        <EmptyState
          icon={<Megaphone />}
          message={t(`platform.campaigns.empty.${filter}`)}
          action={filter === 'PENDING' ? { label: t('platform.campaigns.showAll'), onClick: () => setFilter('ALL') } : undefined}
        />
      ) : null}
      {items.map((campaign) => (
        <CampaignCard key={`${campaign.tenantId}-${campaign.id}`} campaign={campaign} />
      ))}
      <Pagination state={paging} total={campaigns.data?.total ?? 0} />
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
  const resume = useMutation({ mutationFn: () => platformCampaignsApi.resume(tenantId, id), onSuccess: refresh });
  const cancel = useMutation({ mutationFn: () => platformCampaignsApi.cancel(tenantId, id), onSuccess: refresh });
  const pending = campaign.status === 'PENDING_APPROVAL';
  const total = formatNumber(campaign.totalSms ?? 0, lang);

  return (
    <Card>
      <CardContent className="flex flex-col gap-3 pt-5 text-sm sm:pt-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{resolveText(campaign.displayName, lang)}</span>
            <span className="text-muted-foreground">{campaign.slug}</span>
            {campaign.type === 'STAMPS' ? (
              <Badge>{t('admin:platform.campaigns.stampType', { count: campaign.stampCount ?? 0 })}</Badge>
            ) : null}
            {campaign.latin ? <Badge>{t('admin:campaigns.list.latin')}</Badge> : null}
            <Badge tone={campaignStatusTone(campaign.status)}>{t(`admin:campaigns.status.${campaignStatus(campaign.status)}`)}</Badge>
          </div>
          <span className="text-muted-foreground">{campaign.createdAt ? formatDateTime(campaign.createdAt, lang) : null}</span>
        </div>
        <p className="whitespace-pre-wrap rounded-lg bg-muted p-3">
          {/* Müraciət müştəriyə gedən kimi: latın hərfləri ilə göndərilirsə "Deyerli" */}
          {campaign.personalized
            ? `${t(campaign.latin ? 'admin:campaigns.list.greetingPrefixLatin' : 'admin:campaigns.list.greetingPrefix')} `
            : null}
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
          {['SENT', 'SENDING', 'PAUSED', 'CANCELLED'].includes(campaign.status ?? '') ? (
            <div>
              <p className="text-muted-foreground">{t('admin:platform.campaigns.delivery')}</p>
              <p className="font-display text-xl font-semibold">
                {campaign.sentCount} / {campaign.pendingCount} / {campaign.failedCount}
              </p>
            </div>
          ) : null}
          {campaign.type === 'STAMPS' ? (
            <div>
              <p className="text-muted-foreground">{t('admin:platform.campaigns.stampsGranted')}</p>
              <p className="font-display text-xl font-semibold">{formatNumber(campaign.stampsGranted ?? 0, lang)}</p>
            </div>
          ) : null}
        </div>
        {campaign.updatedAt ? (
          <p className="text-xs text-muted-foreground">
            {t('admin:campaigns.list.updated', { time: formatDateTime(campaign.updatedAt, lang) })}
          </p>
        ) : null}
        {campaign.rejectReason ? (
          <p className="text-destructive">{t('admin:campaigns.list.rejectReason', { reason: campaign.rejectReason })}</p>
        ) : null}
        {campaign.pauseReason ? (
          <p className="break-all text-destructive">
            {t('admin:platform.campaigns.pauseReason', { reason: campaign.pauseReason })}
          </p>
        ) : null}
        <ApiErrorAlert error={approve.error ?? reject.error ?? resume.error ?? cancel.error} />
        {campaign.status === 'PAUSED' ? (
          <div className="flex flex-wrap gap-2">
            <Button
              disabled={resume.isPending || cancel.isPending}
              onClick={async () => {
                if (await confirm({ message: t('admin:platform.campaigns.resumeConfirm') })) {
                  resume.mutate();
                }
              }}
            >
              <Play className="size-4" aria-hidden="true" />
              {t('admin:platform.campaigns.resume')}
            </Button>
            <Button
              variant="outline"
              disabled={resume.isPending || cancel.isPending}
              onClick={async () => {
                if (await confirm({ message: t('admin:platform.campaigns.cancelConfirm'), danger: true })) {
                  cancel.mutate();
                }
              }}
            >
              <X className="size-4" aria-hidden="true" />
              {t('admin:platform.campaigns.cancel')}
            </Button>
          </div>
        ) : null}
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
