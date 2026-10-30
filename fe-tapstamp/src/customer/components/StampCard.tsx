import { Check, Gift, Hourglass, Nfc } from 'lucide-react';
import type { CSSProperties, ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { cn } from '@/shared/lib/cn';

import { useCompanyName } from '../lib/companyInitial';

interface StampCardProps {
  /** Hədiyyə üçün lazım olan möhür sayı. */
  stampsRequired: number;
  /** Cari kartdakı möhürlər. */
  current: number;
  /** Hədiyyənin adı (cari dildə). */
  rewardName?: string;
  /** İşçi təsdiqini gözləyən möhürlər (kartda ayrıca işarələnir). */
  pending?: number;
  /** Bu indeksdən başlayan möhürlər yeni vurulub — animasiya ilə görünür. */
  animateFrom?: number;
  /** Alt hissədə "nömrə bir daha soruşulmayacaq" izahı (daxil olmuş müştəri üçün). */
  showReturnHint?: boolean;
  /** Kart dolub (hədiyyə kartı yaranıb) — hədiyyə xanası canlanır. */
  completed?: boolean;
  /** Kartın alt zolaqları (geri sayım, təsdiq gözləyən möhürlər). */
  footer?: ReactNode;
  /** Kartın aşağı xəttində, şirkət adının yerində (məs. səviyyə nişanı). */
  badge?: ReactNode;
}

/**
 * Möhür kartı: N xana + hədiyyə xanası. Bütün rənglər şirkətin brend rəngindəndir — kafe, avtoyuma, bərbər və s. üçün
 * eyni dərəcədə uyğundur. Sayğac başlıqdadır, boş xanalarda nömrə var (neçə qaldığı bir baxışda görünür); açılışda
 * toplanmış möhürlər bir-bir yanır, hədiyyəyə 1 möhür qalanda hədiyyə xanası "nəfəs alır".
 */
export function StampCard({
  stampsRequired,
  current,
  rewardName,
  pending = 0,
  animateFrom,
  completed,
  showReturnHint = false,
  footer,
  badge,
}: StampCardProps) {
  const { t } = useTranslation('profile');
  const shown = completed ? stampsRequired : Math.min(current, stampsRequired);
  const remaining = Math.max(stampsRequired - shown, 0);
  const pendingSlots = Math.min(pending, stampsRequired - shown);
  const lastOne = !completed && remaining === 1;
  const companyName = useCompanyName();

  return (
    <section className="overflow-hidden rounded-3xl bg-card shadow-[0_18px_30px_-16px_color-mix(in_srgb,var(--primary)_80%,transparent)]" aria-labelledby="stamp-card-title">
      {/* Kartın özü cüzdandakı plastik kart kimidir: tam brend rəngində, möhürlər ağ */}
      <div className="shine relative overflow-hidden bg-[radial-gradient(120%_90%_at_0%_0%,color-mix(in_srgb,var(--primary)_80%,#fff),var(--primary)_55%,color-mix(in_srgb,var(--primary)_75%,#000))] p-5 text-primary-foreground">
        <span className="pointer-events-none absolute -top-12 -right-12 size-44 rounded-full border-[24px] border-white/[0.07]" aria-hidden="true" />
        <div className="relative flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 id="stamp-card-title" className="text-lg font-bold">
              {t('card.title')}
            </h2>
            <p className="text-sm opacity-80">{completed ? t('card.complete') : t('card.progress', { count: remaining })}</p>
          </div>
          <p
            className="flex shrink-0 items-baseline text-4xl leading-none font-extrabold tracking-tight tabular-nums"
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={stampsRequired}
            aria-valuenow={shown}
            aria-label={t('card.title')}
          >
            <span key={shown} className="animate-count-in inline-block">
              {shown}
            </span>
            <span className="ml-0.5 text-base font-bold opacity-60">/{stampsRequired}</span>
          </p>
        </div>
        {/* N xana: sonuncu xana hədiyyənin özüdür (N-ci möhür vurulanda hədiyyə qazanılır) */}
        <ol className="relative mt-4 grid grid-cols-5 gap-2.5">
          {Array.from({ length: stampsRequired }, (_, index) => {
            const filled = index < shown;
            const waiting = !filled && index < shown + pendingSlots;
            const fresh = filled && animateFrom !== undefined && index >= animateFrom;
            const isGift = index === stampsRequired - 1;
            return (
              <li key={index} className="flex justify-center">
                {isGift ? (
                  <GiftSlot filled={filled} completed={Boolean(completed)} lastOne={lastOne} />
                ) : (
                  <StampSlot
                    filled={filled}
                    waiting={waiting}
                    fresh={fresh}
                    index={index}
                    delay={fresh ? (index - (animateFrom ?? 0)) * 120 : 0}
                  />
                )}
              </li>
            );
          })}
        </ol>
        {rewardName || companyName || badge ? (
          <p className="relative mt-4 flex items-center justify-between gap-3 border-t border-dashed border-white/25 pt-3 text-[13px]">
            <span className="min-w-0 font-semibold">{rewardName ? t('card.reward', { name: rewardName }) : null}</span>
            {badge ?? <span className="shrink-0 truncate opacity-75">{companyName}</span>}
          </p>
        ) : null}
      </div>
      <div className="flex flex-col empty:hidden">
        {footer}
        {showReturnHint ? (
          <div className="flex items-start gap-3 border-t border-border bg-primary/5 px-5 py-3.5">
            <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary">
              <Nfc className="size-5" aria-hidden="true" />
            </span>
            <p className="text-sm">
              <span className="block font-semibold">{t('card.returnTitle')}</span>
              <span className="block text-muted-foreground">{t('card.returnHint')}</span>
            </p>
          </div>
        ) : null}
      </div>
    </section>
  );
}

/** Kartın alt zolağı: geri sayım (adi) və ya təsdiq gözləyən möhür (diqqət). */
export function StampCardNote({ icon, tone = 'muted', children }: { icon: ReactNode; tone?: 'muted' | 'warn'; children: ReactNode }) {
  return (
    <p
      className={cn(
        'flex items-center gap-2 border-t px-5 py-3 text-sm [&_svg]:size-4 [&_svg]:shrink-0',
        tone === 'warn'
          ? 'border-highlight/30 bg-highlight/10 font-semibold text-[var(--highlight-ink)]'
          : 'border-dashed border-border text-muted-foreground',
      )}
    >
      {icon}
      {children}
    </p>
  );
}

/** Hədiyyə xanası: qazanılana qədər brend rənginin açıq çaları, son möhürlə dolu və canlı; 1 möhür qalanda "Son möhür!". */
function GiftSlot({ filled, completed, lastOne }: { filled: boolean; completed: boolean; lastOne: boolean }) {
  const { t } = useTranslation('profile');
  return (
    <span className="relative">
      {/* İkonun ortasında; mətnlər qısa saxlanılır ki, kartın kənarından çıxmasın */}
      {lastOne ? (
        <span className="absolute -top-5 left-1/2 z-10 -translate-x-1/2 whitespace-nowrap rounded-md bg-foreground px-1.5 py-0.5 text-[10px] leading-tight font-bold text-background">
          {t('card.lastOne')}
        </span>
      ) : null}
      <span
        role="img"
        aria-label={t('card.gift')}
        className={cn(
          'grid size-12 place-items-center rounded-full',
          filled
            ? 'bg-white text-primary shadow-md'
            : 'border-2 border-white bg-white/10 text-white',
          completed && 'animate-gift-bounce',
          lastOne && 'animate-gift-breathe',
        )}
      >
        <Gift className="size-5" aria-hidden="true" />
      </span>
    </span>
  );
}

function StampSlot({
  filled,
  waiting,
  fresh,
  index,
  delay,
}: {
  filled: boolean;
  waiting: boolean;
  fresh: boolean;
  index: number;
  delay: number;
}) {
  const { t } = useTranslation('profile');
  if (waiting) {
    return (
      <span
        role="img"
        aria-label={t('card.pending', { count: 1 })}
        className="grid size-12 place-items-center rounded-full animate-pulse border-2 border-dashed border-white text-white"
      >
        <Hourglass className="size-4" aria-hidden="true" />
      </span>
    );
  }
  return (
    <span className="relative grid size-12 place-items-center">
      {fresh ? (
        <span
          className="animate-stamp-ring absolute inset-0 rounded-full border-4 border-white"
          style={{ animationDelay: `${delay + 250}ms` }}
          aria-hidden="true"
        />
      ) : null}
      <span
        role="img"
        aria-label={t(filled ? 'card.filled' : 'card.empty')}
        className={cn(
          'flex size-12 items-center justify-center rounded-full text-sm leading-none font-bold',
          filled
            ? 'bg-white text-primary shadow-[0_3px_8px_rgb(0_0_0/0.18)]'
            : 'border-2 border-dashed border-white/35 text-white/60',
          fresh && 'animate-stamp-pop',
          // Açılışda toplanmış möhürlər soldan sağa bir-bir yanır (yeni vurulanın öz animasiyası var)
          filled && !fresh && 'animate-stamp-light',
        )}
        style={fresh ? { animationDelay: `${delay}ms` } : ({ '--i': index } as CSSProperties)}
      >
        {filled ? <Check className="size-5" strokeWidth={3} aria-hidden="true" /> : <span aria-hidden="true">{index + 1}</span>}
      </span>
    </span>
  );
}
