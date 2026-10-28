import { Medal } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';

import { useMyTier } from '../lib/useMyTier';
import { StampCardNote } from './StampCard';

/**
 * Kartın üzərindəki səviyyə nişanı (plastik kartdakı "Gold" yazısı kimi): səviyyənin rəngində medal və ad, imtiyaz
 * varsa yanında. Səviyyə hələ yoxdursa görünmür.
 */
export function TierChip() {
  const { t } = useTranslation('profile');
  const tier = useMyTier();
  const current = tier?.current;
  if (!current) {
    return null;
  }
  return (
    <span
      className="inline-flex max-w-full shrink-0 items-center gap-1.5 rounded-full bg-white/95 py-1 pr-2.5 pl-1 text-xs font-bold text-foreground shadow-sm"
      aria-label={`${t('tier.label')}: ${resolveText(current.name, currentLanguage())}`}
    >
      <span className="grid size-5 shrink-0 place-items-center rounded-full text-white" style={{ background: current.color }}>
        <Medal className="size-3.5" aria-hidden="true" />
      </span>
      <span className="truncate">{resolveText(current.name, currentLanguage())}</span>
      {current.bonusStamps ? <span className="shrink-0 opacity-60">{t('tier.perk', { count: current.bonusStamps })}</span> : null}
    </span>
  );
}

/** Kartın alt zolağında: növbəti səviyyəyə qalan ziyarətlər və nazik irəliləyiş xətti. */
export function TierNote() {
  const { t } = useTranslation('profile');
  const tier = useMyTier();
  if (!tier) {
    return null;
  }
  const { next, visitsToNext, progress } = tier;
  const color = next?.color ?? tier.current?.color ?? 'var(--primary)';
  return (
    <StampCardNote icon={<Medal aria-hidden="true" style={{ color }} />}>
      <span className="min-w-0 flex-1">
      <span className="block">
        {next ? t('tier.toNext', { count: visitsToNext, name: resolveText(next.name, currentLanguage()) }) : t('tier.top')}
        {next?.bonusStamps ? ` ${t('tier.nextPerk', { count: next.bonusStamps })}` : ''}
      </span>
      {next ? (
        <span
          className="mt-1.5 block h-1 overflow-hidden rounded-full bg-muted"
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(progress * 100)}
          aria-label={t('tier.progress')}
        >
          <span className="block h-full rounded-full" style={{ width: `${progress * 100}%`, background: color }} />
        </span>
      ) : null}
      </span>
    </StampCardNote>
  );
}

