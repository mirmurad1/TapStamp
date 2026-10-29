import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BellRing } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { currentLanguage } from '@/shared/i18n/setup';
import { formatNumber, formatPercent } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Select } from '@/shared/ui/input';

import { remindersApi, type ReminderSettings } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';

const SETTINGS_KEY = ['reminder-settings'] as const;
const KINDS = ['NEAR_REWARD', 'UNUSED_GIFT', 'WINBACK'] as const;
type ReminderKind = (typeof KINDS)[number];
const WINBACK_DAYS = [30, 60, 90] as const;

function reminderKind(value: string | undefined): ReminderKind {
  return KINDS.find((kind) => kind === value) ?? 'WINBACK';
}

/** Avtomatik xatırlatmalar (yalnız sahib): yalnız web push ilə, pulsuz. */
export function RemindersPage() {
  const { t } = useTranslation('admin');
  const settings = useQuery({ queryKey: SETTINGS_KEY, queryFn: remindersApi.settings });
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">{t('reminders.title')}</h1>
        <p className="mt-1 text-muted-foreground">{t('reminders.subtitle')}</p>
      </div>
      <ApiErrorAlert error={settings.error} />
      {settings.isPending ? <InlineLoading /> : null}
      {settings.data ? <SettingsForm initial={settings.data} /> : null}
      <StatsCard />
    </div>
  );
}

function Toggle({ checked, onChange, title, hint }: { checked: boolean; onChange: (value: boolean) => void; title: string; hint: string }) {
  return (
    <label className="flex items-start gap-3 rounded-xl border border-border p-3 text-sm">
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]"
      />
      <span>
        <span className="block font-medium">{title}</span>
        <span className="block text-muted-foreground">{hint}</span>
      </span>
    </label>
  );
}

function SettingsForm({ initial }: { initial: ReminderSettings }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const [nearReward, setNearReward] = useState(Boolean(initial.nearRewardEnabled));
  const [unusedGift, setUnusedGift] = useState(Boolean(initial.unusedGiftEnabled));
  const [winback, setWinback] = useState(Boolean(initial.winbackEnabled));
  const [winbackDays, setWinbackDays] = useState(initial.winbackDays ?? 30);
  const save = useMutation({
    mutationFn: () =>
      remindersApi.updateSettings({
        nearRewardEnabled: nearReward,
        unusedGiftEnabled: unusedGift,
        winbackEnabled: winback,
        winbackDays,
      }),
    onSuccess: (updated) => queryClient.setQueryData(SETTINGS_KEY, updated),
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <BellRing className="size-5 text-primary" aria-hidden="true" />
          {t('admin:reminders.settings.title')}
        </CardTitle>
        <CardDescription>{t('admin:reminders.settings.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-3" onSubmit={submit}>
          {save.isSuccess ? <Alert tone="success">{t('admin:settings.saved')}</Alert> : null}
          <ApiErrorAlert error={save.error} />
          <Toggle checked={nearReward} onChange={setNearReward} title={t('admin:reminders.kinds.NEAR_REWARD.title')}
            hint={t('admin:reminders.kinds.NEAR_REWARD.hint')} />
          <Toggle checked={unusedGift} onChange={setUnusedGift} title={t('admin:reminders.kinds.UNUSED_GIFT.title')}
            hint={t('admin:reminders.kinds.UNUSED_GIFT.hint')} />
          <Toggle checked={winback} onChange={setWinback} title={t('admin:reminders.kinds.WINBACK.title')}
            hint={t('admin:reminders.kinds.WINBACK.hint', { days: winbackDays })} />
          {winback ? (
            <div className="sm:max-w-xs">
              <Field id="winback-days" label={t('admin:reminders.settings.winbackDays')}>
                <Select value={winbackDays} onChange={(event) => setWinbackDays(Number(event.target.value))}>
                  {WINBACK_DAYS.map((days) => (
                    <option key={days} value={days}>
                      {t('admin:campaigns.new.days', { count: days })}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>
          ) : null}
          <p className="text-xs text-muted-foreground">{t('admin:reminders.settings.rules')}</p>
          <Button type="submit" disabled={save.isPending} className="self-start">
            {t('common:actions.save')}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function StatsCard() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const stats = useQuery({ queryKey: ['reminder-stats'], queryFn: remindersApi.stats });
  const data = stats.data;
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('reminders.stats.title')}</CardTitle>
        <CardDescription>
          {t('reminders.stats.reach', { count: data?.customersWithPush ?? 0, customers: formatNumber(data?.customersWithPush ?? 0, lang) })}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <ApiErrorAlert error={stats.error} />
        {stats.isPending ? <InlineLoading /> : null}
        {data ? (
          <div className="-mx-5 overflow-x-auto sm:-mx-6">
            <table className="w-full min-w-[28rem] text-left text-sm">
              <thead className="border-b border-border text-xs text-muted-foreground">
                <tr>
                  <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('reminders.stats.kind')}</th>
                  <th scope="col" className="px-3 py-2 text-right font-medium">{t('reminders.stats.sent')}</th>
                  <th scope="col" className="px-5 py-2 text-right font-medium sm:px-6">{t('reminders.stats.returned')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {(data.kinds ?? []).map((row) => {
                  const sent = row.sent ?? 0;
                  const returned = row.returned ?? 0;
                  return (
                    <tr key={row.kind}>
                      <td className="px-5 py-2 sm:px-6">{t(`reminders.kinds.${reminderKind(row.kind)}.title`)}</td>
                      <td className="px-3 py-2 text-right tabular-nums">{formatNumber(sent, lang)}</td>
                      <td className="px-5 py-2 text-right tabular-nums sm:px-6">
                        {formatNumber(returned, lang)}
                        {sent > 0 ? <span className="ml-1 text-muted-foreground">({formatPercent(returned / sent, lang)})</span> : null}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}
        <p className="text-xs text-muted-foreground">{t('reminders.stats.note')}</p>
      </CardContent>
    </Card>
  );
}
