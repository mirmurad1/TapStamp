import { Check, ChevronRight, Gift, Medal, Sparkles, Stamp } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';

import { useCustomerSession } from '../auth/CustomerSessionProvider';
import { useMyTier } from '../lib/useMyTier';
import { Sheet } from './Sheet';

/**
 * Kartın üzərindəki səviyyə nişanı (plastik kartdakı "Gold" yazısı kimi): səviyyənin rəngində medal və ad, imtiyaz
 * varsa yanında. Toxunanda bütün səviyyələr açılır. Səviyyə hələ yoxdursa görünmür.
 */
export function TierChip() {
  const { t } = useTranslation('profile');
  const tier = useMyTier();
  const [open, setOpen] = useState(false);
  const current = tier?.current;
  if (!current) {
    return null;
  }
  const name = resolveText(current.name, currentLanguage());
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="inline-flex max-w-full shrink-0 items-center gap-1.5 rounded-full bg-white/95 py-1 pr-2 pl-1 text-xs font-bold text-foreground shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white"
        aria-label={t('tier.openAll', { name })}
      >
        <span className="grid size-5 shrink-0 place-items-center rounded-full text-white" style={{ background: current.color }}>
          <Medal className="size-3.5" aria-hidden="true" />
        </span>
        <span className="truncate">{name}</span>
        {current.bonusStamps ? <span className="shrink-0 opacity-60">{t('tier.perk', { count: current.bonusStamps })}</span> : null}
        <ChevronRight className="size-3.5 shrink-0 opacity-50" aria-hidden="true" />
      </button>
      <TierSheet open={open} onOpenChange={setOpen} />
    </>
  );
}

/** Kartın alt zolağında: növbəti səviyyəyə qalan ziyarətlər və nazik irəliləyiş xətti; toxunanda bütün səviyyələr. */
export function TierNote() {
  const { t } = useTranslation('profile');
  const tier = useMyTier();
  const [open, setOpen] = useState(false);
  if (!tier) {
    return null;
  }
  const { next, visitsToNext, progress } = tier;
  const color = next?.color ?? tier.current?.color ?? 'var(--primary)';
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="flex w-full items-center gap-2 border-t border-dashed border-border px-5 py-3 text-left text-sm text-muted-foreground hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:outline-none"
      >
        <Medal className="size-4 shrink-0" aria-hidden="true" style={{ color }} />
        <span className="min-w-0 flex-1">
          <span className="block">
            {next ? t('tier.toNext', { count: visitsToNext, name: resolveText(next.name, currentLanguage()) }) : t('tier.top')}
            {next?.bonusStamps ? ` ${t('tier.nextPerk', { count: next.bonusStamps })}` : ''}
            {next?.reachGift ? ` ${t('tier.reachGiftGeneric')} 🎁` : ''}
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
        <ChevronRight className="size-4 shrink-0 opacity-60" aria-hidden="true" />
      </button>
      <TierSheet open={open} onOpenChange={setOpen} />
    </>
  );
}

/**
 * Bütün səviyyələr həddə görə: medal, ad, imtiyaz və hədd. Keçilmiş səviyyələr işarələnir, cari səviyyə vurğulanır,
 * qarşıdakıların medalı solğundur.
 */
function TierSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useTranslation('profile');
  const { me } = useCustomerSession();
  const lang = currentLanguage();
  const tier = useMyTier();
  if (!tier) {
    return null;
  }
  const { current, visits } = tier;
  const rewardName = resolveText(me?.card?.rewardName, lang);
  return (
    <Sheet open={open} onOpenChange={onOpenChange} title={t('tier.allTitle')} description={t('tier.visits', { count: visits })}>
      <ol className="flex flex-col gap-2">
        {tier.tiers.map((item) => {
          const isCurrent = item.id === current?.id;
          const reached = visits >= (item.minVisits ?? 0);
          return (
            <li
              key={item.id}
              className={cn('flex items-center gap-3 rounded-2xl border p-3 text-left', isCurrent ? 'border-transparent shadow-sm' : 'border-border')}
              style={isCurrent ? { background: `color-mix(in srgb, ${item.color} 14%, var(--card))` } : undefined}
              aria-current={isCurrent ? 'step' : undefined}
            >
              <span
                className={cn('grid size-11 shrink-0 place-items-center rounded-full text-white', reached ? 'shadow-sm' : 'opacity-40')}
                style={{ background: item.color }}
              >
                <Medal className="size-6" aria-hidden="true" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex items-center gap-2">
                  <span className="truncate font-bold">{resolveText(item.name, lang)}</span>
                  {isCurrent ? (
                    <span className="shrink-0 rounded-full px-2 py-0.5 text-[11px] font-bold text-white" style={{ background: item.color }}>
                      {t('tier.youAreHere')}
                    </span>
                  ) : null}
                </span>
                <TierPerks tier={item} rewardName={rewardName} />
                <span className="block text-xs text-muted-foreground">
                  {item.minVisits ? t('tier.from', { count: item.minVisits }) : t('tier.fromStart')}
                </span>
              </span>
              {reached && !isCurrent ? <Check className="size-5 shrink-0 text-[var(--success-ink)]" aria-label={t('tier.reached')} /> : null}
            </li>
          );
        })}
      </ol>
      <p className="mt-3 text-xs text-muted-foreground">{t('tier.howItWorks')}</p>
    </Sheet>
  );
}

type TierItem = NonNullable<ReturnType<typeof useMyTier>>['tiers'][number];

/** Səviyyənin imtiyazları ikonları ilə: hər möhürə bonus, çatanda hədiyyə, çatanda bir dəfəlik möhür. */
function TierPerks({ tier, rewardName }: { tier: TierItem; rewardName: string }) {
  const { t } = useTranslation('profile');
  const perks: { icon: ReactNode; text: string }[] = [];
  if (tier.bonusStamps) {
    perks.push({ icon: <Stamp aria-hidden="true" />, text: t('tier.benefit', { count: tier.bonusStamps }) });
  }
  if (tier.reachGift) {
    perks.push({
      icon: <Gift aria-hidden="true" />,
      text: rewardName ? t('tier.reachGift', { reward: rewardName }) : t('tier.reachGiftGeneric'),
    });
  }
  if (tier.reachStamps) {
    perks.push({ icon: <Sparkles aria-hidden="true" />, text: t('tier.reachStamps', { count: tier.reachStamps }) });
  }
  if (perks.length === 0) {
    perks.push({ icon: <Medal aria-hidden="true" />, text: t('tier.badgeOnly') });
  }
  return (
    <ul className="mt-0.5 flex flex-col gap-0.5 text-sm text-muted-foreground">
      {perks.map((perk) => (
        <li key={perk.text} className="flex items-center gap-1.5 [&_svg]:size-3.5 [&_svg]:shrink-0">
          {perk.icon}
          {perk.text}
        </li>
      ))}
    </ul>
  );
}
