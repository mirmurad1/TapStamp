import { useQuery } from '@tanstack/react-query';
import { Medal } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';

import { meApi } from '../api/customerApi';

const TIER_KEY = ['my-tier'] as const;

/**
 * Müştərinin səviyyəsi: rəngli nişan, imtiyaz və növbəti səviyyəyə irəliləyiş zolağı. Şirkətdə səviyyə yoxdursa
 * görünmür.
 */
export function TierCard() {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const tier = useQuery({ queryKey: TIER_KEY, queryFn: meApi.tier });
  const data = tier.data;
  if (!data?.enabled) {
    return null;
  }
  const current = data.current;
  const next = data.next;
  const visits = data.visits ?? 0;
  const color = current?.color ?? next?.color ?? 'var(--primary)';
  // Zolaq cari səviyyənin həddindən növbətinin həddinə qədər doldurulur
  const from = current?.minVisits ?? 0;
  const to = next?.minVisits ?? from;
  const progress = next ? Math.min(1, Math.max(0, (visits - from) / Math.max(1, to - from))) : 1;
  return (
    <section className="flex flex-col gap-3 rounded-2xl border border-border bg-card p-4">
      <div className="flex items-center gap-3">
        <span
          className="grid size-11 shrink-0 place-items-center rounded-full text-white shadow-sm"
          style={{ background: current ? color : 'var(--muted-foreground)' }}
        >
          <Medal className="size-6" aria-hidden="true" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-xs text-muted-foreground">{t('tier.label')}</span>
          <span className="block truncate text-base font-bold">
            {current ? resolveText(current.name, lang) : t('tier.none')}
          </span>
        </span>
        {current?.bonusStamps ? (
          <span className="shrink-0 rounded-full px-2.5 py-1 text-xs font-bold text-white" style={{ background: color }}>
            {t('tier.perk', { count: current.bonusStamps })}
          </span>
        ) : null}
      </div>
      <div
        className="h-2 overflow-hidden rounded-full bg-muted"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(progress * 100)}
        aria-label={t('tier.progress')}
      >
        <span className="block h-full rounded-full transition-[width]" style={{ width: `${progress * 100}%`, background: next?.color ?? color }} />
      </div>
      <p className="text-sm text-muted-foreground">
        {next
          ? t('tier.toNext', { count: data.visitsToNext ?? 0, name: resolveText(next.name, lang) })
          : t('tier.top')}
        {next?.bonusStamps ? ` ${t('tier.nextPerk', { count: next.bonusStamps })}` : ''}
      </p>
    </section>
  );
}
