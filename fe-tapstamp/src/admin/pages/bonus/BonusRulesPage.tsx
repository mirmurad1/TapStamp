import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BellRing, ChevronRight, Pencil, Plus, Sparkles, Trash2 } from 'lucide-react';
import { Link } from 'react-router';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatDate, formatNumber } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { useConfirm } from '@/shared/ui/ConfirmProvider';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';
import { TimeSelect } from '@/shared/ui/time-select';

import {
  bonusDeliveriesApi,
  bonusRulesApi,
  companyApi,
  type BonusDeliveryStatus,
  type BonusRule,
  type BonusRuleRequest,
  type Localized,
} from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { toLocalized } from '../../lib/localized';
import { LocalizedFields } from '../settings/CompanyForm';

const RULES_KEY = ['bonus-rules'] as const;
const DAYS = [1, 2, 3, 4, 5, 6, 7] as const;
const BONUS_OPTIONS = [1, 2, 3, 4, 5] as const;
const STATUSES = ['ACTIVE', 'SCHEDULED', 'ENDED', 'OFF'] as const;
type RuleStatus = (typeof STATUSES)[number];
type Weekday = (typeof DAYS)[number];

function ruleStatus(value: string | undefined): RuleStatus {
  return STATUSES.find((status) => status === value) ?? 'OFF';
}

/** "14:00:00" → "14:00". */
function hhmm(value: string | undefined | null): string {
  return value ? value.slice(0, 5) : '';
}

/** Bonus qaydaları (yalnız sahib): xoşbəxt saatlar və tarixli kampaniyalar. */
export function BonusRulesPage() {
  const { t } = useTranslation('admin');
  const rules = useQuery({ queryKey: RULES_KEY, queryFn: bonusRulesApi.list });
  const [editing, setEditing] = useState<BonusRule | 'new'>();
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-display text-2xl font-semibold">{t('bonusRules.title')}</h1>
          <p className="mt-1 max-w-2xl text-muted-foreground">{t('bonusRules.subtitle')}</p>
        </div>
        <Button onClick={() => setEditing('new')}>
          <Plus className="size-4" aria-hidden="true" />
          {t('bonusRules.add')}
        </Button>
      </div>
      <ApiErrorAlert error={rules.error} />
      {rules.isPending ? <InlineLoading /> : null}
      {rules.data && rules.data.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-start gap-3 py-8">
            <Sparkles className="size-6 text-primary" aria-hidden="true" />
            <p className="font-medium">{t('bonusRules.empty.title')}</p>
            <p className="max-w-xl text-sm text-muted-foreground">{t('bonusRules.empty.hint')}</p>
            <Button variant="outline" onClick={() => setEditing('new')}>
              {t('bonusRules.add')}
            </Button>
          </CardContent>
        </Card>
      ) : null}
      {(rules.data ?? []).map((rule) => (
        <RuleCard key={rule.id} rule={rule} onEdit={() => setEditing(rule)} />
      ))}
      <p className="text-xs text-muted-foreground">{t('bonusRules.note')}</p>
      {editing ? <RuleDialog rule={editing === 'new' ? undefined : editing} onClose={() => setEditing(undefined)} /> : null}
    </div>
  );
}

const STATUS_TONE: Record<RuleStatus, 'success' | 'warning' | 'neutral'> = {
  ACTIVE: 'success',
  SCHEDULED: 'warning',
  ENDED: 'neutral',
  OFF: 'neutral',
};

/** Qaydanın cədvəli bir sətirdə: günlər, saat, tarix aralığı, filial. */
function useScheduleText() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  return (rule: Pick<BonusRule, 'days' | 'startTime' | 'endTime' | 'startsOn' | 'endsOn'>) => {
    const days = rule.days ?? [];
    const parts: string[] = [];
    if (days.length === 7) {
      parts.push(t('bonusRules.schedule.everyDay'));
    } else if (days.join() === '1,2,3,4,5') {
      parts.push(t('bonusRules.schedule.weekdays'));
    } else if (days.join() === '6,7') {
      parts.push(t('bonusRules.schedule.weekend'));
    } else {
      parts.push(days.map((day) => t(`bonusRules.days.${day as Weekday}`)).join(', '));
    }
    parts.push(rule.startTime ? `${hhmm(rule.startTime)}–${hhmm(rule.endTime)}` : t('bonusRules.schedule.allDay'));
    if (rule.startsOn || rule.endsOn) {
      const from = rule.startsOn ? formatDate(rule.startsOn, lang, 'UTC') : '…';
      const to = rule.endsOn ? formatDate(rule.endsOn, lang, 'UTC') : '…';
      parts.push(`${from} – ${to}`);
    }
    return parts.join(', ');
  };
}

function RuleCard({ rule, onEdit }: { rule: BonusRule; onEdit: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const confirm = useConfirm();
  const schedule = useScheduleText();
  const branches = useQuery({ queryKey: ['settings-branches'], queryFn: companyApi.branches });
  const status = ruleStatus(rule.status);
  const remove = useMutation({
    mutationFn: () => bonusRulesApi.remove(rule.id ?? ''),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: RULES_KEY }),
  });
  const branchName = rule.branchId
    ? resolveText((branches.data ?? []).find((branch) => branch.id === rule.branchId)?.name, lang)
    : t('admin:bonusRules.allBranches');
  const onDelete = async () => {
    if (await confirm({ message: t('admin:bonusRules.deleteConfirm'), danger: true, confirmLabel: t('common:actions.delete') })) {
      remove.mutate();
    }
  };
  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <CardTitle className="flex flex-wrap items-center gap-2">
            {resolveText(rule.name, lang)}
            <Badge tone={STATUS_TONE[status]}>{t(`admin:bonusRules.status.${status}`)}</Badge>
            {rule.runningNow ? <Badge tone="success">{t('admin:bonusRules.runningNow')}</Badge> : null}
          </CardTitle>
          <CardDescription>
            {t('admin:bonusRules.bonus', { count: rule.bonusStamps ?? 1 })}
            {' · '}
            {schedule(rule)}
            {' · '}
            {branchName}
          </CardDescription>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={onEdit}>
            <Pencil className="size-4" aria-hidden="true" />
            {t('common:actions.edit')}
          </Button>
          <Button variant="outline" size="sm" onClick={() => void onDelete()} disabled={remove.isPending}
            aria-label={t('common:actions.delete')}>
            <Trash2 className="size-4" aria-hidden="true" />
          </Button>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-2 text-sm">
        <ApiErrorAlert error={remove.error} />
        {remove.data && !remove.data.deleted ? <Alert tone="info">{t('admin:bonusRules.disabledInstead')}</Alert> : null}
        <p>
          {t('admin:bonusRules.stats', {
            stamps: formatNumber(rule.bonusStamps30d ?? 0, lang),
            customers: formatNumber(rule.customers30d ?? 0, lang),
          })}
        </p>
        {rule.notifyOnStart ? (
          <p className="text-muted-foreground">
            {rule.notifiedAt ? t('admin:bonusRules.notified', { date: formatDate(rule.notifiedAt, lang) }) : t('admin:bonusRules.willNotify')}
          </p>
        ) : null}
        {rule.notifyTotal ? <DeliverySummary rule={rule} /> : null}
      </CardContent>
    </Card>
  );
}

function RuleDialog({ rule, onClose }: { rule?: BonusRule; onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const branches = useQuery({ queryKey: ['settings-branches'], queryFn: companyApi.branches });
  const [name, setName] = useState<Localized>({ az: rule?.name?.az ?? '', en: rule?.name?.en, ru: rule?.name?.ru });
  const [bonus, setBonus] = useState(rule?.bonusStamps ?? 1);
  const [days, setDays] = useState<number[]>(rule?.days ?? [1, 2, 3, 4, 5]);
  const [timed, setTimed] = useState(Boolean(rule?.startTime ?? !rule));
  const [startTime, setStartTime] = useState(hhmm(rule?.startTime) || '14:00');
  const [endTime, setEndTime] = useState(hhmm(rule?.endTime) || '17:00');
  const [dated, setDated] = useState(Boolean(rule?.startsOn ?? rule?.endsOn));
  const [startsOn, setStartsOn] = useState(rule?.startsOn ?? '');
  const [endsOn, setEndsOn] = useState(rule?.endsOn ?? '');
  const [branchId, setBranchId] = useState(rule?.branchId ?? '');
  const [active, setActive] = useState(rule?.active ?? true);
  const [notify, setNotify] = useState(rule?.notifyOnStart ?? false);

  const save = useMutation({
    mutationFn: () => {
      const body: BonusRuleRequest = {
        name: toLocalized(name) ?? { az: name.az },
        bonusStamps: bonus,
        days: [...days].sort((a, b) => a - b),
        startTime: timed ? startTime : undefined,
        endTime: timed ? endTime : undefined,
        startsOn: dated && startsOn ? startsOn : undefined,
        endsOn: dated && endsOn ? endsOn : undefined,
        branchId: branchId || undefined,
        active,
        notifyOnStart: notify,
      };
      return rule?.id ? bonusRulesApi.update(rule.id, body) : bonusRulesApi.create(body);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: RULES_KEY });
      onClose();
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };
  const toggleDay = (day: number) =>
    setDays((current) => (current.includes(day) ? current.filter((value) => value !== day) : [...current, day]));
  const timesInvalid = timed && startTime >= endTime;
  const datesInvalid = dated && Boolean(startsOn) && Boolean(endsOn) && startsOn > endsOn;

  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent className="max-h-[90vh] max-w-xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{rule ? t('admin:bonusRules.editTitle') : t('admin:bonusRules.newTitle')}</DialogTitle>
          <DialogDescription>{t('admin:bonusRules.dialogDescription')}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={submit}>
          <ApiErrorAlert error={save.error} />
          <LocalizedFields id="bonus-name" legend={t('admin:bonusRules.form.name')} value={name} onChange={setName} required maxLength={60} />
          <Field id="bonus-stamps" label={t('admin:bonusRules.form.bonus')} hint={t('admin:bonusRules.form.bonusHint')}>
            <Select value={bonus} onChange={(event) => setBonus(Number(event.target.value))}>
              {BONUS_OPTIONS.map((value) => (
                <option key={value} value={value}>
                  {t('admin:bonusRules.bonusOption', { count: value })}
                </option>
              ))}
            </Select>
          </Field>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium">{t('admin:bonusRules.form.days')}</legend>
            <div className="flex flex-wrap gap-2">
              {DAYS.map((day) => (
                <label
                  key={day}
                  className="flex cursor-pointer items-center gap-2 rounded-lg border border-border px-3 py-1.5 text-sm has-[:checked]:border-primary has-[:checked]:bg-primary/10"
                >
                  <input type="checkbox" checked={days.includes(day)} onChange={() => toggleDay(day)} className="size-4 accent-[var(--primary)]" />
                  {t(`admin:bonusRules.days.${day}`)}
                </label>
              ))}
            </div>
            <div className="flex flex-wrap gap-2 text-xs">
              <Button type="button" variant="ghost" size="sm" onClick={() => setDays([1, 2, 3, 4, 5, 6, 7])}>
                {t('admin:bonusRules.schedule.everyDay')}
              </Button>
              <Button type="button" variant="ghost" size="sm" onClick={() => setDays([1, 2, 3, 4, 5])}>
                {t('admin:bonusRules.schedule.weekdays')}
              </Button>
              <Button type="button" variant="ghost" size="sm" onClick={() => setDays([6, 7])}>
                {t('admin:bonusRules.schedule.weekend')}
              </Button>
            </div>
          </fieldset>
          <div className="flex flex-col gap-2 rounded-xl border border-border p-3">
            <label className="flex items-center gap-3 text-sm font-medium">
              <input type="checkbox" checked={timed} onChange={(e) => setTimed(e.target.checked)} className="size-5 accent-[var(--primary)]" />
              {t('admin:bonusRules.form.timed')}
            </label>
            {timed ? (
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <TimeSelect value={startTime} onChange={setStartTime} label={t('admin:bonusRules.form.from')} />
                <span aria-hidden="true">–</span>
                <TimeSelect value={endTime} onChange={setEndTime} label={t('admin:bonusRules.form.to')} />
              </div>
            ) : null}
            <p className="text-xs text-muted-foreground">{timed ? t('admin:bonusRules.form.timedHint') : t('admin:bonusRules.schedule.allDay')}</p>
            {timesInvalid ? <p className="text-xs text-[var(--destructive-ink)]">{t('admin:bonusRules.form.timesInvalid')}</p> : null}
          </div>
          <div className="flex flex-col gap-2 rounded-xl border border-border p-3">
            <label className="flex items-center gap-3 text-sm font-medium">
              <input type="checkbox" checked={dated} onChange={(e) => setDated(e.target.checked)} className="size-5 accent-[var(--primary)]" />
              {t('admin:bonusRules.form.dated')}
            </label>
            {dated ? (
              <div className="grid gap-3 sm:grid-cols-2">
                <Field id="bonus-starts" label={t('admin:bonusRules.form.startsOn')}>
                  <Input type="date" value={startsOn} onChange={(e) => setStartsOn(e.target.value)} />
                </Field>
                <Field id="bonus-ends" label={t('admin:bonusRules.form.endsOn')}>
                  <Input type="date" value={endsOn} onChange={(e) => setEndsOn(e.target.value)} />
                </Field>
              </div>
            ) : null}
            <p className="text-xs text-muted-foreground">{t('admin:bonusRules.form.datedHint')}</p>
            {datesInvalid ? <p className="text-xs text-[var(--destructive-ink)]">{t('admin:bonusRules.form.datesInvalid')}</p> : null}
          </div>
          <Field id="bonus-branch" label={t('admin:bonusRules.form.branch')}>
            <Select value={branchId} onChange={(event) => setBranchId(event.target.value)}>
              <option value="">{t('admin:bonusRules.allBranches')}</option>
              {(branches.data ?? []).map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {resolveText(branch.name, lang)}
                </option>
              ))}
            </Select>
          </Field>
          <label className="flex items-start gap-3 text-sm">
            <input type="checkbox" checked={notify} onChange={(e) => setNotify(e.target.checked)} className="mt-0.5 size-5 accent-[var(--primary)]" />
            <span>
              <span className="block font-medium">{t('admin:bonusRules.form.notify')}</span>
              <span className="block text-muted-foreground">{t('admin:bonusRules.form.notifyHint')}</span>
            </span>
          </label>
          <label className="flex items-start gap-3 text-sm">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} className="mt-0.5 size-5 accent-[var(--primary)]" />
            <span>
              <span className="block font-medium">{t('admin:bonusRules.form.active')}</span>
              <span className="block text-muted-foreground">{t('admin:bonusRules.form.activeHint')}</span>
            </span>
          </label>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {t('common:actions.cancel')}
            </Button>
            <Button type="submit" disabled={save.isPending || !name.az.trim() || days.length === 0 || timesInvalid || datesInvalid}>
              {t('common:actions.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

const DELIVERY_FILTERS = ['ALL', 'DELIVERED', 'FAILED', 'PENDING'] as const;
type DeliveryFilter = (typeof DELIVERY_FILTERS)[number];
const DELIVERY_TONE: Record<BonusDeliveryStatus, 'success' | 'danger' | 'neutral'> = {
  DELIVERED: 'success',
  FAILED: 'danger',
  PENDING: 'neutral',
};

function deliveryStatus(value: string | undefined): BonusDeliveryStatus {
  return value === 'DELIVERED' || value === 'FAILED' ? value : 'PENDING';
}

/** Başlanğıc bildirişinin xülasəsi: neçə nəfərə getdi, çatdı, alınmadı; klikləyəndə siyahı açılır. */
function DeliverySummary({ rule }: { rule: BonusRule }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const [open, setOpen] = useState(false);
  const total = rule.notifyTotal ?? 0;
  const delivered = rule.notifyDelivered ?? 0;
  const failed = rule.notifyFailed ?? 0;
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="flex w-full items-center gap-3 rounded-xl border border-border px-3 py-2.5 text-left hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <BellRing className="size-4 shrink-0 text-primary" aria-hidden="true" />
        <span className="flex flex-1 flex-wrap gap-x-4 gap-y-1">
          <span>{t('bonusRules.deliveries.sent', { count: total, value: formatNumber(total, lang) })}</span>
          <span className="text-[var(--success-ink)]">{t('bonusRules.deliveries.delivered', { value: formatNumber(delivered, lang) })}</span>
          <span className={failed ? 'text-[var(--destructive-ink)]' : 'text-muted-foreground'}>
            {t('bonusRules.deliveries.failed', { value: formatNumber(failed, lang) })}
          </span>
        </span>
        <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
      </button>
      {open ? <DeliveriesDialog rule={rule} onClose={() => setOpen(false)} /> : null}
    </>
  );
}

/** Bildirişi alanların siyahısı: ad, telefon və nəticə; süzgəc və səhifələmə serverdədir. */
function DeliveriesDialog({ rule, onClose }: { rule: BonusRule; onClose: () => void }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const [filter, setFilter] = useState<DeliveryFilter>('ALL');
  const paging = usePagination(filter);
  const ruleId = rule.id ?? '';
  const deliveries = useQuery({
    queryKey: ['bonus-deliveries', ruleId, filter, paging.page, paging.size],
    queryFn: () =>
      bonusDeliveriesApi.list(ruleId, {
        page: paging.page,
        size: paging.size,
        status: filter === 'ALL' ? undefined : filter,
      }),
    placeholderData: keepPreviousData,
    // Göndəriş hələ gedirsə, nəticələr yenilənir
    refetchInterval: (query) => (query.state.data?.items?.some((item) => item.status === 'PENDING') ? 3000 : false),
  });
  const items = deliveries.data?.items ?? [];
  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent className="max-h-[90vh] max-w-xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('bonusRules.deliveries.title')}</DialogTitle>
          <DialogDescription>
            {resolveText(rule.name, lang)}
            {' · '}
            {t('bonusRules.deliveries.note')}
          </DialogDescription>
        </DialogHeader>
        <div className="flex flex-wrap gap-2" role="group" aria-label={t('bonusRules.deliveries.filter')}>
          {DELIVERY_FILTERS.map((value) => (
            <Button key={value} type="button" size="sm" variant={filter === value ? 'default' : 'outline'}
              aria-pressed={filter === value} onClick={() => setFilter(value)}>
              {t(`bonusRules.deliveries.filters.${value}`)}
            </Button>
          ))}
        </div>
        <ApiErrorAlert error={deliveries.error} />
        {deliveries.isPending ? <InlineLoading /> : null}
        {deliveries.data && items.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">{t('bonusRules.deliveries.empty')}</p>
        ) : null}
        {items.length > 0 ? (
          <ul className="divide-y divide-border rounded-xl border border-border">
            {items.map((item) => {
              const status = deliveryStatus(item.status);
              return (
                <li key={item.customerId} className="flex items-center gap-3 px-3 py-2.5 text-sm">
                  <span className="min-w-0 flex-1">
                    <Link to={`/customers/${item.customerId ?? ''}`} className="block truncate font-medium underline-offset-4 hover:underline">
                      {item.name || t('customers.noName')}
                    </Link>
                    <span className="block text-xs text-muted-foreground tabular-nums">{item.phoneMasked}</span>
                  </span>
                  <Badge tone={DELIVERY_TONE[status]}>{t(`bonusRules.deliveries.status.${status}`)}</Badge>
                </li>
              );
            })}
          </ul>
        ) : null}
        <Pagination state={paging} total={deliveries.data?.total ?? 0} />
      </DialogContent>
    </Dialog>
  );
}
