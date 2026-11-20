import { useQuery } from '@tanstack/react-query';
import { Check, Circle, CircleDashed, ListChecks } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { cn } from '@/shared/lib/cn';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

import { platformApi } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { setupStepKey } from '../dashboard/setupSteps';

/**
 * Platforma: şirkətin başlanğıc siyahısı — məcburi addımlardan neçəsi tamamlanıb, hansı qalıb və paketdəki müştəri cəlbi
 * imkanlarından hansılar açılıb. Yarımçıq qurulan şirkətə vaxtında kömək etmək üçün.
 */
export function TenantSetupCard({ tenantId }: { tenantId: string }) {
  const { t } = useTranslation('admin');
  const setup = useQuery({ queryKey: ['platform-tenant-setup', tenantId], queryFn: () => platformApi.setup(tenantId) });
  const data = setup.data;
  const required = data?.required ?? [];
  const recommended = data?.recommended ?? [];
  const stepTitle = (value: string | undefined) => {
    const key = setupStepKey(value);
    return key ? t(`setup.steps.${key}.title`) : value;
  };
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <ListChecks className="size-5 text-primary" aria-hidden="true" />
          {t('platform.detail.setup.title')}
          {data ? (
            <span className={cn('ml-auto text-sm font-semibold tabular-nums', data.requiredComplete ? 'text-[var(--success-ink)]' : 'text-primary')}>
              {t('setup.progress', { done: data.requiredDone ?? 0, total: required.length })}
            </span>
          ) : null}
        </CardTitle>
        <CardDescription>{t('platform.detail.setup.subtitle')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <ApiErrorAlert error={setup.error} />
        {data ? (
          <div className="h-2 overflow-hidden rounded-full bg-primary/15" role="progressbar" aria-label={t('platform.detail.setup.title')}
            aria-valuemin={0} aria-valuemax={required.length} aria-valuenow={data.requiredDone ?? 0}>
            <div className={cn('h-full rounded-full', data.requiredComplete ? 'bg-success' : 'bg-primary')}
              style={{ width: `${((data.requiredDone ?? 0) / Math.max(required.length, 1)) * 100}%` }} />
          </div>
        ) : null}
        <ul className="flex flex-col gap-2">
          {required.map((step) => (
            <li key={step.key} className="flex items-center gap-2 text-sm">
              {step.done ? (
                <Check className="size-4 shrink-0 text-[var(--success-ink)]" aria-hidden="true" />
              ) : step.partial ? (
                <CircleDashed className="size-4 shrink-0 text-primary" aria-hidden="true" />
              ) : (
                <Circle className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
              )}
              <span className={cn(!step.done && 'font-semibold')}>
                {step.key === 'NFC_TAG' ? t('platform.detail.setup.nfc') : stepTitle(step.key)}
              </span>
              {step.partial ? <span className="text-xs text-muted-foreground">— {t('platform.detail.setup.partial')}</span> : null}
              {step.key === 'NFC_TAG' && !step.done ? (
                <span className="text-xs text-muted-foreground">— {t('platform.detail.setup.nfcHint')}</span>
              ) : null}
            </li>
          ))}
        </ul>
        {recommended.length > 0 ? (
          <div className="flex flex-col gap-2 border-t border-border pt-3">
            <p className="text-xs font-semibold text-muted-foreground">
              {t('platform.detail.setup.recommended', { enabled: recommended.filter((step) => step.done).length, total: recommended.length })}
            </p>
            <div className="flex flex-wrap gap-1.5">
              {recommended.map((step) => (
                <span
                  key={step.key}
                  className={cn(
                    'inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium',
                    step.done ? 'bg-success/15 text-[var(--success-ink)]' : 'bg-muted text-muted-foreground',
                  )}
                >
                  {step.done ? <Check className="size-3" aria-hidden="true" /> : null}
                  {stepTitle(step.key)}
                </span>
              ))}
            </div>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
