import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Ban, Gift, MinusCircle, PlusCircle, ShieldAlert, Stamp, Unlock } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';
import { formatDate, formatDateTime } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';
import { useConfirm } from '@/shared/ui/ConfirmProvider';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { customersApi, type CustomerRow, type FraudFlag, type TimelineItem } from '../../api/adminApi';
import { useMe } from '../../auth/AdminAuthProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { hasAnyRole } from '../../layout/navigation';
import { useBranchNames } from '../../lib/branches';

const customerKey = (id: string) => ['customer', id] as const;

export function CustomerDetailPage() {
  const { customerId = '' } = useParams();
  const { t } = useTranslation(['admin', 'common']);
  const customer = useQuery({ queryKey: customerKey(customerId), queryFn: () => customersApi.get(customerId) });

  if (customer.isPending) {
    return <InlineLoading />;
  }
  if (!customer.data) {
    return (
      <div className="flex flex-col gap-4">
        <BackLink />
        <ApiErrorAlert error={customer.error} />
      </div>
    );
  }
  const data = customer.data;
  return (
    <div className="flex flex-col gap-6">
      <BackLink />
      <Header customer={data} />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label={t('admin:customers.detail.currentStamps')} value={data.currentStamps ?? 0} />
        <Stat label={t('admin:customers.detail.totalStamps')} value={data.totalStamps ?? 0} />
        <Stat label={t('admin:customers.detail.rewardsEarned')} value={data.rewardsEarned ?? 0} />
        <Stat label={t('admin:customers.detail.rewardsRedeemed')} value={data.rewardsRedeemed ?? 0} />
      </div>
      <div className="grid gap-6 xl:grid-cols-3">
        <Timeline customerId={customerId} className="xl:col-span-2" />
        <FraudFlags customerId={customerId} />
      </div>
    </div>
  );
}

function BackLink() {
  const { t } = useTranslation('admin');
  return (
    <Link to="/customers" className="inline-flex w-fit items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
      <ArrowLeft className="size-4" aria-hidden="true" />
      {t('customers.title')}
    </Link>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <Card>
      <CardContent className="pt-5 sm:pt-6">
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="font-display text-3xl font-semibold tabular-nums">{value}</p>
      </CardContent>
    </Card>
  );
}

function Header({ customer }: { customer: CustomerRow }) {
  const { t } = useTranslation(['admin', 'common']);
  const me = useMe();
  const lang = currentLanguage();
  const isOwner = hasAnyRole(me, ['COMPANY_OWNER']);
  const confirm = useConfirm();
  const queryClient = useQueryClient();
  const id = customer.id ?? '';
  const blocked = customer.status === 'BLOCKED';

  const status = useMutation({
    mutationFn: (next: 'ACTIVE' | 'BLOCKED') => customersApi.updateStatus(id, next),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: customerKey(id) }),
  });
  const toggleBlock = () => {
    if (blocked) {
      status.mutate('ACTIVE');
      return;
    }
    void confirm({ message: t('admin:customers.detail.blockConfirm', { name: customer.name || customer.phone }), danger: true }).then(
      (ok) => ok && status.mutate('BLOCKED'),
    );
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="flex flex-wrap items-center gap-2 font-display text-2xl font-semibold">
            {customer.name || t('admin:customers.noName')}
            <Badge tone={blocked ? 'danger' : 'success'}>{t(`admin:customers.status.${blocked ? 'BLOCKED' : 'ACTIVE'}`)}</Badge>
          </h1>
          <p className="mt-1 flex flex-wrap items-center gap-x-3 text-muted-foreground">
            <span className="tabular-nums">{customer.phone}</span>
            <span>{t(`common:language.${(customer.language ?? 'az') as 'az' | 'en' | 'ru'}`)}</span>
            {customer.registeredAt ? <span>{t('admin:customers.detail.registered', { date: formatDate(customer.registeredAt, lang) })}</span> : null}
            <span>
              {customer.lastVisitAt
                ? t('admin:customers.detail.lastVisit', { date: formatDateTime(customer.lastVisitAt, lang) })
                : t('admin:customers.detail.noVisits')}
            </span>
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <AdjustDialog customer={customer} />
          {isOwner ? (
            <Button
              variant="ghost"
              className={blocked ? undefined : 'text-destructive hover:bg-destructive/10 hover:text-destructive'}
              onClick={toggleBlock}
              disabled={status.isPending}
            >
              {blocked ? <Unlock className="size-4" aria-hidden="true" /> : <Ban className="size-4" aria-hidden="true" />}
              {blocked ? t('admin:customers.detail.unblock') : t('admin:customers.detail.block')}
            </Button>
          ) : null}
        </div>
      </div>
      <ApiErrorAlert error={status.error} />
    </div>
  );
}

const ADJUST_MODES = ['add', 'remove'] as const;

function newIdempotencyKey() {
  return `adj-${crypto.randomUUID()}`;
}

/** Əl ilə düzəliş: əlavə (filial seçilir) və ya silmə; səbəb məcburidir. Açar təkrar göndərişdən qoruyur. */
function AdjustDialog({ customer }: { customer: CustomerRow }) {
  const { t } = useTranslation(['admin', 'common']);
  const me = useMe();
  const queryClient = useQueryClient();
  const { branches } = useBranchNames();
  const isOwner = hasAnyRole(me, ['COMPANY_OWNER']);
  const own = new Set((me.roles ?? []).map((grant) => grant.branchId).filter(Boolean));
  const branchOptions = isOwner ? branches : branches.filter((branch) => own.has(branch.id));
  const id = customer.id ?? '';

  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<'add' | 'remove'>('add');
  const [count, setCount] = useState('1');
  const [reason, setReason] = useState('');
  const [branchId, setBranchId] = useState('');
  const [key, setKey] = useState(newIdempotencyKey);
  const [invalid, setInvalid] = useState(false);

  const adjust = useMutation({
    mutationFn: () =>
      customersApi.adjust(
        id,
        { delta: mode === 'add' ? Number(count) : -Number(count), reason: reason.trim(), branchId: mode === 'add' ? branchId : undefined },
        key,
      ),
    onSuccess: () => {
      setOpen(false);
      void queryClient.invalidateQueries({ queryKey: customerKey(id) });
      void queryClient.invalidateQueries({ queryKey: ['customer-timeline', id] });
    },
  });

  const start = () => {
    setMode('add');
    setCount('1');
    setReason('');
    setBranchId(branchOptions[0]?.id ?? '');
    setKey(newIdempotencyKey());
    setInvalid(false);
    adjust.reset();
    setOpen(true);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const n = Number(count);
    if (!Number.isInteger(n) || n < 1 || n > 50 || reason.trim().length < 5 || (mode === 'add' && !branchId)) {
      setInvalid(true);
      return;
    }
    adjust.mutate();
  };

  return (
    <>
      <Button onClick={start}>
        <Stamp className="size-4" aria-hidden="true" />
        {t('admin:customers.adjust.open')}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('admin:customers.adjust.title')}</DialogTitle>
            <DialogDescription>{t('admin:customers.adjust.description')}</DialogDescription>
          </DialogHeader>
          <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
            <ApiErrorAlert error={adjust.error} />
            {invalid ? <Alert tone="error">{t('admin:customers.adjust.invalid')}</Alert> : null}
            <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label={t('admin:customers.adjust.title')}>
              {ADJUST_MODES.map((value) => (
                <button
                  key={value}
                  type="button"
                  role="radio"
                  aria-checked={mode === value}
                  onClick={() => {
                    setMode(value);
                    setKey(newIdempotencyKey());
                  }}
                  className={cn(
                    'flex items-center justify-center gap-2 rounded-xl border px-3 py-2 text-sm font-medium',
                    mode === value ? 'border-primary bg-primary/10 text-primary' : 'border-border text-muted-foreground',
                  )}
                >
                  {value === 'add' ? <PlusCircle className="size-4" aria-hidden="true" /> : <MinusCircle className="size-4" aria-hidden="true" />}
                  {t(`admin:customers.adjust.${value}`)}
                </button>
              ))}
            </div>
            <Field id="adjust-count" label={t('admin:customers.adjust.count')}>
              <Input type="number" min={1} max={50} value={count} onChange={(event) => setCount(event.target.value)} />
            </Field>
            {mode === 'add' ? (
              <Field id="adjust-branch" label={t('admin:dashboard.filters.branch')}>
                <Select value={branchId} onChange={(event) => setBranchId(event.target.value)}>
                  {branchOptions.map((branch) => (
                    <option key={branch.id} value={branch.id}>
                      {branch.name}
                    </option>
                  ))}
                </Select>
              </Field>
            ) : (
              <p className="text-sm text-muted-foreground">{t('admin:customers.adjust.removeHint')}</p>
            )}
            <Field id="adjust-reason" label={t('admin:customers.adjust.reason')} hint={t('admin:customers.adjust.reasonHint')}>
              <Input value={reason} onChange={(event) => setReason(event.target.value)} maxLength={200} />
            </Field>
            <DialogFooter>
              <Button variant="outline" onClick={() => setOpen(false)}>
                {t('common:actions.cancel')}
              </Button>
              <Button type="submit" disabled={adjust.isPending}>
                {t('common:actions.save')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}

function Timeline({ customerId, className }: { customerId: string; className?: string }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const { nameOf } = useBranchNames();
  const paging = usePagination();
  const timeline = useQuery({
    queryKey: ['customer-timeline', customerId, paging.page, paging.size],
    queryFn: () => customersApi.timeline(customerId, { page: paging.page, size: paging.size }),
    placeholderData: keepPreviousData,
  });
  const items = timeline.data?.items ?? [];

  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle>{t('customers.timeline.title')}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <ApiErrorAlert error={timeline.error} />
        {timeline.isPending ? <InlineLoading /> : null}
        {timeline.data && items.length === 0 ? <p className="text-sm text-muted-foreground">{t('customers.timeline.empty')}</p> : null}
        <ol className="flex flex-col divide-y divide-border">
          {items.map((item) => (
            <TimelineRow key={`${item.kind}-${item.id}`} item={item} branchName={nameOf(item.branchId)} lang={lang} />
          ))}
        </ol>
        <Pagination state={paging} total={timeline.data?.total ?? 0} />
      </CardContent>
    </Card>
  );
}

function TimelineRow({ item, branchName, lang }: { item: TimelineItem; branchName?: string; lang: ReturnType<typeof currentLanguage> }) {
  const { t } = useTranslation('admin');
  const isReward = item.kind === 'REWARD';
  const rewardName = item.rewardName ? resolveText(JSON.parse(item.rewardName) as Record<string, string>, lang) : '';
  const muted = item.status === 'REVOKED' || item.status === 'REJECTED' || item.status === 'EXPIRED';
  return (
    <li className="flex items-start gap-3 py-3">
      <span
        className={cn(
          'mt-0.5 grid size-8 shrink-0 place-items-center rounded-full',
          isReward ? 'bg-accent/15 text-accent' : 'bg-primary/10 text-primary',
          muted && 'bg-muted text-muted-foreground',
        )}
        aria-hidden="true"
      >
        {isReward ? <Gift className="size-4" /> : <Stamp className="size-4" />}
      </span>
      <div className="min-w-0 flex-1 text-sm">
        <p className={cn('font-medium', muted && 'text-muted-foreground line-through')}>
          {isReward ? t('customers.timeline.reward', { name: rewardName }) : t(`customers.timeline.stamp.${item.source === 'MANUAL' || item.source === 'CAMPAIGN' ? item.source : 'NFC_TAP'}`)}
        </p>
        <p className="text-muted-foreground">
          {item.at ? formatDateTime(item.at, lang) : null}
          {branchName ? ` · ${branchName}` : null}
          {' · '}
          {t(`customers.timeline.status.${item.status ?? 'CONFIRMED'}` as 'customers.timeline.status.CONFIRMED')}
          {isReward && item.redeemedAt ? ` · ${formatDateTime(item.redeemedAt, lang)}` : null}
        </p>
        {item.reason ? <p className="text-muted-foreground italic">“{item.reason}”</p> : null}
      </div>
    </li>
  );
}

function FraudFlags({ customerId }: { customerId: string }) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const paging = usePagination();
  const flags = useQuery({
    queryKey: ['customer-flags', customerId, paging.page, paging.size],
    queryFn: () => customersApi.fraudFlags(customerId, { page: paging.page, size: paging.size }),
    placeholderData: keepPreviousData,
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <ShieldAlert className="size-5 text-[var(--highlight-ink)]" aria-hidden="true" />
          {t('admin:customers.flagsTitle')}
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <ApiErrorAlert error={flags.error} />
        {flags.isPending ? <InlineLoading /> : null}
        {flags.data && flags.data.items?.length === 0 ? <p className="text-sm text-muted-foreground">{t('admin:customers.noFlags')}</p> : null}
        <ul className="flex flex-col gap-3">
          {flags.data?.items?.map((flag) => (
            <FlagItem
              key={flag.id}
              flag={flag}
              lang={lang}
              onResolved={() => {
                void queryClient.invalidateQueries({ queryKey: ['customer-flags', customerId] });
                void queryClient.invalidateQueries({ queryKey: customerKey(customerId) });
              }}
            />
          ))}
        </ul>
        <Pagination state={paging} total={flags.data?.total ?? 0} />
      </CardContent>
    </Card>
  );
}

function FlagItem({ flag, lang, onResolved }: { flag: FraudFlag; lang: ReturnType<typeof currentLanguage>; onResolved: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const [note, setNote] = useState('');
  const resolve = useMutation({ mutationFn: () => customersApi.resolveFlag(flag.id ?? '', note.trim()), onSuccess: onResolved });
  const open = !flag.resolvedAt;
  return (
    <li className="rounded-xl border border-border p-3 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="font-medium">{t(`admin:customers.flagType.${flag.type ?? 'REPLAY_ATTEMPT'}` as 'admin:customers.flagType.REPLAY_ATTEMPT')}</span>
        <Badge tone={open ? 'warning' : 'neutral'}>{open ? t('admin:customers.flagOpen') : t('admin:customers.flagResolved')}</Badge>
      </div>
      <p className="text-muted-foreground">{flag.createdAt ? formatDateTime(flag.createdAt, lang) : null}</p>
      {flag.resolutionNote ? <p className="mt-1 italic text-muted-foreground">“{flag.resolutionNote}”</p> : null}
      {open ? (
        <form
          className="mt-2 flex gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            if (note.trim().length >= 3) {
              resolve.mutate();
            }
          }}
        >
          <Input
            value={note}
            onChange={(event) => setNote(event.target.value)}
            placeholder={t('admin:customers.resolveNote')}
            aria-label={t('admin:customers.resolveNote')}
            maxLength={500}
          />
          <Button type="submit" size="sm" variant="outline" disabled={resolve.isPending || note.trim().length < 3}>
            {t('admin:customers.resolve')}
          </Button>
        </form>
      ) : null}
      <ApiErrorAlert error={resolve.error} />
    </li>
  );
}

