import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Gift, Stamp } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Input } from '@/shared/ui/input';

import { settingsApi, type ExistingCards, type RewardImpact, type RewardSettings, type StampPolicy } from '../../api/adminApi';
import { useStepUp } from '../../auth/StepUpProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { BranchesCard } from './BranchesCard';
import { CompanyCard } from './CompanyForm';
import { PlanCard } from './PlanCard';

const POLICY_KEY = ['settings-stamp-policy'] as const;
const REWARD_KEY = ['settings-reward-rule'] as const;

/** Sahibin parametrləri: möhür qaydası, hədiyyə, şirkətin məzmunu (3 dildə) və filiallar. */
export function SettingsPage() {
  const { t } = useTranslation('admin');
  const policy = useQuery({ queryKey: POLICY_KEY, queryFn: settingsApi.stampPolicy });
  const reward = useQuery({ queryKey: REWARD_KEY, queryFn: settingsApi.rewardRule });
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-bold">{t('settings.title')}</h1>
        <p className="mt-1 text-muted-foreground">{t('settings.subtitle')}</p>
      </div>
      <PlanCard />
      <ApiErrorAlert error={policy.error ?? reward.error} />
      {policy.isPending || reward.isPending ? <InlineLoading /> : null}
      <div className="grid gap-6 xl:grid-cols-2">
        {policy.data ? <StampPolicyForm initial={policy.data} /> : null}
        {reward.isSuccess ? <RewardForm initial={reward.data ?? undefined} /> : null}
      </div>
      <div className="grid gap-6 xl:grid-cols-2">
        <CompanyCard />
        <BranchesCard />
      </div>
    </div>
  );
}

function StampPolicyForm({ initial }: { initial: StampPolicy }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const [intervalMinutes, setIntervalMinutes] = useState(String(initial.minIntervalMinutes ?? 20));
  const [maxPerDay, setMaxPerDay] = useState(String(initial.maxStampsPerDay ?? 3));
  const [approval, setApproval] = useState(Boolean(initial.requireStaffApproval));
  const [timeoutMinutes, setTimeoutMinutes] = useState(String(initial.pendingTimeoutMinutes ?? 30));
  const save = useMutation({
    mutationFn: () =>
      settingsApi.updateStampPolicy({
        minIntervalMinutes: Number(intervalMinutes),
        maxStampsPerDay: Number(maxPerDay),
        requireStaffApproval: approval,
        pendingTimeoutMinutes: Number(timeoutMinutes),
      }),
    onSuccess: (updated) => queryClient.setQueryData(POLICY_KEY, updated),
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Stamp className="size-5 text-primary" aria-hidden="true" />
          {t('admin:settings.stamps.title')}
        </CardTitle>
        <CardDescription>{t('admin:settings.stamps.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-4" onSubmit={submit}>
          {save.isSuccess ? <Alert tone="success">{t('admin:settings.saved')}</Alert> : null}
          <ApiErrorAlert error={save.error} />
          <Field id="min-interval" label={t('admin:settings.stamps.interval')} hint={t('admin:settings.stamps.intervalHint')}>
            <Input type="number" min={0} max={1440} required value={intervalMinutes} onChange={(e) => setIntervalMinutes(e.target.value)} />
          </Field>
          <Field id="max-per-day" label={t('admin:settings.stamps.maxPerDay')} hint={t('admin:settings.stamps.maxPerDayHint')}>
            <Input type="number" min={1} max={50} required value={maxPerDay} onChange={(e) => setMaxPerDay(e.target.value)} />
          </Field>
          <label className="flex items-start gap-3 text-sm">
            <input
              type="checkbox"
              checked={approval}
              onChange={(e) => setApproval(e.target.checked)}
              className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]"
            />
            <span>
              <span className="block font-medium">{t('admin:settings.stamps.approval')}</span>
              <span className="block text-muted-foreground">{t('admin:settings.stamps.approvalHint')}</span>
            </span>
          </label>
          {approval ? (
            <Field id="pending-timeout" label={t('admin:settings.stamps.timeout')} hint={t('admin:settings.stamps.timeoutHint')}>
              <Input type="number" min={1} max={1440} required value={timeoutMinutes} onChange={(e) => setTimeoutMinutes(e.target.value)} />
            </Field>
          ) : null}
          <Button type="submit" disabled={save.isPending} className="self-start">
            {t('common:actions.save')}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function RewardForm({ initial }: { initial: RewardSettings | undefined }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const [stamps, setStamps] = useState(String(initial?.stampsRequired ?? 9));
  const [nameAz, setNameAz] = useState(initial?.rewardName?.az ?? '');
  const [nameEn, setNameEn] = useState(initial?.rewardName?.en ?? '');
  const [nameRu, setNameRu] = useState(initial?.rewardName?.ru ?? '');
  const [validity, setValidity] = useState(initial?.validityDays ? String(initial.validityDays) : '');
  const [issued, setIssued] = useState<number>();
  const [impact, setImpact] = useState<RewardImpact>();
  const withStepUp = useStepUp();
  const save = useMutation({
    // "Hamıya dərhal" kimliyin təsdiqini tələb edir (2FA yoxdursa şifrə) — StepUpProvider pəncərə açır
    mutationFn: (existingCards: ExistingCards) =>
      withStepUp(() =>
        settingsApi.updateRewardRule({
          stampsRequired: Number(stamps),
          rewardName: { az: nameAz.trim(), en: nameEn.trim() || undefined, ru: nameRu.trim() || undefined },
          validityDays: validity.trim() ? Number(validity) : undefined,
          existingCards,
        }),
      ),
    onSuccess: (updated) => {
      setImpact(undefined);
      setIssued(updated.giftCardsIssued ?? 0);
      queryClient.setQueryData(REWARD_KEY, updated);
      void queryClient.invalidateQueries({ queryKey: ['public-company'] });
    },
  });
  const checkImpact = useMutation({
    mutationFn: () => settingsApi.rewardImpact(Number(stamps)),
    onSuccess: (result) => (result.cardsInProgress ? setImpact(result) : save.mutate('KEEP_RULE')),
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    // Möhür sayı dəyişirsə, yarımçıq kartlarla nə ediləcəyini sahib seçir (təsir əvvəlcədən göstərilir)
    const changed = initial?.stampsRequired !== undefined && Number(stamps) !== initial.stampsRequired;
    if (changed) {
      checkImpact.mutate();
    } else {
      save.mutate('KEEP_RULE');
    }
  };
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Gift className="size-5 text-accent" aria-hidden="true" />
          {t('admin:settings.reward.title')}
        </CardTitle>
        <CardDescription>{t('admin:settings.reward.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-4" onSubmit={(event) => void submit(event)}>
          {save.isSuccess ? (
            <Alert tone="success">
              {t('admin:settings.saved')}
              {issued ? ` ${t('admin:settings.reward.issued', { count: issued })}` : null}
            </Alert>
          ) : null}
          <ApiErrorAlert error={save.error ?? checkImpact.error} />
          <Field id="stamps-required" label={t('admin:settings.reward.stamps')} hint={t('admin:settings.reward.stampsHint')}>
            <Input type="number" min={1} max={50} required value={stamps} onChange={(e) => setStamps(e.target.value)} />
          </Field>
          <fieldset className="flex flex-col gap-3">
            <legend className="mb-1 text-sm font-medium">{t('admin:settings.reward.name')}</legend>
            <Field id="reward-name-az" label={t('admin:settings.reward.nameAz')}>
              <Input required maxLength={120} value={nameAz} onChange={(e) => setNameAz(e.target.value)} />
            </Field>
            <Field id="reward-name-en" label={t('admin:settings.reward.nameEn')}>
              <Input maxLength={120} value={nameEn} onChange={(e) => setNameEn(e.target.value)} />
            </Field>
            <Field id="reward-name-ru" label={t('admin:settings.reward.nameRu')} hint={t('admin:settings.reward.translationHint')}>
              <Input maxLength={120} value={nameRu} onChange={(e) => setNameRu(e.target.value)} />
            </Field>
          </fieldset>
          <Field id="validity-days" label={t('admin:settings.reward.validity')} hint={t('admin:settings.reward.validityHint')}>
            <Input type="number" min={1} max={3650} value={validity} onChange={(e) => setValidity(e.target.value)} />
          </Field>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Button type="submit" disabled={save.isPending || checkImpact.isPending}>
              {t('common:actions.save')}
            </Button>
            {initial?.version ? (
              <span className="text-xs text-muted-foreground">{t('admin:settings.reward.version', { version: initial.version })}</span>
            ) : null}
          </div>
        </form>
      </CardContent>
      {impact ? (
        <ExistingCardsDialog
          impact={impact}
          to={Number(stamps)}
          saving={save.isPending}
          onCancel={() => setImpact(undefined)}
          onConfirm={(choice) => save.mutate(choice)}
        />
      ) : null}
    </Card>
  );
}

/** Möhür sayı dəyişəndə yarımçıq kartlar: köhnə qayda ilə bitsin (standart) və ya yeni qayda hamıya dərhal. */
function ExistingCardsDialog({
  impact,
  to,
  saving,
  onCancel,
  onConfirm,
}: {
  impact: RewardImpact;
  to: number;
  saving: boolean;
  onCancel: () => void;
  onConfirm: (choice: ExistingCards) => void;
}) {
  const { t } = useTranslation(['admin', 'common']);
  const [choice, setChoice] = useState<ExistingCards>('KEEP_RULE');
  const from = impact.currentStampsRequired ?? to;
  const giftCards = impact.giftCardsIfAppliedNow ?? 0;
  const options: { value: ExistingCards; title: string; hint: string }[] = [
    {
      value: 'KEEP_RULE',
      title: t('admin:settings.reward.change.keep', { count: from }),
      hint: t('admin:settings.reward.change.keepHint'),
    },
    {
      value: 'APPLY_NOW',
      title: t('admin:settings.reward.change.applyNow'),
      hint: giftCards ? t('admin:settings.reward.change.applyNowHint', { count: giftCards }) : t('admin:settings.reward.change.applyNowNone'),
    },
  ];
  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onCancel())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('admin:settings.reward.change.title', { from, to })}</DialogTitle>
          <DialogDescription>{t('admin:settings.reward.change.inProgress', { count: impact.cardsInProgress ?? 0 })}</DialogDescription>
        </DialogHeader>
        <fieldset className="flex flex-col gap-3">
          <legend className="sr-only">{t('admin:settings.reward.change.legend')}</legend>
          {options.map((option) => (
            <label
              key={option.value}
              className="flex cursor-pointer gap-3 rounded-xl border border-border p-4 has-[:checked]:border-primary has-[:checked]:bg-primary/5"
            >
              <input
                type="radio"
                name="existing-cards"
                value={option.value}
                checked={choice === option.value}
                onChange={() => setChoice(option.value)}
                className="mt-1 size-4 accent-[var(--primary)]"
              />
              <span>
                <span className="block font-medium">{option.title}</span>
                <span className="block text-sm text-muted-foreground">{option.hint}</span>
              </span>
            </label>
          ))}
        </fieldset>
        <DialogFooter>
          <Button variant="outline" onClick={onCancel}>
            {t('common:actions.cancel')}
          </Button>
          <Button onClick={() => onConfirm(choice)} disabled={saving}>
            {t('common:actions.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
