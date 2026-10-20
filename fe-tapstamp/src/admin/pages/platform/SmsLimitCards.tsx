import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MessageSquareText } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { currentLanguage } from '@/shared/i18n/setup';
import { formatNumber, formatPercent } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input } from '@/shared/ui/input';

import { platformSmsApi, type TenantSmsLimit } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';

/** İstifadə zolağı: dolma faizi, 80%-dən sonra xəbərdarlıq rəngi. */
function UsageBar({ used, limit }: { used: number; limit: number }) {
  const share = limit > 0 ? Math.min(used / limit, 1) : 1;
  const tone = share >= 1 ? 'bg-destructive' : share >= 0.8 ? 'bg-[var(--highlight-ink)]' : 'bg-primary';
  return (
    <div className="h-2 overflow-hidden rounded-full bg-muted" role="presentation">
      <div className={`h-full rounded-full ${tone}`} style={{ width: `${share * 100}%` }} />
    </div>
  );
}

/** Şirkətin səhifəsində: gündəlik SMS limiti (yeni nömrələrə giriş kodları) və bu günkü istifadə. */
export function TenantSmsLimitCard({ tenantId }: { tenantId: string }) {
  const { t } = useTranslation('admin');
  const key = ['tenant-sms-limit', tenantId];
  const limit = useQuery({ queryKey: key, queryFn: () => platformSmsApi.tenantLimit(tenantId) });
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <MessageSquareText className="size-5 text-primary" aria-hidden="true" />
          {t('platform.smsLimit.title')}
        </CardTitle>
        <CardDescription>{t('platform.smsLimit.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        <ApiErrorAlert error={limit.error} />
        {limit.isPending ? <InlineLoading /> : null}
        {limit.data ? <TenantSmsLimitForm key={limit.data.limit} tenantId={tenantId} data={limit.data} /> : null}
      </CardContent>
    </Card>
  );
}

function TenantSmsLimitForm({ tenantId, data }: { tenantId: string; data: TenantSmsLimit }) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const [value, setValue] = useState(String(data.limit ?? ''));
  const save = useMutation({
    mutationFn: (limit: number | null) => platformSmsApi.updateTenantLimit(tenantId, limit),
    onSuccess: (updated) => queryClient.setQueryData(['tenant-sms-limit', tenantId], updated),
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate(Number(value));
  };
  const used = data.usedToday ?? 0;
  const max = data.limit ?? 0;
  return (
    <form className="flex flex-col gap-4" onSubmit={submit}>
      {save.isSuccess ? <Alert tone="success">{t('admin:settings.saved')}</Alert> : null}
      <ApiErrorAlert error={save.error} />
      <div className="flex flex-col gap-2 text-sm">
        <p>
          {t('admin:platform.smsLimit.usedToday', { used: formatNumber(used, lang), limit: formatNumber(max, lang) })}
        </p>
        <UsageBar used={used} limit={max} />
        <p className="text-muted-foreground">
          {data.custom
            ? t('admin:platform.smsLimit.custom', { default: formatNumber(data.defaultLimit ?? 0, lang) })
            : t('admin:platform.smsLimit.default')}
        </p>
      </div>
      <Field id={`sms-limit-${tenantId}`} label={t('admin:platform.smsLimit.limit')} hint={t('admin:platform.smsLimit.limitHint')}>
        <Input type="number" min={10} max={100000} required value={value} onChange={(event) => setValue(event.target.value)} />
      </Field>
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={save.isPending}>
          {t('common:actions.save')}
        </Button>
        {data.custom ? (
          <Button type="button" variant="outline" disabled={save.isPending} onClick={() => save.mutate(null)}>
            {t('admin:platform.smsLimit.reset')}
          </Button>
        ) : null}
      </div>
    </form>
  );
}

/** SMS hesabatında: platformanın bu günkü giriş kodları və gündəlik büdcə. */
export function PlatformSmsBudgetCard() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const budget = useQuery({ queryKey: ['platform-sms-budget'], queryFn: platformSmsApi.budget, refetchInterval: 60_000 });
  const used = budget.data?.usedToday ?? 0;
  const max = budget.data?.dailyBudget ?? 0;
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('platform.smsLimit.budgetTitle')}</CardTitle>
        <CardDescription>{t('platform.smsLimit.budgetDescription')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-2 text-sm">
        <ApiErrorAlert error={budget.error} />
        {budget.isPending ? <InlineLoading /> : null}
        {budget.data ? (
          <>
            <p>
              {t('platform.smsLimit.usedToday', { used: formatNumber(used, lang), limit: formatNumber(max, lang) })}
              {max > 0 ? ` · ${formatPercent(Math.min(used / max, 1), lang)}` : null}
            </p>
            <UsageBar used={used} limit={max} />
          </>
        ) : null}
      </CardContent>
    </Card>
  );
}
