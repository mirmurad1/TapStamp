import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Medal, Pencil, Plus, Trash2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatNumber } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';
import { useConfirm } from '@/shared/ui/ConfirmProvider';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';

import { tiersApi, type Localized, type TierOverview, type TierView } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { toLocalized } from '../../lib/localized';
import { LocalizedFields } from '../settings/CompanyForm';

const TIERS_KEY = ['tiers'] as const;
const MAX_TIERS = 5;
const BONUS_OPTIONS = [0, 1, 2, 3] as const;
/** Hazır rənglər: bürünc, gümüş, qızıl, platin, brilyant. */
const COLORS = ['#CD7F32', '#9CA3AF', '#D4A017', '#64748B', '#38BDF8'] as const;

/** Müştəri səviyyələri (yalnız sahib): ziyarət sayına görə avtomatik, imtiyaz — əlavə möhür. */
export function TiersPage() {
  const { t } = useTranslation('admin');
  const overview = useQuery({ queryKey: TIERS_KEY, queryFn: tiersApi.list });
  const [editing, setEditing] = useState<TierView | 'new'>();
  const tiers = overview.data?.tiers ?? [];
  const total = tiers.reduce((sum, tier) => sum + (tier.customers ?? 0), overview.data?.belowAll ?? 0);
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-display text-2xl font-semibold">{t('tiers.title')}</h1>
          <p className="mt-1 max-w-2xl text-muted-foreground">{t('tiers.subtitle')}</p>
        </div>
        <Button onClick={() => setEditing('new')} disabled={tiers.length >= MAX_TIERS}>
          <Plus className="size-4" aria-hidden="true" />
          {t('tiers.add')}
        </Button>
      </div>
      <ApiErrorAlert error={overview.error} />
      {overview.isPending ? <InlineLoading /> : null}
      {overview.data && tiers.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-start gap-3 py-8">
            <Medal className="size-6 text-primary" aria-hidden="true" />
            <p className="font-medium">{t('tiers.empty.title')}</p>
            <p className="max-w-xl text-sm text-muted-foreground">{t('tiers.empty.hint')}</p>
            <Button variant="outline" onClick={() => setEditing('new')}>
              {t('tiers.add')}
            </Button>
          </CardContent>
        </Card>
      ) : null}
      {overview.data && tiers.length > 0 ? <ProgramSwitch enabled={overview.data.enabled ?? true} /> : null}
      {tiers.length > 0 ? (
        <Card>
          <CardContent className="flex flex-col divide-y divide-border p-0">
            {overview.data && overview.data.belowAll ? (
              <div className="flex flex-wrap items-center gap-4 px-5 py-3 text-sm text-muted-foreground sm:px-6">
                <span className="w-full sm:w-64">{t('tiers.belowAll')}</span>
                <Share value={overview.data.belowAll} total={total} color="var(--muted-foreground)" />
              </div>
            ) : null}
            {tiers.map((tier) => (
              <TierRow key={tier.id} tier={tier} total={total} onEdit={() => setEditing(tier)} />
            ))}
          </CardContent>
        </Card>
      ) : null}
      <p className="text-xs text-muted-foreground">{t('tiers.note')}</p>
      {editing ? (
        <TierDialog tier={editing === 'new' ? undefined : editing} index={tiers.length} onClose={() => setEditing(undefined)} />
      ) : null}
    </div>
  );
}

/** Proqramın açarı: söndürüləndə müştəri səviyyəni görmür və bonus verilmir, səviyyələr qalır. */
function ProgramSwitch({ enabled }: { enabled: boolean }) {
  const { t } = useTranslation('admin');
  const queryClient = useQueryClient();
  const toggle = useMutation({
    mutationFn: (next: boolean) => tiersApi.setEnabled(next),
    onSuccess: (data: TierOverview) => queryClient.setQueryData(TIERS_KEY, data),
  });
  return (
    <Card>
      <CardContent className="flex flex-col gap-3 p-5 sm:p-6">
        <label className="flex items-start gap-3 text-sm">
          <input
            type="checkbox"
            checked={enabled}
            disabled={toggle.isPending}
            onChange={(event) => toggle.mutate(event.target.checked)}
            className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]"
          />
          <span>
            <span className="block font-medium">{t('tiers.switch.title')}</span>
            <span className="block text-muted-foreground">{t('tiers.switch.hint')}</span>
          </span>
        </label>
        {enabled ? null : <Alert tone="info">{t('tiers.switch.off')}</Alert>}
        <ApiErrorAlert error={toggle.error} />
      </CardContent>
    </Card>
  );
}

/** Səviyyədəki müştərilərin payı — üfüqi zolaq və say. */
function Share({ value, total, color }: { value: number; total: number; color: string }) {
  const lang = currentLanguage();
  const width = total > 0 ? Math.max(2, Math.round((value / total) * 100)) : 0;
  return (
    <span className="flex flex-1 items-center gap-3">
      <span className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
        <span className="block h-full rounded-full" style={{ width: `${width}%`, background: color }} />
      </span>
      <span className="w-12 text-right tabular-nums">{formatNumber(value, lang)}</span>
    </span>
  );
}

function TierRow({ tier, total, onEdit }: { tier: TierView; total: number; onEdit: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const confirm = useConfirm();
  const remove = useMutation({
    mutationFn: () => tiersApi.remove(tier.id ?? ''),
    onSuccess: (data: TierOverview) => queryClient.setQueryData(TIERS_KEY, data),
  });
  const onDelete = async () => {
    if (await confirm({ message: t('admin:tiers.deleteConfirm'), danger: true, confirmLabel: t('common:actions.delete') })) {
      remove.mutate();
    }
  };
  const color = tier.color ?? '#CD7F32';
  return (
    <div className="flex flex-col gap-2 px-5 py-4 sm:px-6">
      <div className="flex flex-wrap items-center gap-4">
        <span className="flex w-full items-center gap-3 sm:w-64">
          <span className="grid size-10 shrink-0 place-items-center rounded-full text-white" style={{ background: color }}>
            <Medal className="size-5" aria-hidden="true" />
          </span>
          <span className="min-w-0">
            <span className="block truncate font-semibold">{resolveText(tier.name, lang)}</span>
            <span className="block text-sm text-muted-foreground">
              {t('admin:tiers.from', { count: tier.minVisits ?? 0 })}
              {' · '}
              {tier.bonusStamps ? t('admin:tiers.perk', { count: tier.bonusStamps }) : t('admin:tiers.badgeOnly')}
            </span>
          </span>
        </span>
        <Share value={tier.customers ?? 0} total={total} color={color} />
        <span className="flex gap-2">
          <Button variant="outline" size="sm" onClick={onEdit}>
            <Pencil className="size-4" aria-hidden="true" />
            {t('common:actions.edit')}
          </Button>
          <Button variant="outline" size="sm" onClick={() => void onDelete()} disabled={remove.isPending}
            aria-label={t('common:actions.delete')}>
            <Trash2 className="size-4" aria-hidden="true" />
          </Button>
        </span>
      </div>
      <ApiErrorAlert error={remove.error} />
    </div>
  );
}

function TierDialog({ tier, index, onClose }: { tier?: TierView; index: number; onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const [name, setName] = useState<Localized>({ az: tier?.name?.az ?? '', en: tier?.name?.en, ru: tier?.name?.ru });
  const [color, setColor] = useState<string>(tier?.color ?? COLORS[Math.min(index, COLORS.length - 1)] ?? '#CD7F32');
  const [minVisits, setMinVisits] = useState(String(tier?.minVisits ?? ''));
  const [bonus, setBonus] = useState(tier?.bonusStamps ?? 0);
  const save = useMutation({
    mutationFn: () => {
      const body = { name: toLocalized(name) ?? { az: name.az }, color, minVisits: Number(minVisits), bonusStamps: bonus };
      return tier?.id ? tiersApi.update(tier.id, body) : tiersApi.create(body);
    },
    onSuccess: (data) => {
      queryClient.setQueryData(TIERS_KEY, data);
      onClose();
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };
  const visitsValid = /^\d{1,5}$/.test(minVisits) && Number(minVisits) <= 10000;
  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent className="max-h-[90vh] max-w-lg overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{tier ? t('admin:tiers.editTitle') : t('admin:tiers.newTitle')}</DialogTitle>
          <DialogDescription>{t('admin:tiers.dialogDescription')}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={submit}>
          <ApiErrorAlert error={save.error} />
          <LocalizedFields id="tier-name" legend={t('admin:tiers.form.name')} value={name} onChange={setName} required maxLength={30} />
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium">{t('admin:tiers.form.color')}</legend>
            <div className="flex flex-wrap items-center gap-2">
              {COLORS.map((value) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => setColor(value)}
                  aria-label={value}
                  aria-pressed={color.toUpperCase() === value}
                  className="size-9 rounded-full ring-offset-2 ring-offset-background aria-pressed:ring-2 aria-pressed:ring-ring"
                  style={{ background: value }}
                />
              ))}
              <Input type="color" value={color} onChange={(e) => setColor(e.target.value.toUpperCase())}
                className="h-9 w-14 p-1" aria-label={t('admin:tiers.form.customColor')} />
            </div>
          </fieldset>
          <Field id="tier-visits" label={t('admin:tiers.form.minVisits')} hint={t('admin:tiers.form.minVisitsHint')}>
            <Input inputMode="numeric" value={minVisits} onChange={(e) => setMinVisits(e.target.value.trim())} required />
          </Field>
          <Field id="tier-bonus" label={t('admin:tiers.form.bonus')} hint={t('admin:tiers.form.bonusHint')}>
            <Select value={bonus} onChange={(event) => setBonus(Number(event.target.value))}>
              {BONUS_OPTIONS.map((value) => (
                <option key={value} value={value}>
                  {value === 0 ? t('admin:tiers.badgeOnly') : t('admin:tiers.perk', { count: value })}
                </option>
              ))}
            </Select>
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {t('common:actions.cancel')}
            </Button>
            <Button type="submit" disabled={save.isPending || !name.az.trim() || !visitsValid}>
              {t('common:actions.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
