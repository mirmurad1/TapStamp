import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Megaphone, Send } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatDateTime, formatNumber } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { useConfirm } from '@/shared/ui/ConfirmProvider';
import { Field } from '@/shared/ui/field';
import { Select } from '@/shared/ui/input';

import { campaignsApi, companyApi, type CampaignRequest, type CampaignView } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { campaignStatus, campaignStatusTone, CAMPAIGN_AUDIENCES, INACTIVE_DAYS, type CampaignAudience } from './campaignLabels';

const LIST_KEY = ['campaigns'] as const;
const MAX_TEXT = 1000;

/** Kampaniya SMS-ləri: yaratmaq (önizləmə ilə) və vəziyyətini izləmək. */
export function CampaignsPage() {
  const { t } = useTranslation('admin');
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">{t('campaigns.title')}</h1>
        <p className="mt-1 text-muted-foreground">{t('campaigns.subtitle')}</p>
      </div>
      <NewCampaignCard />
      <CampaignList />
    </div>
  );
}

/** Yazmaq dayananda (400 ms) dəyər yenilənir — önizləmə hər hərfdə sorğu göndərmir. */
function useDebounced<T>(value: T, delay = 400): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

function NewCampaignCard() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const [text, setText] = useState('');
  const [audience, setAudience] = useState<CampaignAudience>('ALL');
  const [inactiveDays, setInactiveDays] = useState<number>(30);
  const [branchId, setBranchId] = useState('');
  const branches = useQuery({ queryKey: ['settings-branches'], queryFn: companyApi.branches });

  const request: CampaignRequest = {
    text: text.trim(),
    audience,
    inactiveDays: audience === 'INACTIVE' ? inactiveDays : undefined,
    branchId: branchId || undefined,
  };
  const debounced = useDebounced(request);
  const preview = useQuery({
    queryKey: ['campaign-preview', debounced],
    queryFn: () => campaignsApi.preview(debounced),
    enabled: debounced.text.length > 0,
    placeholderData: keepPreviousData,
  });
  const create = useMutation({
    mutationFn: () => campaignsApi.create(request),
    onSuccess: () => {
      setText('');
      void queryClient.invalidateQueries({ queryKey: LIST_KEY });
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    create.mutate();
  };

  const data = text.trim() ? preview.data : undefined;
  // Latın variantından istifadə: şirkət adı prefiksini ("Kafe: ") çıxarıb yalnız istifadəçinin mətnini dəyişirik
  const useLatin = () => {
    if (!data?.latinText || !data.fullText) {
      return;
    }
    const prefix = data.fullText.length - text.trim().length;
    setText(data.latinText.slice(prefix));
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Megaphone className="size-5 text-primary" aria-hidden="true" />
          {t('campaigns.new.title')}
        </CardTitle>
        <CardDescription>{t('campaigns.new.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-4" onSubmit={submit}>
          {create.isSuccess ? <Alert tone="success">{t('campaigns.new.sent')}</Alert> : null}
          <ApiErrorAlert error={create.error} />
          <Field id="campaign-text" label={t('campaigns.new.text')} hint={t('campaigns.new.textHint')}>
            <textarea
              value={text}
              maxLength={MAX_TEXT}
              rows={4}
              required
              onChange={(event) => setText(event.target.value)}
              className="w-full rounded-xl border border-border bg-card px-3.5 py-2 text-base text-foreground shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:text-sm"
            />
          </Field>
          <div className="grid gap-4 sm:grid-cols-3">
            <Field id="campaign-audience" label={t('campaigns.new.audience')}>
              <Select value={audience} onChange={(event) => setAudience(event.target.value as CampaignAudience)}>
                {CAMPAIGN_AUDIENCES.map((value) => (
                  <option key={value} value={value}>
                    {t(`campaigns.audience.${value}`)}
                  </option>
                ))}
              </Select>
            </Field>
            {audience === 'INACTIVE' ? (
              <Field id="campaign-days" label={t('campaigns.new.inactiveDays')}>
                <Select value={inactiveDays} onChange={(event) => setInactiveDays(Number(event.target.value))}>
                  {INACTIVE_DAYS.map((days) => (
                    <option key={days} value={days}>
                      {t('campaigns.new.days', { count: days })}
                    </option>
                  ))}
                </Select>
              </Field>
            ) : null}
            <Field id="campaign-branch" label={t('campaigns.new.branch')}>
              <Select value={branchId} onChange={(event) => setBranchId(event.target.value)}>
                <option value="">{t('campaigns.new.allBranches')}</option>
                {(branches.data ?? []).map((branch) => (
                  <option key={branch.id} value={branch.id}>
                    {resolveText(branch.name, lang)}
                  </option>
                ))}
              </Select>
            </Field>
          </div>

          {data ? (
            <div className="flex flex-col gap-3 rounded-xl bg-muted p-4 text-sm">
              <p className="font-medium">{t('campaigns.preview.title')}</p>
              <p className="whitespace-pre-wrap rounded-lg bg-card p-3">{data.fullText}</p>
              <p>
                {t('campaigns.preview.summary', {
                  recipients: formatNumber(data.recipients ?? 0, lang),
                  characters: data.characters ?? 0,
                  segments: data.segments ?? 0,
                })}
              </p>
              <p className="font-display text-xl font-semibold">
                {t('campaigns.preview.total', { total: formatNumber(data.totalSms ?? 0, lang) })}
              </p>
              {data.latinCheaper ? (
                <Alert tone="info" className="bg-card">
                  <div className="flex flex-col gap-2">
                    <p>
                      {t('campaigns.preview.latinAdvice', {
                        segments: data.latinSegments ?? 0,
                        total: formatNumber(data.latinTotalSms ?? 0, lang),
                      })}
                    </p>
                    <p className="whitespace-pre-wrap rounded-lg bg-muted p-3">{data.latinText}</p>
                    <Button type="button" variant="outline" className="self-start" onClick={useLatin}>
                      {t('campaigns.preview.useLatin')}
                    </Button>
                  </div>
                </Alert>
              ) : data.encoding === 'UCS_2' ? (
                <p className="text-muted-foreground">{t('campaigns.preview.unicodeNote')}</p>
              ) : null}
            </div>
          ) : null}
          <ApiErrorAlert error={text.trim() ? preview.error : null} />

          <Button type="submit" className="self-start" disabled={!text.trim() || create.isPending || !data?.recipients}>
            <Send className="size-4" aria-hidden="true" />
            {t('campaigns.new.submit')}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function CampaignList() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const confirm = useConfirm();
  const queryClient = useQueryClient();
  const campaigns = useQuery({ queryKey: LIST_KEY, queryFn: campaignsApi.list });
  const cancel = useMutation({
    mutationFn: (id: string) => campaignsApi.cancel(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: LIST_KEY }),
  });
  const items: CampaignView[] = campaigns.data ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('campaigns.list.title')}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <ApiErrorAlert error={campaigns.error ?? cancel.error} />
        {campaigns.isPending ? <InlineLoading /> : null}
        {campaigns.data && items.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">{t('campaigns.list.empty')}</p>
        ) : null}
        <ul className="flex flex-col divide-y divide-border">
          {items.map((campaign) => (
            <li key={campaign.id} className="flex flex-col gap-2 py-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone={campaignStatusTone(campaign.status)}>{t(`campaigns.status.${campaignStatus(campaign.status)}`)}</Badge>
                  <span className="text-muted-foreground">
                    {campaign.createdAt ? formatDateTime(campaign.createdAt, lang) : null}
                    {' · '}
                    {t(`campaigns.audience.${campaign.audience ?? 'ALL'}`)}
                  </span>
                </div>
                {campaign.status === 'PENDING_APPROVAL' ? (
                  <Button
                    variant="ghost"
                    disabled={cancel.isPending}
                    onClick={async () => {
                      if (await confirm({ message: t('campaigns.list.cancelConfirm'), danger: true })) {
                        cancel.mutate(campaign.id ?? '');
                      }
                    }}
                  >
                    {t('campaigns.list.cancel')}
                  </Button>
                ) : null}
              </div>
              <p className="whitespace-pre-wrap">{campaign.text}</p>
              <p className="text-muted-foreground">
                {t('campaigns.list.counts', {
                  recipients: formatNumber(campaign.recipients ?? 0, lang),
                  total: formatNumber(campaign.totalSms ?? 0, lang),
                })}
                {campaign.status === 'SENT' || campaign.status === 'SENDING'
                  ? ` · ${t('campaigns.list.delivery', { sent: campaign.sentCount ?? 0, failed: campaign.failedCount ?? 0 })}`
                  : null}
              </p>
              {campaign.status === 'PENDING_APPROVAL' ? (
                <p className="text-xs text-muted-foreground">{t('campaigns.list.pendingNote')}</p>
              ) : null}
              {campaign.rejectReason ? (
                <p className="text-destructive">{t('campaigns.list.rejectReason', { reason: campaign.rejectReason })}</p>
              ) : null}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}
