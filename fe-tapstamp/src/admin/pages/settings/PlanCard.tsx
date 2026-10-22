import { BadgeCheck } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { currentLanguage } from '@/shared/i18n/setup';
import { formatDate } from '@/shared/lib/format';
import { usePlan, type PlanName } from '@/shared/plan/plan';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

const DAY_MS = 24 * 60 * 60 * 1000;

/** Parametrlərdə: şirkətin paketi, sınaq müddətinin sonu və limitlər. Paketi yalnız platforma dəyişir. */
export function PlanCard() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const plan = usePlan();
  const data = plan.data;
  if (!data) {
    return null;
  }
  const trial = data.status === 'TRIAL' && data.trialEndsAt;
  const daysLeft = trial ? Math.max(0, Math.ceil((Date.parse(data.trialEndsAt!) - Date.now()) / DAY_MS)) : 0;
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-2">
          <BadgeCheck className="size-5 text-primary" aria-hidden="true" />
          {t('plan.card.title', { plan: t(`plan.names.${(data.plan ?? 'PREMIUM') as PlanName}`) })}
          <Badge tone={trial ? 'warning' : 'success'}>{trial ? t('plan.status.TRIAL') : t('plan.status.ACTIVE')}</Badge>
        </CardTitle>
        <CardDescription>{t('plan.card.description')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3 text-sm">
        {trial ? (
          <Alert tone={daysLeft <= 3 ? 'error' : 'info'}>
            {t('plan.card.trialEnds', { date: formatDate(data.trialEndsAt!, lang), count: daysLeft })}
          </Alert>
        ) : null}
        <p>
          {t('plan.card.limits', {
            branches: data.branchLimit ?? 0,
            staff: data.staffLimit == null ? t('plan.card.unlimited') : data.staffLimit,
          })}
        </p>
      </CardContent>
    </Card>
  );
}
