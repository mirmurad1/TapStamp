import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BadgeCheck } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { currentLanguage } from '@/shared/i18n/setup';
import { formatDate } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';

import { platformPlanApi, type PlanName, type Tenant, type TenantPlan } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { TENANTS_KEY } from './keys';

const PLANS: readonly PlanName[] = ['BASIC', 'STANDARD', 'PREMIUM'];
type Status = 'TRIAL' | 'ACTIVE';

/** Şirkətlər siyahısında: paket və vəziyyət (sınaq, sınaq bitib, ödənişli). */
export function TenantPlanSummary({ tenant }: { tenant: Tenant }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  return (
    <div className="flex flex-col gap-1">
      <span className="font-medium">{t(`plan.names.${(tenant.plan ?? 'PREMIUM') as PlanName}`)}</span>
      <PlanStatusBadge status={tenant.planStatus} expired={tenant.planExpired} />
      {tenant.planStatus === 'TRIAL' && tenant.trialEndsAt ? (
        <span className="text-xs text-muted-foreground">
          {t('platform.plan.until', { date: formatDate(tenant.trialEndsAt, lang) })}
        </span>
      ) : null}
    </div>
  );
}

function PlanStatusBadge({ status, expired }: { status?: string; expired?: boolean }) {
  const { t } = useTranslation('admin');
  if (expired) {
    return <Badge tone="danger">{t('plan.status.EXPIRED')}</Badge>;
  }
  return status === 'TRIAL' ? (
    <Badge tone="warning">{t('plan.status.TRIAL')}</Badge>
  ) : (
    <Badge tone="success">{t('plan.status.ACTIVE')}</Badge>
  );
}

/** Şirkətin səhifəsində: paket, sınaq müddəti (hər paket üçün bir dəfə), ödənişin qeyd olunması, əlavə filiallar. */
export function TenantPlanCard({ tenantId }: { tenantId: string }) {
  const { t } = useTranslation('admin');
  const plan = useQuery({ queryKey: ['tenant-plan', tenantId], queryFn: () => platformPlanApi.get(tenantId) });
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-2">
          <BadgeCheck className="size-5 text-primary" aria-hidden="true" />
          {t('platform.plan.title')}
          {plan.data ? <PlanStatusBadge status={plan.data.status} expired={plan.data.expired} /> : null}
        </CardTitle>
        <CardDescription>{t('platform.plan.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        <ApiErrorAlert error={plan.error} />
        {plan.isPending ? <InlineLoading /> : null}
        {plan.data ? (
          <TenantPlanForm key={`${plan.data.plan}-${plan.data.status}-${plan.data.trialEndsAt}`} tenantId={tenantId} data={plan.data} />
        ) : null}
      </CardContent>
    </Card>
  );
}

function TenantPlanForm({ tenantId, data }: { tenantId: string; data: TenantPlan }) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const [plan, setPlan] = useState<PlanName>((data.plan ?? 'PREMIUM') as PlanName);
  const [status, setStatus] = useState<Status>((data.status ?? 'ACTIVE') as Status);
  const [extraBranches, setExtraBranches] = useState(String(data.extraBranches ?? 0));
  const usedTrials = new Set((data.trials ?? []).map((trial) => trial.plan));
  // Davam edən sınaqda eyni paket — müddət dəyişmir (yalnız əlavə filiallar)
  const ongoingTrial = data.status === 'TRIAL' && !data.expired && data.plan === plan;
  const trialUsed = status === 'TRIAL' && usedTrials.has(plan) && !ongoingTrial;

  const save = useMutation({
    mutationFn: () =>
      platformPlanApi.update(tenantId, {
        plan,
        status,
        extraBranches: plan === 'PREMIUM' ? Number(extraBranches) || 0 : 0,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['tenant-plan', tenantId], updated);
      void queryClient.invalidateQueries({ queryKey: TENANTS_KEY });
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };

  return (
    <form className="flex flex-col gap-4" onSubmit={submit}>
      {save.isSuccess ? <Alert tone="success">{t('admin:settings.saved')}</Alert> : null}
      <ApiErrorAlert error={save.error} />
      <div className="flex flex-col gap-1 text-sm">
        <p>
          {t('admin:platform.plan.current', { plan: t(`admin:plan.names.${(data.plan ?? 'PREMIUM') as PlanName}`) })}
          {data.status === 'TRIAL' && data.trialEndsAt
            ? ` · ${t(data.expired ? 'admin:platform.plan.ended' : 'admin:platform.plan.until', { date: formatDate(data.trialEndsAt, lang) })}`
            : null}
        </p>
        <p className="text-muted-foreground">
          {t('admin:plan.card.limits', {
            branches: data.branchLimit ?? 0,
            staff: data.staffLimit == null ? t('admin:plan.card.unlimited') : data.staffLimit,
          })}
        </p>
        {(data.trials ?? []).length > 0 ? (
          <p className="text-muted-foreground">
            {t('admin:platform.plan.trialsUsed', {
              list: (data.trials ?? [])
                .map((trial) => `${t(`admin:plan.names.${trial.plan as PlanName}`)} (${formatDate(trial.startedAt ?? '', lang)})`)
                .join(', '),
            })}
          </p>
        ) : null}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field id={`plan-${tenantId}`} label={t('admin:platform.plan.plan')}>
          <Select value={plan} onChange={(event) => setPlan(event.target.value as PlanName)}>
            {PLANS.map((value) => (
              <option key={value} value={value}>
                {t(`admin:plan.names.${value}`)}
              </option>
            ))}
          </Select>
        </Field>
        <Field id={`plan-status-${tenantId}`} label={t('admin:platform.plan.status')}>
          <Select value={status} onChange={(event) => setStatus(event.target.value as Status)}>
            <option value="TRIAL">{t('admin:platform.plan.trialOption')}</option>
            <option value="ACTIVE">{t('admin:platform.plan.activeOption')}</option>
          </Select>
        </Field>
        {plan === 'PREMIUM' ? (
          <Field id={`plan-extra-${tenantId}`} label={t('admin:platform.plan.extraBranches')} hint={t('admin:platform.plan.extraBranchesHint')}>
            <Input type="number" min={0} max={100} value={extraBranches} onChange={(event) => setExtraBranches(event.target.value)} />
          </Field>
        ) : null}
      </div>
      {trialUsed ? <Alert tone="error">{t('admin:platform.plan.trialUsed')}</Alert> : null}
      <Button type="submit" className="self-start" disabled={save.isPending || trialUsed}>
        {t('common:actions.save')}
      </Button>
    </form>
  );
}
