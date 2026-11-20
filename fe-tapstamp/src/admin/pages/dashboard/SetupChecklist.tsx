import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowRight,
  Award,
  BellRing,
  BookOpen,
  Check,
  Gift,
  MessageSquareHeart,
  Nfc,
  Palette,
  Star,
  Store,
  UserPlus,
  Users,
  X,
  Zap,
  type LucideIcon,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { cn } from '@/shared/lib/cn';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';

import { SETUP_KEY, setupApi, type SetupView } from '../../api/adminApi';
import { setupStepKey, type SetupStepKey } from './setupSteps';

/** Addımın ikonu və keçid ünvanı (admin panelində). */
const STEPS: Record<SetupStepKey, { icon: LucideIcon; to: string }> = {
  BRANDING: { icon: Palette, to: '/settings?tab=company' },
  REWARD_RULE: { icon: Gift, to: '/settings?tab=loyalty' },
  BRANCH: { icon: Store, to: '/settings?tab=branches' },
  TEAM: { icon: Users, to: '/team' },
  NFC_TAG: { icon: Nfc, to: '/tags' },
  FEEDBACK: { icon: MessageSquareHeart, to: '/feedback' },
  GOOGLE_REVIEW: { icon: Star, to: '/settings?tab=branches' },
  REFERRALS: { icon: UserPlus, to: '/referrals' },
  TIERS: { icon: Award, to: '/tiers' },
  REMINDERS: { icon: BellRing, to: '/reminders' },
  BONUS_RULES: { icon: Zap, to: '/campaigns?tab=bonus' },
  CATALOG: { icon: BookOpen, to: '/catalog' },
};

type Step = NonNullable<SetupView['required']>[number];

/**
 * Göstəricilər səhifəsinin başında başlanğıc siyahısı (yalnız sahib). Yuxarıda 4 məcburi addım — bütün paketlərdə,
 * tamamlanana qədər gizlədilmir; altında paketdə olan, hələ açılmamış müştəri cəlbi imkanları — məcburi deyil, sahib
 * gizlədə bilər. Hamısı hazırdırsa heç nə göstərilmir.
 */
export function SetupChecklist() {
  const setup = useQuery({ queryKey: SETUP_KEY, queryFn: setupApi.get });
  const data = setup.data;
  if (!data) {
    return null;
  }
  const pendingRecommendations = (data.recommended ?? []).filter((step) => !step.done);
  const showRecommendations = !data.recommendationsHidden && pendingRecommendations.length > 0;
  if (data.requiredComplete && !showRecommendations) {
    return null;
  }
  return (
    <div className="flex flex-col gap-4">
      {data.requiredComplete ? null : <Required data={data} />}
      {showRecommendations ? <Recommendations steps={data.recommended ?? []} /> : null}
    </div>
  );
}

function Required({ data }: { data: SetupView }) {
  const { t } = useTranslation('admin');
  const steps = data.required ?? [];
  const done = data.requiredDone ?? 0;
  return (
    <Card className="overflow-hidden border-primary/25">
      <CardContent className="flex flex-col gap-4 p-5 sm:p-6">
        <div className="flex flex-wrap items-end justify-between gap-x-4 gap-y-2">
          <div>
            <h2 className="text-lg font-bold">{t('setup.title')}</h2>
            <p className="text-sm text-muted-foreground">{t('setup.subtitle')}</p>
          </div>
          <span className="text-sm font-semibold tabular-nums text-primary">
            {t('setup.progress', { done, total: steps.length })}
          </span>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-primary/15" role="progressbar"
          aria-valuemin={0} aria-valuemax={steps.length} aria-valuenow={done} aria-label={t('setup.title')}>
          <div className="h-full rounded-full bg-primary transition-[width] duration-500" style={{ width: `${(done / Math.max(steps.length, 1)) * 100}%` }} />
        </div>
        <ol className="flex flex-col divide-y divide-border rounded-xl border border-border">
          {steps.map((step, index) => (
            <RequiredRow key={step.key} step={step} index={index} />
          ))}
        </ol>
      </CardContent>
    </Card>
  );
}

function RequiredRow({ step, index }: { step: Step; index: number }) {
  const { t } = useTranslation('admin');
  const key = setupStepKey(step.key);
  if (!key) {
    return null;
  }
  const meta = STEPS[key];
  const Icon = meta.icon;
  const done = Boolean(step.done);
  return (
    // Telefonda düymə mətnin altında (sola, nömrə ilə bir xətdə deyil); geniş ekranda sağda
    <li className="flex flex-wrap items-center gap-x-3 gap-y-2 px-3.5 py-3 sm:flex-nowrap sm:px-4">
      <span
        className={cn(
          'grid size-9 shrink-0 place-items-center rounded-full text-sm font-bold',
          done ? 'bg-success/15 text-[var(--success-ink)]' : 'bg-primary/10 text-primary',
        )}
        aria-hidden="true"
      >
        {done ? <Check className="size-5" /> : index + 1}
      </span>
      {/* Telefonda mətn bütün eni tutur (nömrədən başqa), düymə növbəti sətrə keçir */}
      <span className="min-w-0 flex-[1_1_calc(100%-3rem)] sm:flex-1">
        <span className={cn('flex items-center gap-2 font-semibold', done && 'text-muted-foreground')}>
          <Icon className="size-4 shrink-0" aria-hidden="true" />
          {t(`setup.steps.${key}.title`)}
          <span className="sr-only">{done ? t('setup.done') : t('setup.todo')}</span>
        </span>
        <span className="block text-sm text-muted-foreground">
          {step.partial && key === 'BRANCH' ? t('setup.steps.BRANCH.partial') : t(`setup.steps.${key}.hint`)}
        </span>
      </span>
      {done ? null : (
        <Button asChild size="sm" variant={step.partial ? 'outline' : 'default'} className="ml-12 shrink-0 sm:ml-0">
          <Link to={meta.to}>
            {step.partial ? t('setup.finish') : key === 'NFC_TAG' ? t('setup.request') : t('setup.start')}
            <ArrowRight className="size-4" aria-hidden="true" />
          </Link>
        </Button>
      )}
    </li>
  );
}

/** Paketdə olan müştəri cəlbi imkanları: açılmamışlar irəlidə, açılmışlar "Açıqdır" nişanı ilə. */
function Recommendations({ steps }: { steps: Step[] }) {
  const { t } = useTranslation('admin');
  const queryClient = useQueryClient();
  const hide = useMutation({
    mutationFn: () => setupApi.hideRecommendations(true),
    onSuccess: (data) => queryClient.setQueryData(SETUP_KEY, data),
  });
  const sorted = [...steps].sort((a, b) => Number(Boolean(a.done)) - Number(Boolean(b.done)));
  const enabled = steps.filter((step) => step.done).length;
  return (
    <Card>
      <CardContent className="flex flex-col gap-4 p-5 sm:p-6">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-bold">{t('setup.recommended.title')}</h2>
            <p className="text-sm text-muted-foreground">
              {t('setup.recommended.subtitle', { enabled, total: steps.length })}
            </p>
          </div>
          <Button variant="ghost" size="sm" onClick={() => hide.mutate()} disabled={hide.isPending} className="shrink-0">
            <X className="size-4" aria-hidden="true" />
            {t('setup.recommended.hide')}
          </Button>
        </div>
        <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {sorted.map((step) => {
            const key = setupStepKey(step.key);
            if (!key) {
              return null;
            }
            const meta = STEPS[key];
            const Icon = meta.icon;
            return (
              <li key={key}>
                <Link
                  to={meta.to}
                  className={cn(
                    'flex h-full items-start gap-3 rounded-xl border p-3.5 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                    step.done ? 'border-border bg-muted/40' : 'border-border hover:border-primary/40 hover:bg-primary/5',
                  )}
                >
                  <span
                    className={cn(
                      'grid size-10 shrink-0 place-items-center rounded-xl',
                      step.done ? 'bg-success/15 text-[var(--success-ink)]' : 'bg-primary/10 text-primary',
                    )}
                    aria-hidden="true"
                  >
                    <Icon className="size-5" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center justify-between gap-2">
                      <span className="font-semibold">{t(`setup.steps.${key}.title`)}</span>
                      {step.done ? (
                        <span className="inline-flex shrink-0 items-center gap-1 text-xs font-semibold text-[var(--success-ink)]">
                          <Check className="size-3.5" aria-hidden="true" />
                          {t('setup.recommended.enabled')}
                        </span>
                      ) : (
                        <span className="inline-flex shrink-0 items-center gap-0.5 text-xs font-semibold text-primary">
                          {t('setup.recommended.enable')}
                          <ArrowRight className="size-3.5" aria-hidden="true" />
                        </span>
                      )}
                    </span>
                    <span className="mt-0.5 block text-sm leading-snug text-muted-foreground">{t(`setup.steps.${key}.hint`)}</span>
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      </CardContent>
    </Card>
  );
}
