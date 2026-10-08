import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Clock, MapPin, Pencil, Plus, SlidersHorizontal, Store } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Input } from '@/shared/ui/input';

import { companyApi, type BranchSettings, type BusinessHours, type Localized } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { toLocalized } from '../../lib/localized';
import { LocalizedFields } from './CompanyForm';

const BRANCHES_KEY = ['settings-branches'] as const;
const DAYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'] as const;
type Day = (typeof DAYS)[number];

export function BranchesCard() {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const branches = useQuery({ queryKey: BRANCHES_KEY, queryFn: companyApi.branches });
  const [editing, setEditing] = useState<BranchSettings | 'new'>();
  const [policyOf, setPolicyOf] = useState<BranchSettings>();

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle className="flex items-center gap-2">
              <Store className="size-5 text-primary" aria-hidden="true" />
              {t('admin:settings.branches.title')}
            </CardTitle>
            <CardDescription>{t('admin:settings.branches.description')}</CardDescription>
          </div>
          <Button variant="outline" onClick={() => setEditing('new')}>
            <Plus className="size-4" aria-hidden="true" />
            {t('admin:settings.branches.add')}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <ApiErrorAlert error={branches.error} />
        {branches.isPending ? <InlineLoading /> : null}
        <ul className="flex flex-col divide-y divide-border">
          {branches.data?.map((branch) => (
            <li key={branch.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="min-w-0 text-sm">
                <p className="flex flex-wrap items-center gap-2 font-medium">
                  {resolveText(branch.name, lang)}
                  {branch.status === 'CLOSED' ? <Badge tone="danger">{t('admin:settings.branches.closed')}</Badge> : null}
                  {branch.policyOverridden ? <Badge tone="warning">{t('admin:settings.branches.ownPolicy')}</Badge> : null}
                </p>
                {branch.address ? (
                  <p className="flex items-center gap-1 text-muted-foreground">
                    <MapPin className="size-3.5" aria-hidden="true" />
                    {resolveText(branch.address, lang)}
                  </p>
                ) : null}
                <p className="flex items-center gap-1 text-muted-foreground">
                  <Clock className="size-3.5" aria-hidden="true" />
                  {branch.businessHours ? summarizeHours(branch.businessHours, t) : t('admin:settings.branches.alwaysOpen')}
                </p>
              </div>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={() => setPolicyOf(branch)}>
                  <SlidersHorizontal className="size-4" aria-hidden="true" />
                  {t('admin:settings.branches.policy')}
                </Button>
                <Button variant="outline" size="sm" onClick={() => setEditing(branch)}>
                  <Pencil className="size-4" aria-hidden="true" />
                  {t('common:actions.edit')}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      </CardContent>
      {editing ? <BranchDialog branch={editing === 'new' ? undefined : editing} onClose={() => setEditing(undefined)} /> : null}
      {policyOf ? <PolicyDialog branch={policyOf} onClose={() => setPolicyOf(undefined)} /> : null}
    </Card>
  );
}

function summarizeHours(hours: BusinessHours, t: (key: 'admin:settings.branches.closedDay') => string): string {
  const first = Object.values(hours).find((intervals) => intervals.length > 0)?.[0];
  const openDays = DAYS.filter((day) => (hours[day]?.length ?? 0) > 0).length;
  if (!first) {
    return t('admin:settings.branches.closedDay');
  }
  return `${first[0]}–${first[1]} · ${openDays}/7`;
}

interface DayRow {
  open: boolean;
  from: string;
  to: string;
}

function toRows(hours: BusinessHours | undefined): Record<Day, DayRow> {
  const rows = {} as Record<Day, DayRow>;
  for (const day of DAYS) {
    const interval = hours?.[day]?.[0];
    rows[day] = interval ? { open: true, from: interval[0] ?? '09:00', to: interval[1] ?? '21:00' } : { open: !hours, from: '09:00', to: '21:00' };
  }
  return rows;
}

function BranchDialog({ branch, onClose }: { branch?: BranchSettings; onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const [name, setName] = useState<Localized>({ az: branch?.name?.az ?? '', en: branch?.name?.en, ru: branch?.name?.ru });
  const [address, setAddress] = useState<Localized>({ az: branch?.address?.az ?? '', en: branch?.address?.en, ru: branch?.address?.ru });
  const [scheduled, setScheduled] = useState(Boolean(branch?.businessHours));
  const [rows, setRows] = useState(() => toRows(branch?.businessHours));
  const [closed, setClosed] = useState(branch?.status === 'CLOSED');

  const save = useMutation({
    mutationFn: () => {
      const hours: BusinessHours = {};
      for (const day of DAYS) {
        hours[day] = rows[day].open ? [[rows[day].from, rows[day].to]] : [];
      }
      const body = {
        name: toLocalized(name),
        address: toLocalized(address),
        businessHours: scheduled ? hours : undefined,
        clearBusinessHours: !scheduled,
        status: closed ? 'CLOSED' : 'ACTIVE',
      };
      return branch?.id ? companyApi.updateBranch(branch.id, body) : companyApi.createBranch(body);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: BRANCHES_KEY });
      void queryClient.invalidateQueries({ queryKey: ['public-company'] });
      onClose();
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };
  const setRow = (day: Day, patch: Partial<DayRow>) => setRows((current) => ({ ...current, [day]: { ...current[day], ...patch } }));

  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent className="max-h-[90vh] max-w-xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{branch ? t('admin:settings.branches.editTitle') : t('admin:settings.branches.newTitle')}</DialogTitle>
          <DialogDescription>{t('admin:settings.branches.dialogDescription')}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={submit}>
          <ApiErrorAlert error={save.error} />
          <LocalizedFields id="branch-name" legend={t('admin:settings.branches.name')} value={name} onChange={setName} required />
          <LocalizedFields id="branch-address" legend={t('admin:settings.branches.address')} value={address} onChange={setAddress} maxLength={200} />
          <label className="flex items-center gap-3 text-sm font-medium">
            <input type="checkbox" checked={scheduled} onChange={(e) => setScheduled(e.target.checked)} className="size-5 accent-[var(--primary)]" />
            {t('admin:settings.branches.useHours')}
          </label>
          {scheduled ? (
            <div className="flex flex-col gap-2 rounded-xl border border-border p-3">
              {DAYS.map((day) => (
                <div key={day} className="flex flex-wrap items-center gap-2 text-sm">
                  <label className="flex w-28 items-center gap-2">
                    <input
                      type="checkbox"
                      checked={rows[day].open}
                      onChange={(e) => setRow(day, { open: e.target.checked })}
                      className="size-4 accent-[var(--primary)]"
                    />
                    {t(`admin:settings.branches.days.${day}`)}
                  </label>
                  {rows[day].open ? (
                    <>
                      <Input
                        type="time"
                        className="w-32"
                        value={rows[day].from}
                        onChange={(e) => setRow(day, { from: e.target.value })}
                        aria-label={t('admin:settings.branches.opens')}
                      />
                      <span aria-hidden="true">–</span>
                      <Input
                        type="time"
                        className="w-32"
                        value={rows[day].to}
                        onChange={(e) => setRow(day, { to: e.target.value })}
                        aria-label={t('admin:settings.branches.closes')}
                      />
                    </>
                  ) : (
                    <span className="text-muted-foreground">{t('admin:settings.branches.closedDay')}</span>
                  )}
                </div>
              ))}
              <p className="text-xs text-muted-foreground">{t('admin:settings.branches.hoursHint')}</p>
            </div>
          ) : null}
          {branch ? (
            <label className="flex items-start gap-3 text-sm">
              <input type="checkbox" checked={closed} onChange={(e) => setClosed(e.target.checked)} className="mt-0.5 size-5 accent-[var(--primary)]" />
              <span>
                <span className="block font-medium">{t('admin:settings.branches.closeBranch')}</span>
                <span className="block text-muted-foreground">{t('admin:settings.branches.closeHint')}</span>
              </span>
            </label>
          ) : null}
          <DialogFooter>
            <Button variant="outline" onClick={onClose}>
              {t('common:actions.cancel')}
            </Button>
            <Button type="submit" disabled={save.isPending || !name.az.trim()}>
              {t('common:actions.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** Filial üçün ayrıca qayda; söndürüləndə şirkətin standart qaydası tətbiq olunur. */
function PolicyDialog({ branch, onClose }: { branch: BranchSettings; onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const id = branch.id ?? '';
  const policy = useQuery({ queryKey: ['branch-policy', id], queryFn: () => companyApi.branchPolicy(id) });

  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('admin:settings.branches.policyTitle', { name: resolveText(branch.name, lang) })}</DialogTitle>
          <DialogDescription>{t('admin:settings.branches.policyDescription')}</DialogDescription>
        </DialogHeader>
        <ApiErrorAlert error={policy.error} />
        {policy.isPending ? <InlineLoading /> : null}
        {policy.data ? (
          <PolicyForm
            branchId={id}
            initial={policy.data}
            onSaved={() => {
              void queryClient.invalidateQueries({ queryKey: BRANCHES_KEY });
              void queryClient.invalidateQueries({ queryKey: ['branch-policy', id] });
              onClose();
            }}
            onCancel={onClose}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function PolicyForm({
  branchId,
  initial,
  onSaved,
  onCancel,
}: {
  branchId: string;
  initial: NonNullable<Awaited<ReturnType<typeof companyApi.branchPolicy>>>;
  onSaved: () => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation(['admin', 'common']);
  const [own, setOwn] = useState(Boolean(initial.overridden));
  const [intervalMinutes, setIntervalMinutes] = useState(String(initial.minIntervalMinutes ?? 20));
  const [maxPerDay, setMaxPerDay] = useState(String(initial.maxStampsPerDay ?? 3));
  const [approval, setApproval] = useState(Boolean(initial.requireStaffApproval));
  const [timeout, setTimeoutMinutes] = useState(String(initial.pendingTimeoutMinutes ?? 30));
  const [hours, setHours] = useState(Boolean(initial.enforceBusinessHours));
  const save = useMutation({
    mutationFn: () =>
      own
        ? companyApi.overridePolicy(branchId, {
            minIntervalMinutes: Number(intervalMinutes),
            maxStampsPerDay: Number(maxPerDay),
            requireStaffApproval: approval,
            pendingTimeoutMinutes: Number(timeout),
            enforceBusinessHours: hours,
          })
        : companyApi.resetPolicy(branchId),
    onSuccess: onSaved,
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };
  const check = (checked: boolean, onChange: (value: boolean) => void, label: string, hint?: string) => (
    <label className="flex items-start gap-3 text-sm">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]" />
      <span>
        <span className="block font-medium">{label}</span>
        {hint ? <span className="block text-muted-foreground">{hint}</span> : null}
      </span>
    </label>
  );

  return (
    <form className="flex flex-col gap-4" onSubmit={submit}>
      <ApiErrorAlert error={save.error} />
      {check(own, setOwn, t('admin:settings.branches.useOwnPolicy'), t('admin:settings.branches.useOwnPolicyHint'))}
      {own ? (
        <>
          <Field id="branch-interval" label={t('admin:settings.stamps.interval')} hint={t('admin:settings.stamps.intervalHint')}>
            <Input type="number" min={0} max={1440} value={intervalMinutes} onChange={(e) => setIntervalMinutes(e.target.value)} />
          </Field>
          <Field id="branch-max" label={t('admin:settings.stamps.maxPerDay')}>
            <Input type="number" min={1} max={50} value={maxPerDay} onChange={(e) => setMaxPerDay(e.target.value)} />
          </Field>
          {check(approval, setApproval, t('admin:settings.stamps.approval'), t('admin:settings.stamps.approvalHint'))}
          {approval ? (
            <Field id="branch-timeout" label={t('admin:settings.stamps.timeout')}>
              <Input type="number" min={1} max={1440} value={timeout} onChange={(e) => setTimeoutMinutes(e.target.value)} />
            </Field>
          ) : null}
          {check(hours, setHours, t('admin:settings.branches.enforceHours'), t('admin:settings.branches.enforceHoursHint'))}
        </>
      ) : (
        <Alert tone="info">{t('admin:settings.branches.companyPolicyUsed')}</Alert>
      )}
      <DialogFooter>
        <Button variant="outline" onClick={onCancel}>
          {t('common:actions.cancel')}
        </Button>
        <Button type="submit" disabled={save.isPending}>
          {t('common:actions.save')}
        </Button>
      </DialogFooter>
    </form>
  );
}
