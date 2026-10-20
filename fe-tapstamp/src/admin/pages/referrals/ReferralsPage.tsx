import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { UserPlus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { currentLanguage } from '@/shared/i18n/setup';
import { formatDate, formatNumber } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Select } from '@/shared/ui/input';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { referralsApi, type ReferralActivity, type ReferralSettings } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';

const SETTINGS_KEY = ['referral-settings'] as const;
const REWARD_ON = ['FIRST_VISIT', 'REGISTRATION'] as const;
type RewardOn = (typeof REWARD_ON)[number];
const STATUSES = ['PENDING', 'REWARDED', 'EXPIRED', 'LIMIT_REACHED'] as const;
const ACTIVITIES: readonly ReferralActivity[] = ['ALL', 'NONE', 'ONCE', 'RETURNED'];
type ReferralStatus = (typeof STATUSES)[number];

function referralStatus(value: string | undefined): ReferralStatus {
  return STATUSES.find((status) => status === value) ?? 'PENDING';
}

const TONES: Record<ReferralStatus, 'neutral' | 'success' | 'warning' | 'danger'> = {
  PENDING: 'warning',
  REWARDED: 'success',
  EXPIRED: 'neutral',
  LIMIT_REACHED: 'danger',
};

/** Dost dəvəti (yalnız sahib): proqramın parametrləri, nəticələr və dəvətlərin siyahısı. */
export function ReferralsPage() {
  const { t } = useTranslation('admin');
  const settings = useQuery({ queryKey: SETTINGS_KEY, queryFn: referralsApi.settings });
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">{t('referrals.title')}</h1>
        <p className="mt-1 text-muted-foreground">{t('referrals.subtitle')}</p>
      </div>
      <ApiErrorAlert error={settings.error} />
      {settings.isPending ? <InlineLoading /> : null}
      {settings.data ? <SettingsForm initial={settings.data} /> : null}
      <SummaryCard />
      <ReferralList />
    </div>
  );
}

function NumberSelect({ id, label, hint, value, options, onChange }: {
  id: string;
  label: string;
  hint?: string;
  value: number;
  options: readonly number[];
  onChange: (value: number) => void;
}) {
  return (
    <Field id={id} label={label} hint={hint}>
      <Select value={value} onChange={(event) => onChange(Number(event.target.value))}>
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </Select>
    </Field>
  );
}

function SettingsForm({ initial }: { initial: ReferralSettings }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const [enabled, setEnabled] = useState(Boolean(initial.enabled));
  const [rewardOn, setRewardOn] = useState<RewardOn>(initial.rewardOn === 'REGISTRATION' ? 'REGISTRATION' : 'FIRST_VISIT');
  const [referrerStamps, setReferrerStamps] = useState(initial.referrerStamps ?? 1);
  const [friendStamps, setFriendStamps] = useState(initial.friendStamps ?? 1);
  const [monthlyCap, setMonthlyCap] = useState(initial.monthlyCap ?? 5);
  const [validDays, setValidDays] = useState(initial.validDays ?? 30);
  const save = useMutation({
    mutationFn: () => referralsApi.updateSettings({ enabled, rewardOn, referrerStamps, friendStamps, monthlyCap, validDays }),
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
          <UserPlus className="size-5 text-primary" aria-hidden="true" />
          {t('admin:referrals.settings.title')}
        </CardTitle>
        <CardDescription>{t('admin:referrals.settings.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-4" onSubmit={submit}>
          {save.isSuccess ? <Alert tone="success">{t('admin:settings.saved')}</Alert> : null}
          <ApiErrorAlert error={save.error} />
          <label className="flex items-start gap-3 text-sm">
            <input
              type="checkbox"
              checked={enabled}
              onChange={(event) => setEnabled(event.target.checked)}
              className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]"
            />
            <span>
              <span className="block font-medium">{t('admin:referrals.settings.enabled')}</span>
              <span className="block text-muted-foreground">{t('admin:referrals.settings.enabledHint')}</span>
            </span>
          </label>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium">{t('admin:referrals.settings.rewardOn')}</legend>
            {REWARD_ON.map((value) => (
              <label key={value} className="flex items-start gap-3 rounded-xl border border-border p-3 text-sm">
                <input
                  type="radio"
                  name="reward-on"
                  value={value}
                  checked={rewardOn === value}
                  onChange={() => setRewardOn(value)}
                  className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]"
                />
                <span>
                  <span className="block font-medium">{t(`admin:referrals.settings.rewardOnOptions.${value}.title`)}</span>
                  <span className="block text-muted-foreground">{t(`admin:referrals.settings.rewardOnOptions.${value}.hint`)}</span>
                </span>
              </label>
            ))}
            {rewardOn === 'REGISTRATION' ? (
              <Alert tone="error">{t('admin:referrals.settings.registrationWarning')}</Alert>
            ) : null}
          </fieldset>
          <div className="grid gap-4 sm:grid-cols-2">
            <NumberSelect id="referrer-stamps" label={t('admin:referrals.settings.referrerStamps')} value={referrerStamps}
              options={[1, 2, 3]} onChange={setReferrerStamps} />
            <NumberSelect id="friend-stamps" label={t('admin:referrals.settings.friendStamps')} value={friendStamps}
              options={[1, 2, 3]} onChange={setFriendStamps} />
            <NumberSelect id="monthly-cap" label={t('admin:referrals.settings.monthlyCap')}
              hint={t('admin:referrals.settings.monthlyCapHint')} value={monthlyCap}
              options={[1, 3, 5, 10, 20, 50]} onChange={setMonthlyCap} />
            {rewardOn === 'FIRST_VISIT' ? (
              <NumberSelect id="valid-days" label={t('admin:referrals.settings.validDays')}
                hint={t('admin:referrals.settings.validDaysHint')} value={validDays}
                options={[7, 14, 30, 60, 90]} onChange={setValidDays} />
            ) : null}
          </div>
          <Button type="submit" disabled={save.isPending} className="self-start">
            {t('common:actions.save')}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function SummaryCard() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const summary = useQuery({ queryKey: ['referral-summary'], queryFn: referralsApi.summary });
  const data = summary.data;
  const items = [
    { label: t('referrals.summary.total'), value: data?.total },
    { label: t('referrals.summary.rewarded'), value: data?.rewarded },
    { label: t('referrals.summary.pending'), value: data?.pending },
    { label: t('referrals.summary.bonusStamps'), value: data?.bonusStamps },
  ];
  return (
    <Card>
      <CardContent className="flex flex-col gap-3 pt-5 sm:pt-6">
        <ApiErrorAlert error={summary.error} />
        <dl className="grid gap-4 sm:grid-cols-4">
          {items.map((item) => (
            <div key={item.label} className="rounded-xl bg-muted px-4 py-3">
              <dt className="text-sm text-muted-foreground">{item.label}</dt>
              <dd className="mt-1 font-display text-2xl font-semibold">
                {item.value === undefined ? '–' : formatNumber(item.value, lang)}
              </dd>
            </div>
          ))}
        </dl>
        {data && (data.expired ?? 0) + (data.limitReached ?? 0) > 0 ? (
          <p className="text-sm text-muted-foreground">
            {t('referrals.summary.notCounted', { expired: data.expired ?? 0, limit: data.limitReached ?? 0 })}
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}

function ReferralList() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const [activity, setActivity] = useState<ReferralActivity>('ALL');
  const paging = usePagination(activity);
  const referrals = useQuery({
    queryKey: ['referrals', activity, paging.page, paging.size],
    queryFn: () => referralsApi.list({ activity, page: paging.page, size: paging.size }),
    placeholderData: keepPreviousData,
  });
  const items = referrals.data?.items ?? [];
  const person = (id: string | undefined, name: string | undefined, phone: string | undefined) => (
    <Link to={`/customers/${id ?? ''}`} className="font-medium underline-offset-4 hover:underline">
      {name || t('customers.noName')}
      <span className="block font-normal text-muted-foreground" dir="ltr">
        {phone}
      </span>
    </Link>
  );
  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-end justify-between gap-3">
        <CardTitle>{t('referrals.list.title')}</CardTitle>
        <label className="flex flex-col gap-1 text-sm font-medium">
          {t('referrals.list.activity')}
          <Select value={activity} onChange={(event) => setActivity(event.target.value as ReferralActivity)}>
            {ACTIVITIES.map((value) => (
              <option key={value} value={value}>
                {t(`referrals.activity.${value}`)}
              </option>
            ))}
          </Select>
        </label>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <ApiErrorAlert error={referrals.error} />
        {referrals.isPending ? <InlineLoading /> : null}
        {referrals.data && items.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">{t('referrals.list.empty')}</p>
        ) : null}
        {items.length > 0 ? (
          <div className="-mx-5 overflow-x-auto sm:-mx-6">
            <table className="w-full min-w-[48rem] text-left text-sm">
              <thead className="border-b border-border text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('referrals.list.referrer')}</th>
                  <th scope="col" className="px-3 py-2 font-medium">{t('referrals.list.friend')}</th>
                  <th scope="col" className="px-3 py-2 font-medium">{t('referrals.list.status')}</th>
                  <th scope="col" className="px-3 py-2 text-right font-medium">{t('referrals.list.visits')}</th>
                  <th scope="col" className="px-3 py-2 font-medium">{t('referrals.list.lastVisit')}</th>
                  <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('referrals.list.date')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border align-top">
                {items.map((item) => {
                  const status = referralStatus(item.status);
                  return (
                    <tr key={item.id}>
                      <td className="px-5 py-3 sm:px-6">{person(item.referrerId, item.referrerName, item.referrerPhone)}</td>
                      <td className="px-3 py-3">{person(item.friendId, item.friendName, item.friendPhone)}</td>
                      <td className="px-3 py-3">
                        <Badge tone={TONES[status]}>{t(`referrals.status.${status}`)}</Badge>
                      </td>
                      <td className="px-3 py-3 text-right tabular-nums">{formatNumber(item.visits ?? 0, lang)}</td>
                      <td className="px-3 py-3 text-muted-foreground">
                        {item.lastVisitAt ? formatDate(item.lastVisitAt, lang) : '—'}
                      </td>
                      <td className="px-5 py-3 text-muted-foreground sm:px-6">
                        {item.createdAt ? formatDate(item.createdAt, lang) : null}
                        {item.rewardedAt ? (
                          <span className="block">{t('referrals.list.rewardedAt', { date: formatDate(item.rewardedAt, lang) })}</span>
                        ) : null}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}
        <Pagination state={paging} total={referrals.data?.total ?? 0} />
      </CardContent>
    </Card>
  );
}
