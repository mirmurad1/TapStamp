import { useQuery } from '@tanstack/react-query';

import { meApi } from '../api/customerApi';

/** Müştərinin səviyyəsi; şirkətdə səviyyə yoxdursa və ya söndürülübsə {@code undefined}. */
export function useMyTier() {
  const tier = useQuery({ queryKey: ['my-tier'], queryFn: meApi.tier });
  const data = tier.data;
  if (!data?.enabled) {
    return undefined;
  }
  const current = data.current;
  const next = data.next;
  const visits = data.visits ?? 0;
  // Cari səviyyənin həddindən növbətinin həddinə qədər irəliləyiş (0–1)
  const from = current?.minVisits ?? 0;
  const to = next?.minVisits ?? from;
  const progress = next ? Math.min(1, Math.max(0, (visits - from) / Math.max(1, to - from))) : 1;
  return { current, next, visits, visitsToNext: data.visitsToNext ?? 0, progress, tiers: data.tiers ?? [] };
}
