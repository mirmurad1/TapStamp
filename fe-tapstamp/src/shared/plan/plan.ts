import { useQuery } from '@tanstack/react-query';

import { api, unwrap } from '@/shared/api/client';
import type { components } from '@/shared/api/schema';

export type PlanInfo = components['schemas']['PlanResponse'];
export type Feature = NonNullable<PlanInfo['features']>[number];
export type PlanName = 'BASIC' | 'STANDARD' | 'PREMIUM';

export const PLAN_KEY = ['plan'] as const;

/** Hər imkanın ilk olduğu paket — bağlı bölmədə "hansı paketə keçmək lazımdır" demək üçün. */
export const FEATURE_PLAN: Record<Feature, PlanName> = {
  PUSH: 'STANDARD',
  REFERRALS: 'STANDARD',
  CSV_EXPORT: 'STANDARD',
  FULL_ANALYTICS: 'STANDARD',
  STAMP_CAMPAIGNS: 'PREMIUM',
  REMINDERS: 'PREMIUM',
  AUDIT_LOG: 'PREMIUM',
  BRANCH_COMPARISON: 'PREMIUM',
  REFERRAL_STATS: 'PREMIUM',
  BONUS_RULES: 'STANDARD',
  TIERS: 'STANDARD',
};

/**
 * Şirkətin paketi (yalnız şirkətin domenində; platforma domenində sorğu 404 qaytarır). Backend imkanları özü
 * yoxlayır — frontend yalnız bağlı bölmələri göstərməmək üçün istifadə edir.
 */
export function usePlan(enabled = true) {
  return useQuery({
    queryKey: PLAN_KEY,
    queryFn: () => unwrap(api.GET('/api/v1/public/plan')),
    enabled,
    retry: false,
    staleTime: 60_000,
    refetchInterval: 5 * 60_000,
  });
}

/** Paket hələ yüklənməyibsə {@code undefined} — bölmə nə açıq, nə bağlı göstərilmir. */
export function useFeature(feature: Feature): boolean | undefined {
  const plan = usePlan();
  return plan.data ? (plan.data.features ?? []).includes(feature) : undefined;
}
