import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Gift, Stamp } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input } from '@/shared/ui/input';

import { settingsApi, type RewardSettings, type StampPolicy } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';

const POLICY_KEY = ['settings-stamp-policy'] as const;
const REWARD_KEY = ['settings-reward-rule'] as const;

/** Sahibin loyallıq parametrləri: möhür qaydası və hədiyyə (möhür sayı, adı 3 dildə, kartın müddəti). */
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
      <ApiErrorAlert error={policy.error ?? reward.error} />
      {policy.isPending || reward.isPending ? <InlineLoading /> : null}
      <div className="grid gap-6 xl:grid-cols-2">
        {policy.data ? <StampPolicyForm initial={policy.data} /> : null}
        {reward.isSuccess ? <RewardForm initial={reward.data ?? undefined} /> : null}
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
  const save = useMutation({
    mutationFn: () =>
      settingsApi.updateRewardRule({
        stampsRequired: Number(stamps),
        rewardName: { az: nameAz.trim(), en: nameEn.trim() || undefined, ru: nameRu.trim() || undefined },
        validityDays: validity.trim() ? Number(validity) : undefined,
      }),
    onSuccess: (updated) => {
      setIssued(updated.giftCardsIssued ?? 0);
      queryClient.setQueryData(REWARD_KEY, updated);
      void queryClient.invalidateQueries({ queryKey: ['public-company'] });
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    const lowered = initial?.stampsRequired !== undefined && Number(stamps) < initial.stampsRequired;
    // Hədd azaldılırsa, kartı artıq dolmuş müştərilərə hədiyyə kartı dərhal veriləcək — təsdiq istənilir
    if (lowered && !window.confirm(t('admin:settings.reward.confirm', { count: Number(stamps) }))) {
      return;
    }
    save.mutate();
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
        <form className="flex flex-col gap-4" onSubmit={submit}>
          {save.isSuccess ? (
            <Alert tone="success">
              {t('admin:settings.saved')}
              {issued ? ` ${t('admin:settings.reward.issued', { count: issued })}` : null}
            </Alert>
          ) : null}
          <ApiErrorAlert error={save.error} />
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
            <Button type="submit" disabled={save.isPending}>
              {t('common:actions.save')}
            </Button>
            {initial?.version ? (
              <span className="text-xs text-muted-foreground">{t('admin:settings.reward.version', { version: initial.version })}</span>
            ) : null}
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
