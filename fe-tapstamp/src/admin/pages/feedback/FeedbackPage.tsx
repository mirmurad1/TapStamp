import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MessageSquareHeart, TriangleAlert } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';
import { formatDateTime, formatNumber, formatPercent, formatPhone } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Select } from '@/shared/ui/input';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { FEEDBACK_SUMMARY_KEY, feedbackApi, type FeedbackRating, type FeedbackView } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';

const SETTINGS_KEY = ['feedback-settings'] as const;
const FREQUENCIES = [1, 7, 14, 30] as const;
const FILTERS = ['ALL', 'UNRESOLVED', 'BAD', 'OK', 'GOOD'] as const;
type Filter = (typeof FILTERS)[number];
const EMOJI: Record<FeedbackRating, string> = { GOOD: '😊', OK: '😐', BAD: '🙁' };
/** Xülasədə qiymət üzrə say. */
const COUNT: Record<FeedbackRating, (s: { good?: number; ok?: number; bad?: number }) => number> = {
  GOOD: (s) => s.good ?? 0,
  OK: (s) => s.ok ?? 0,
  BAD: (s) => s.bad ?? 0,
};
const TONE: Record<FeedbackRating, 'success' | 'warning' | 'danger'> = { GOOD: 'success', OK: 'warning', BAD: 'danger' };

function rating(value: string | undefined): FeedbackRating {
  return value === 'GOOD' || value === 'OK' ? value : 'BAD';
}

/** Ziyarətdən sonra rəy (yalnız sahib): parametrlər, son 30 günün xülasəsi və rəylər. */
export function FeedbackPage() {
  const { t } = useTranslation('admin');
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">{t('feedback.title')}</h1>
        <p className="mt-1 max-w-2xl text-muted-foreground">{t('feedback.subtitle')}</p>
      </div>
      <div className="grid gap-6 lg:grid-cols-[1fr_1.4fr]">
        <SettingsCard />
        <SummaryCard />
      </div>
      <FeedbackList />
    </div>
  );
}

function SettingsCard() {
  const { t } = useTranslation('admin');
  const queryClient = useQueryClient();
  const settings = useQuery({ queryKey: SETTINGS_KEY, queryFn: feedbackApi.settings });
  const save = useMutation({
    mutationFn: feedbackApi.updateSettings,
    onSuccess: (data) => queryClient.setQueryData(SETTINGS_KEY, data),
  });
  const data = settings.data;
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('feedback.settings.title')}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <ApiErrorAlert error={settings.error ?? save.error} />
        {data ? (
          <>
            <label className="flex items-start gap-3 text-sm">
              <input
                type="checkbox"
                checked={Boolean(data.enabled)}
                disabled={save.isPending}
                onChange={(event) => save.mutate({ enabled: event.target.checked, frequencyDays: data.frequencyDays ?? 7 })}
                className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]"
              />
              <span>
                <span className="block font-medium">{t('feedback.settings.enabled')}</span>
                <span className="block text-muted-foreground">{t('feedback.settings.enabledHint')}</span>
              </span>
            </label>
            <Field id="feedback-frequency" label={t('feedback.settings.frequency')}>
              <Select
                value={data.frequencyDays ?? 7}
                disabled={save.isPending}
                onChange={(event) => save.mutate({ enabled: Boolean(data.enabled), frequencyDays: Number(event.target.value) })}
              >
                {FREQUENCIES.map((days) => (
                  <option key={days} value={days}>
                    {t(`feedback.settings.freq.${days}`)}
                  </option>
                ))}
              </Select>
            </Field>
          </>
        ) : (
          <InlineLoading />
        )}
      </CardContent>
    </Card>
  );
}

function SummaryCard() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const summary = useQuery({ queryKey: FEEDBACK_SUMMARY_KEY, queryFn: () => feedbackApi.summary() });
  const s = summary.data;
  const tile = (label: string, value: string) => (
    <div className="rounded-xl border border-border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-2xl font-semibold tabular-nums">{value}</p>
    </div>
  );
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('feedback.summary.title')}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <ApiErrorAlert error={summary.error} />
        {s ? (
          <>
            {s.unresolvedBad ? (
              <p className="flex items-center gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-sm font-semibold text-[var(--destructive-ink)]">
                <TriangleAlert className="size-4 shrink-0" aria-hidden="true" />
                {t('feedback.unresolvedAlert', { count: s.unresolvedBad })}
              </p>
            ) : null}
            <div className="grid grid-cols-3 gap-3">
              {tile(t('feedback.summary.satisfaction'), s.satisfaction == null ? t('feedback.summary.none') : formatPercent(s.satisfaction, lang, 0))}
              {tile(t('feedback.summary.responses'), formatNumber(s.answered ?? 0, lang))}
              {tile(t('feedback.summary.responseRate'), s.responseRate == null ? t('feedback.summary.none') : formatPercent(s.responseRate, lang, 0))}
            </div>
            <div className="flex flex-wrap gap-4 text-sm">
              {(['GOOD', 'OK', 'BAD'] as const).map((value) => (
                <span key={value} className="flex items-center gap-1.5">
                  <span aria-hidden="true">{EMOJI[value]}</span>
                  {t(`feedback.rating.${value}`)}: <b className="tabular-nums">{formatNumber(COUNT[value](s), lang)}</b>
                </span>
              ))}
            </div>
          </>
        ) : (
          <InlineLoading />
        )}
      </CardContent>
    </Card>
  );
}

function FeedbackList() {
  const { t } = useTranslation('admin');
  const [filter, setFilter] = useState<Filter>('ALL');
  const paging = usePagination(filter);
  const list = useQuery({
    queryKey: ['feedback', filter, paging.page, paging.size],
    queryFn: () =>
      feedbackApi.list({
        page: paging.page,
        size: paging.size,
        rating: filter === 'GOOD' || filter === 'OK' || filter === 'BAD' ? filter : undefined,
        unresolved: filter === 'UNRESOLVED' ? true : undefined,
      }),
    placeholderData: keepPreviousData,
  });
  const items = list.data?.items ?? [];
  return (
    <Card>
      <CardContent className="flex flex-col gap-4 pt-5">
        <div className="flex flex-wrap gap-2" role="group" aria-label={t('feedback.title')}>
          {FILTERS.map((value) => (
            <Button key={value} size="sm" variant={filter === value ? 'default' : 'outline'} aria-pressed={filter === value}
              onClick={() => setFilter(value)}>
              {t(`feedback.filters.${value}`)}
            </Button>
          ))}
        </div>
        <ApiErrorAlert error={list.error} />
        {list.isPending ? <InlineLoading /> : null}
        {list.data && items.length === 0 ? (
          <p className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
            <MessageSquareHeart className="size-4" aria-hidden="true" />
            {t('feedback.empty')}
          </p>
        ) : null}
        <ul className="flex flex-col divide-y divide-border">
          {items.map((item) => (
            <FeedbackItem key={item.id} item={item} />
          ))}
        </ul>
        <Pagination state={paging} total={list.data?.total ?? 0} />
      </CardContent>
    </Card>
  );
}

function FeedbackItem({ item }: { item: FeedbackView }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const [resolving, setResolving] = useState(false);
  const value = rating(item.rating);
  const open = value === 'BAD' && !item.resolvedAt;
  return (
    <li className={cn('flex gap-3 py-4', open && '-mx-3 rounded-xl bg-destructive/5 px-3')}>
      <span className="text-3xl leading-none" aria-hidden="true">
        {EMOJI[value]}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone={TONE[value]}>{t(`feedback.rating.${value}`)}</Badge>
          <Link to={`/customers/${item.customerId ?? ''}`} className="font-semibold underline-offset-4 hover:underline">
            {item.customerName || t('customers.noName')}
          </Link>
          <a href={`tel:${item.phone ?? ''}`} className="text-sm tabular-nums text-muted-foreground hover:underline">
            {formatPhone(item.phone)}
          </a>
        </div>
        <p className="mt-0.5 text-xs text-muted-foreground">
          {resolveText(item.branchName, lang)} · {item.createdAt ? formatDateTime(item.createdAt, lang) : ''}
        </p>
        {item.reasons?.length ? (
          <div className="mt-2 flex flex-wrap gap-1.5">
            {item.reasons.map((reason) => (
              <Badge key={reason}>{t(`feedback.reasons.${reason as 'OTHER'}`)}</Badge>
            ))}
          </div>
        ) : null}
        {item.comment ? <p className="mt-2 text-sm whitespace-pre-line">{item.comment}</p> : null}
        {item.resolvedAt ? (
          <p className="mt-2 text-xs text-[var(--success-ink)]">
            {t('feedback.resolvedMark')} {t('feedback.resolved')} · {formatDateTime(item.resolvedAt, lang)}
            {item.resolutionNote ? ` — ${item.resolutionNote}` : ''}
          </p>
        ) : value !== 'GOOD' ? (
          <Button size="sm" variant="outline" className="mt-2" onClick={() => setResolving(true)}>
            {t('feedback.resolve')}
          </Button>
        ) : null}
      </div>
      {resolving ? <ResolveDialog id={item.id ?? ''} onClose={() => setResolving(false)} /> : null}
    </li>
  );
}

function ResolveDialog({ id, onClose }: { id: string; onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const [note, setNote] = useState('');
  const resolve = useMutation({
    mutationFn: () => feedbackApi.resolve(id, note.trim() || undefined),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['feedback'] });
      void queryClient.invalidateQueries({ queryKey: FEEDBACK_SUMMARY_KEY });
      onClose();
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    resolve.mutate();
  };
  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t('feedback.resolveTitle')}</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={submit}>
          <ApiErrorAlert error={resolve.error} />
          <Field id="feedback-note" label={t('feedback.resolveNote')}>
            <textarea
              value={note}
              onChange={(event) => setNote(event.target.value)}
              maxLength={500}
              rows={3}
              placeholder={t('feedback.resolveNotePlaceholder')}
              className="w-full rounded-xl border border-border bg-card p-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {t('common:actions.cancel')}
            </Button>
            <Button type="submit" disabled={resolve.isPending}>
              {t('feedback.resolve')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
