import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronDown, Megaphone, Pencil, Send, Stamp, Trash2 } from 'lucide-react';
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
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { campaignsApi, companyApi, type CampaignRequest, type CampaignView } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { ConfirmPasswordDialog } from '../../components/ConfirmPasswordDialog';
import { InlineLoading } from '../../components/FullPageState';
import {
  campaignStatus,
  campaignStatusTone,
  CAMPAIGN_AUDIENCES,
  INACTIVE_DAYS,
  isAscii,
  NAME_TOKEN,
  toLatin,
  type CampaignAudience,
  type CampaignType,
} from './campaignLabels';

const MAX_TEXT = 1000;
const listKey = (type: CampaignType) => ['campaigns', type] as const;

/** Kampaniya SMS-ləri: yaratmaq (önizləmə ilə) və vəziyyətini izləmək. */
export function CampaignsPage() {
  return <CampaignWorkspace type="SMS" />;
}

/** Möhür kampaniyası: kartı boş olan müştərilərə SMS + hədiyyə möhür (möhür SMS göndəriləndə verilir). */
export function StampCampaignsPage() {
  return <CampaignWorkspace type="STAMPS" />;
}

function CampaignWorkspace({ type }: { type: CampaignType }) {
  const { t } = useTranslation('admin');
  // Dəyişdirilən (təsdiq gözləyən) kampaniya: forma onun dəyərləri ilə açılır
  const [editing, setEditing] = useState<CampaignView>();
  const page = type === 'STAMPS' ? 'stampCampaigns' : 'campaigns';
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">{t(`${page}.title`)}</h1>
        <p className="mt-1 text-muted-foreground">{t(`${page}.subtitle`)}</p>
      </div>
      <NewCampaignCard
        key={editing?.id ?? 'new'}
        type={type}
        editing={editing}
        onDone={() => setEditing(undefined)}
      />
      <CampaignList
        type={type}
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

function NewCampaignCard({ type, editing, onDone }: { type: CampaignType; editing?: CampaignView; onDone: () => void }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const stamps = type === 'STAMPS';
  const [text, setText] = useState(editing?.text ?? '');
  const [audience, setAudience] = useState<CampaignAudience>(editing?.audience ?? 'ALL');
  const [inactiveDays, setInactiveDays] = useState<number>(editing?.inactiveDays ?? 30);
  const [branchId, setBranchId] = useState(editing?.branchId ?? '');
  const [personalized, setPersonalized] = useState(editing?.personalized ?? false);
  const [latin, setLatin] = useState(editing?.latin ?? false);
  const [stampCount, setStampCount] = useState<number>(editing?.stampCount ?? 1);
  const branches = useQuery({ queryKey: ['settings-branches'], queryFn: companyApi.branches });

  const request: CampaignRequest = {
    type,
    stampCount: stamps ? stampCount : undefined,
    text: text.trim(),
    audience: stamps ? 'EMPTY_CARD' : audience,
    inactiveDays: !stamps && audience === 'INACTIVE' ? inactiveDays : undefined,
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
  // Təsdiqə göndərmək (yaratma və dəyişmə) cari şifrə tələb edir
  const [confirming, setConfirming] = useState(false);
  const create = useMutation({
    mutationFn: (currentPassword: string) => {
      const body = { ...request, currentPassword };
      return editing?.id ? campaignsApi.update(editing.id, body) : campaignsApi.create(body);
    },
    onSuccess: () => {
      setConfirming(false);
      setText('');
      setLatin(false);
      setPersonalized(false);
      void queryClient.invalidateQueries({ queryKey: listKey(type) });
      if (editing) {
        onDone();
      }
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    create.reset();
    setConfirming(true);
  };

  const data = text.trim() ? preview.data : undefined;
  // Möhür sayı: 1 … "hədd − 1" (müştəri hədiyyə üçün yenə ən azı bir dəfə gəlməlidir)
  const maxStamps = Math.max(preview.data?.maxStamps ?? stampCount, 1);
  // Bildiriş yalnız istifadəçinin öz mətnindən asılıdır ("Hörmətli" müraciəti nəzərə alınmır)
  const nonLatin = !isAscii(text);
  // Latın variantı yalnız mətn həqiqətən latına çevrilə bilirsə təklif olunur (məs. kiril hərfləri çevrilmir)
  const latinizable = nonLatin && isAscii(toLatin(text));
  const withNamePlaceholder = (value: string) => value.replaceAll(NAME_TOKEN, t('campaigns.preview.namePlaceholder'));
  const Icon = stamps ? Stamp : Megaphone;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Icon className="size-5 text-primary" aria-hidden="true" />
          {editing ? t('campaigns.edit.title') : t(stamps ? 'stampCampaigns.new.title' : 'campaigns.new.title')}
        </CardTitle>
        <CardDescription>{t(stamps ? 'stampCampaigns.new.description' : 'campaigns.new.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-4" onSubmit={submit}>
          {create.isSuccess ? <Alert tone="success">{t('campaigns.new.sent')}</Alert> : null}
          <ApiErrorAlert error={confirming ? null : create.error} />
          <Field id={`${type}-text`} label={t('campaigns.new.text')} hint={t('campaigns.new.textHint')}>
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
            {stamps ? (
              <>
                <Field id={`${type}-stamps`} label={t('stampCampaigns.new.stampCount')}>
                  <Select value={stampCount} onChange={(event) => setStampCount(Number(event.target.value))}>
                    {Array.from({ length: maxStamps }, (_, i) => i + 1).map((count) => (
                      <option key={count} value={count}>
                        {t('stampCampaigns.new.stamps', { count })}
                      </option>
                    ))}
                  </Select>
                </Field>
                <div className="flex flex-col gap-1.5 text-sm">
                  <span className="font-medium">{t('campaigns.new.audience')}</span>
                  <span className="flex min-h-11 items-center text-muted-foreground">
                    {t('campaigns.audience.EMPTY_CARD')}
                  </span>
                </div>
              </>
            ) : (
              <>
                <Field id={`${type}-audience`} label={t('campaigns.new.audience')}>
                  <Select value={audience} onChange={(event) => setAudience(event.target.value as CampaignAudience)}>
                    {CAMPAIGN_AUDIENCES.map((value) => (
                      <option key={value} value={value}>
                        {t(`campaigns.audience.${value}`)}
                      </option>
                    ))}
                  </Select>
                </Field>
                {audience === 'INACTIVE' ? (
                  <Field id={`${type}-days`} label={t('campaigns.new.inactiveDays')}>
                    <Select value={inactiveDays} onChange={(event) => setInactiveDays(Number(event.target.value))}>
                      {INACTIVE_DAYS.map((days) => (
                        <option key={days} value={days}>
                          {t('campaigns.new.days', { count: days })}
                        </option>
                      ))}
                    </Select>
                  </Field>
                ) : null}
              </>
            )}
            <Field id={`${type}-branch`} label={t('campaigns.new.branch')}>
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
              <p className="whitespace-pre-wrap rounded-lg bg-card p-3">
                {withNamePlaceholder(data.template ?? data.fullText ?? '')}
              </p>
              {stamps ? (
                <p className="font-medium">
                  {t('stampCampaigns.preview.stamps', {
                    recipients: formatNumber(data.recipients ?? 0, lang),
                    stamps: data.stampCount ?? stampCount,
                    total: formatNumber(data.totalStamps ?? 0, lang),
                  })}
                </p>
              ) : null}
              <p>
                {t('campaigns.preview.summary', {
                  recipients: formatNumber(data.recipients ?? 0, lang),
                  characters: data.characters ?? 0,
                  segments: segmentRange(data.minSegments, data.segments),
                })}
              </p>
              <p className="font-display text-xl font-semibold">
                {t('campaigns.preview.total', { total: formatNumber(data.totalSms ?? 0, lang) })}
              </p>
              {stamps ? <p className="text-muted-foreground">{t('stampCampaigns.preview.rule')}</p> : null}
              {data.latin ? (
                <Alert tone="info" className="bg-card">
                  <div className="flex flex-col gap-2">
                    <p>{t('campaigns.preview.latinOn')}</p>
                    <Button type="button" variant="outline" className="self-start" onClick={() => setLatin(false)}>
                      {t('campaigns.preview.useOriginal')}
                    </Button>
                  </div>
                </Alert>
              ) : nonLatin ? (
                <Alert tone="info" className="bg-card">
                  <div className="flex flex-col gap-2">
                    <p>{t('campaigns.preview.nonLatin')}</p>
                    {latinizable ? (
                      <>
                        <p>
                          {t('campaigns.preview.latinAdvice', {
                            segments: segmentRange(undefined, data.latinSegments),
                            total: formatNumber(data.latinTotalSms ?? 0, lang),
                          })}
                        </p>
                        <p className="whitespace-pre-wrap rounded-lg bg-muted p-3">
                          {withNamePlaceholder(toLatin(data.template ?? data.fullText ?? ''))}
                        </p>
                        <Button type="button" variant="outline" className="self-start" onClick={() => setLatin(true)}>
                          {t('campaigns.preview.useLatin')}
                        </Button>
                      </>
                    ) : null}
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
        <ConfirmPasswordDialog
          open={confirming}
          title={t('campaigns.confirm.title')}
          description={t('campaigns.confirm.description')}
          confirmLabel={editing ? t('campaigns.edit.save') : t('campaigns.new.submit')}
          pending={create.isPending}
          error={create.error}
          onConfirm={(password) => create.mutate(password)}
          onCancel={() => setConfirming(false)}
        />
      </CardContent>
    </Card>
  );
}

function CampaignList({ type, onEdit }: { type: CampaignType; onEdit: (campaign: CampaignView) => void }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const confirm = useConfirm();
  const queryClient = useQueryClient();
  const paging = usePagination();
  const campaigns = useQuery({
    queryKey: [...listKey(type), paging.page, paging.size],
    queryFn: () => campaignsApi.list(type, { page: paging.page, size: paging.size }),
    placeholderData: keepPreviousData,
  });
  const cancel = useMutation({
    mutationFn: (id: string) => campaignsApi.cancel(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: listKey(type) }),
  });
  const items: CampaignView[] = campaigns.data?.items ?? [];

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
                  <Badge tone={campaignStatusTone(campaign.status)}>
                    {t(`campaigns.status.${campaignStatus(campaign.status)}`)}
                  </Badge>
                  {campaign.stampCount ? (
                    <Badge>{t('stampCampaigns.new.stamps', { count: campaign.stampCount })}</Badge>
                  ) : null}
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
              </p>
              <DeliverySummary campaign={campaign} />
              {campaign.status === 'PENDING_APPROVAL' ? (
                <p className="text-xs text-muted-foreground">{t('campaigns.list.pendingNote')}</p>
              ) : null}
              {campaign.status === 'PAUSED' ? <p className="text-destructive">{t('campaigns.list.pausedNote')}</p> : null}
              {campaign.rejectReason ? (
                <p className="text-destructive">{t('campaigns.list.rejectReason', { reason: campaign.rejectReason })}</p>
              ) : null}
              {(campaign.failedCount ?? 0) > 0 ? <FailedRecipients campaignId={campaign.id ?? ''} /> : null}
            </li>
          ))}
        </ul>
        <Pagination state={paging} total={campaigns.data?.total ?? 0} />
      </CardContent>
    </Card>
  );
}

/** Göndəriş xülasəsi (təsdiqdən sonra): göndərilib, gözləyir, göndərilmədi, ötürülüb; möhür kampaniyasında möhürlər. */
function DeliverySummary({ campaign }: { campaign: CampaignView }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  if (!['SENDING', 'PAUSED', 'SENT'].includes(campaign.status ?? '')) {
    return null;
  }
  const parts = [
    campaign.type === 'STAMPS'
      ? t('campaigns.summary.sentWithStamps', { count: campaign.stampsGranted ?? 0 })
      : t('campaigns.summary.sent', { count: campaign.sentCount ?? 0 }),
    (campaign.pendingCount ?? 0) > 0 ? t('campaigns.summary.pending', { count: campaign.pendingCount ?? 0 }) : null,
    (campaign.failedCount ?? 0) > 0 ? t('campaigns.summary.failed', { count: campaign.failedCount ?? 0 }) : null,
    (campaign.skippedCount ?? 0) > 0 ? t('campaigns.summary.skipped', { count: campaign.skippedCount ?? 0 }) : null,
  ].filter(Boolean);
  return <p className="font-medium">{parts.join(' · ').replace(/\d+/g, (n) => formatNumber(Number(n), lang))}</p>;
}

/** SMS-i göndərilməyən müştərilər (ad, nömrə, səbəb) — açılanda yüklənir. */
function FailedRecipients({ campaignId }: { campaignId: string }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const [open, setOpen] = useState(false);
  const paging = usePagination();
  const failures = useQuery({
    queryKey: ['campaign-failures', campaignId, paging.page, paging.size],
    queryFn: () => campaignsApi.failures(campaignId, { page: paging.page, size: paging.size }),
    enabled: open,
    placeholderData: keepPreviousData,
  });
  return (
    <div className="flex flex-col gap-2">
      <Button type="button" variant="ghost" className="self-start" onClick={() => setOpen(!open)}>
        <ChevronDown className={`size-4 transition-transform ${open ? 'rotate-180' : ''}`} aria-hidden="true" />
        {t('campaigns.failures.toggle')}
      </Button>
      {open ? (
        <div className="rounded-xl bg-muted p-3">
          <ApiErrorAlert error={failures.error} />
          {failures.isPending ? <InlineLoading /> : null}
          <ul className="flex flex-col gap-2">
            {(failures.data?.items ?? []).map((item) => (
              <li key={item.customerId} className="flex flex-col">
                <span className="font-medium">
                  {item.name || t('campaigns.failures.noName')} · <span className="tabular-nums">{item.phone}</span>
                </span>
                <span className="break-all text-xs text-destructive">
                  {item.error}
                  {item.at ? ` · ${formatDateTime(item.at, lang)}` : null}
                </span>
              </li>
            ))}
          </ul>
          <Pagination className="mt-3" state={paging} total={failures.data?.total ?? 0} />
        </div>
      ) : null}
    </div>
  );
}
