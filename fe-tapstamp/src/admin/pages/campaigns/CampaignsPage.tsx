import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Megaphone, Pencil, Send, Trash2 } from 'lucide-react';
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
  // Dəyişdirilən (təsdiq gözləyən) kampaniya: forma onun dəyərləri ilə açılır
  const [editing, setEditing] = useState<CampaignView>();
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">{t('campaigns.title')}</h1>
        <p className="mt-1 text-muted-foreground">{t('campaigns.subtitle')}</p>
      </div>
      <NewCampaignCard key={editing?.id ?? 'new'} editing={editing} onDone={() => setEditing(undefined)} />
      <CampaignList
        onEdit={(campaign) => {
          setEditing(campaign);
          window.scrollTo({ top: 0, behavior: 'smooth' });
        }}
      />
    </div>
  );
}

/** Hər müştəriyə düşən SMS: hamıya eynidirsə bir rəqəm, fərdi müraciətdə adlara görə aralıq (məs. 1–2). */
function segmentRange(min: number | undefined, max: number | undefined): string {
  return min !== undefined && max !== undefined && min !== max ? `${min}–${max}` : String(max ?? 0);
}

/** Yazmaq dayananda (400 ms) dəyər yenilənir — önizləmə hər hərfdə sorğu göndərmir. */
function useDebounced<T>(value: T, delay = 400): T {
  const [debounced, setDebounced] = useState(value);
  // Obyekt hər render-də yenidir — məzmuna görə müqayisə edirik (əks halda taymer hər dəfə yenidən başlayır)
  const key = JSON.stringify(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(JSON.parse(key) as T), delay);
    return () => clearTimeout(timer);
  }, [key, delay]);
  return debounced;
}

function NewCampaignCard({ editing, onDone }: { editing?: CampaignView; onDone: () => void }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const [text, setText] = useState(editing?.text ?? '');
  const [audience, setAudience] = useState<CampaignAudience>(editing?.audience ?? 'ALL');
  const [inactiveDays, setInactiveDays] = useState<number>(editing?.inactiveDays ?? 30);
  const [branchId, setBranchId] = useState(editing?.branchId ?? '');
  const [personalized, setPersonalized] = useState(editing?.personalized ?? false);
  const [latin, setLatin] = useState(editing?.latin ?? false);
  const branches = useQuery({ queryKey: ['settings-branches'], queryFn: companyApi.branches });

  const request: CampaignRequest = {
    text: text.trim(),
    audience,
    inactiveDays: audience === 'INACTIVE' ? inactiveDays : undefined,
    branchId: branchId || undefined,
    personalized,
    latin,
  };
  const debounced = useDebounced(request);
  const preview = useQuery({
    queryKey: ['campaign-preview', debounced],
    queryFn: () => campaignsApi.preview(debounced),
    enabled: debounced.text.length > 0,
    placeholderData: keepPreviousData,
  });
  const create = useMutation({
    mutationFn: () => (editing?.id ? campaignsApi.update(editing.id, request) : campaignsApi.create(request)),
    onSuccess: () => {
      setText('');
      setLatin(false);
      setPersonalized(false);
      void queryClient.invalidateQueries({ queryKey: LIST_KEY });
      if (editing) {
        onDone();
      }
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    create.mutate();
  };

  const data = text.trim() ? preview.data : undefined;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Megaphone className="size-5 text-primary" aria-hidden="true" />
          {editing ? t('campaigns.edit.title') : t('campaigns.new.title')}
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
          <label className="flex cursor-pointer items-start gap-3 text-sm">
            <input
              type="checkbox"
              className="mt-0.5 size-5 shrink-0 accent-primary"
              checked={personalized}
              onChange={(event) => setPersonalized(event.target.checked)}
            />
            <span>
              <span className="font-medium">{t('campaigns.new.personalized')}</span>
              <span className="block text-muted-foreground">{t('campaigns.new.personalizedHint')}</span>
            </span>
          </label>
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
              {data.sampleWithoutName ? (
                <>
                  <p className="text-muted-foreground">{t('campaigns.preview.withName')}</p>
                  <p className="whitespace-pre-wrap rounded-lg bg-card p-3">{data.fullText}</p>
                  <p className="text-muted-foreground">{t('campaigns.preview.withoutName')}</p>
                  <p className="whitespace-pre-wrap rounded-lg bg-card p-3">{data.sampleWithoutName}</p>
                </>
              ) : (
                <p className="whitespace-pre-wrap rounded-lg bg-card p-3">{data.fullText}</p>
              )}
              <p>
                {t('campaigns.preview.summary', {
                  recipients: formatNumber(data.recipients ?? 0, lang),
                  characters: data.characters ?? 0,
                  segments: segmentRange(data.minSegments, data.segments),
                })}
              </p>
              {data.personalized ? <p className="text-muted-foreground">{t('campaigns.preview.personalizedNote')}</p> : null}
              <p className="font-display text-xl font-semibold">
                {t('campaigns.preview.total', { total: formatNumber(data.totalSms ?? 0, lang) })}
              </p>
              {data.latin ? (
                <Alert tone="info" className="bg-card">
                  <div className="flex flex-col gap-2">
                    <p>{t('campaigns.preview.latinOn')}</p>
                    <Button type="button" variant="outline" className="self-start" onClick={() => setLatin(false)}>
                      {t('campaigns.preview.useAzerbaijani')}
                    </Button>
                  </div>
                </Alert>
              ) : data.encoding === 'UCS_2' ? (
                <Alert tone="info" className="bg-card">
                  <div className="flex flex-col gap-2">
                    <p>
                      {t(data.latinCheaper ? 'campaigns.preview.latinAdvice' : 'campaigns.preview.latinSame', {
                        segments: segmentRange(undefined, data.latinSegments),
                        total: formatNumber(data.latinTotalSms ?? 0, lang),
                      })}
                    </p>
                    <p className="whitespace-pre-wrap rounded-lg bg-muted p-3">{data.latinText}</p>
                    <Button type="button" variant="outline" className="self-start" onClick={() => setLatin(true)}>
                      {t('campaigns.preview.useLatin')}
                    </Button>
                  </div>
                </Alert>
              ) : null}
            </div>
          ) : null}
          <ApiErrorAlert error={text.trim() ? preview.error : null} />

          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={!text.trim() || create.isPending || !data?.recipients}>
              <Send className="size-4" aria-hidden="true" />
              {editing ? t('campaigns.edit.save') : t('campaigns.new.submit')}
            </Button>
            {editing ? (
              <Button type="button" variant="ghost" onClick={onDone}>
                {t('campaigns.edit.cancel')}
              </Button>
            ) : null}
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

function CampaignList({ onEdit }: { onEdit: (campaign: CampaignView) => void }) {
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
                  {campaign.personalized ? <Badge>{t('campaigns.list.personalized')}</Badge> : null}
                  {campaign.latin ? <Badge>{t('campaigns.list.latin')}</Badge> : null}
                  <span className="text-muted-foreground">
                    {campaign.createdAt ? formatDateTime(campaign.createdAt, lang) : null}
                    {' · '}
                    {t(`campaigns.audience.${campaign.audience ?? 'ALL'}`)}
                  </span>
                </div>
                {campaign.status === 'PENDING_APPROVAL' ? (
                  <div className="flex gap-1">
                    <Button variant="outline" onClick={() => onEdit(campaign)}>
                      <Pencil className="size-4" aria-hidden="true" />
                      {t('campaigns.list.edit')}
                    </Button>
                    <Button
                      variant="ghost"
                      disabled={cancel.isPending}
                      onClick={async () => {
                        if (await confirm({ message: t('campaigns.list.cancelConfirm'), danger: true })) {
                          cancel.mutate(campaign.id ?? '');
                        }
                      }}
                    >
                      <Trash2 className="size-4" aria-hidden="true" />
                      {t('campaigns.list.cancel')}
                    </Button>
                  </div>
                ) : null}
              </div>
              <p className="whitespace-pre-wrap">{campaign.text}</p>
              {campaign.updatedAt ? (
                <p className="text-xs text-muted-foreground">
                  {t('campaigns.list.updated', { time: formatDateTime(campaign.updatedAt, lang) })}
                </p>
              ) : null}
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
