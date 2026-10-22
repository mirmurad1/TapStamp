import { useQueryClient } from '@tanstack/react-query';
import { CalendarX2 } from 'lucide-react';
import { useEffect, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { isApiError } from '@/shared/api/client';

import { PLAN_KEY, usePlan } from './plan';

/**
 * Sınaq müddəti bitmiş şirkət: tətbiqin yerinə "xidmət dayandırılıb" ekranı. Backend bu halda şirkətin bütün
 * API-sinə {@code PLAN_TRIAL_EXPIRED} qaytarır; hər hansı sorğu bu xətanı alanda paket yenidən oxunur və ekran
 * dərhal dəyişir. Platforma domenində paket yoxdur (404) — tətbiq olduğu kimi göstərilir.
 */
export function PlanGate({ audience, loading, children }: { audience: 'staff' | 'customer'; loading: ReactNode; children: ReactNode }) {
  const queryClient = useQueryClient();
  const plan = usePlan();

  useEffect(() => {
    const refreshOnExpiry = (error: unknown) => {
      if (isApiError(error, 'PLAN_TRIAL_EXPIRED')) {
        void queryClient.invalidateQueries({ queryKey: PLAN_KEY });
      }
    };
    const unsubscribeQueries = queryClient.getQueryCache().subscribe((event) => {
      if (event.type === 'updated' && event.action.type === 'error') {
        refreshOnExpiry(event.action.error);
      }
    });
    const unsubscribeMutations = queryClient.getMutationCache().subscribe((event) => {
      if (event.type === 'updated' && event.action.type === 'error') {
        refreshOnExpiry(event.action.error);
      }
    });
    return () => {
      unsubscribeQueries();
      unsubscribeMutations();
    };
  }, [queryClient]);

  if (plan.isPending) {
    return loading;
  }
  if (plan.data?.expired) {
    return <TrialExpired audience={audience} />;
  }
  return children;
}

function TrialExpired({ audience }: { audience: 'staff' | 'customer' }) {
  const { t } = useTranslation('common');
  return (
    <main className="grid min-h-dvh place-items-center px-4">
      <div className="flex max-w-md flex-col items-center gap-4 text-center">
        <span className="grid size-14 place-items-center rounded-full bg-muted">
          <CalendarX2 className="size-7 text-muted-foreground" aria-hidden="true" />
        </span>
        <h1 className="font-display text-2xl font-semibold">{t(`plan.expired.${audience}.title`)}</h1>
        <p className="text-muted-foreground">{t(`plan.expired.${audience}.body`)}</p>
      </div>
    </main>
  );
}
