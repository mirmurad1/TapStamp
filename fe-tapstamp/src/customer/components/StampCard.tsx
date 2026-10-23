import { Check, Gift, Hourglass, Nfc, Star } from 'lucide-react';
import type { CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';

import { cn } from '@/shared/lib/cn';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';

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
}

/**
 * Möhür kartı: N xana + hədiyyə xanası. İkonlar bütün şirkətlər üçün eynidir (boş xanada ulduz, toplanmışda təsdiq
 * işarəsi), rəng şirkətin brend rəngidir — kafe, avtoyuma, bərbər və s. üçün eyni dərəcədə uyğundur. Kartın fonu brend
 * rənginin açıq çaları ilə rənglənir; açılışda toplanmış möhürlər bir-bir yanır, hədiyyəyə 1 möhür qalanda hədiyyə xanası
 * "nəfəs alır".
 */
export function StampCard({
  stampsRequired,
  current,
  rewardName,
  pending = 0,
  animateFrom,
  completed,
  showReturnHint = false,
}: StampCardProps) {
  const { t } = useTranslation('profile');
  const shown = completed ? stampsRequired : Math.min(current, stampsRequired);
  const remaining = Math.max(stampsRequired - shown, 0);
  const pendingSlots = Math.min(pending, stampsRequired - shown);
  const lastOne = !completed && remaining === 1;

  return (
    <Card className="overflow-hidden border-primary/15 bg-[linear-gradient(160deg,color-mix(in_srgb,var(--primary)_9%,var(--card))_0%,color-mix(in_srgb,var(--primary)_3%,var(--card))_70%)]">
      <div className="h-1.5 bg-linear-to-r from-primary via-accent to-highlight" aria-hidden="true" />
      <CardHeader>
        <CardTitle>{t('card.title')}</CardTitle>
        <p className="text-sm text-muted-foreground">
          {completed ? t('card.complete') : t('card.progress', { count: remaining })}
        </p>
        {rewardName ? <p className="text-sm font-semibold text-primary">{t('card.reward', { name: rewardName })}</p> : null}
      </CardHeader>
      <CardContent>
        {/* N xana: sonuncu xana hədiyyənin özüdür (N-ci möhür vurulanda hədiyyə qazanılır) */}
        <ol className="grid grid-cols-5 gap-3">
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
        <div
          className="mt-5 h-2.5 overflow-hidden rounded-full bg-muted"
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={stampsRequired}
          aria-valuenow={shown}
          aria-label={t('card.title')}
        >
          <div
            className="animate-progress-grow h-full rounded-full bg-linear-to-r from-primary/70 to-primary transition-[width] duration-700 ease-out"
            style={{ width: `${(shown / stampsRequired) * 100}%` }}
          />
        </div>
      </CardContent>
      {showReturnHint ? (
        <div className="flex items-start gap-3 border-t border-border bg-muted/60 px-5 py-4 sm:px-6">
          <span className="grid size-9 shrink-0 place-items-center rounded-full bg-primary/10 text-primary">
            <Nfc className="size-5" aria-hidden="true" />
          </span>
          <p className="text-sm">
            <span className="block font-semibold">{t('card.returnTitle')}</span>
            <span className="block text-muted-foreground">{t('card.returnHint')}</span>
          </p>
        </div>
      ) : null}
    </Card>
  );
}

/** Hədiyyə xanası: qazanılana qədər solğun, son möhürlə parlaq və canlı; 1 möhür qalanda "Son möhür!" nişanı. */
function GiftSlot({ filled, completed, lastOne }: { filled: boolean; completed: boolean; lastOne: boolean }) {
  const { t } = useTranslation('profile');
  return (
    <span className="relative">
      {lastOne ? (
        <span className="absolute -top-6 left-1/2 -translate-x-1/2 whitespace-nowrap rounded-md bg-foreground px-1.5 py-1 text-[10px] leading-none font-bold text-background">
          {t('card.lastOne')}
        </span>
      ) : null}
      <span
        role="img"
        aria-label={t('card.gift')}
        className={cn(
          'grid size-12 place-items-center rounded-full text-white',
          filled
            ? 'bg-linear-to-br from-accent to-highlight shadow-md'
            : 'border-2 border-dashed border-accent/50 bg-linear-to-br from-accent/25 to-highlight/25 text-accent/60',
          completed && 'animate-gift-bounce',
          lastOne && 'animate-gift-breathe border-solid',
        )}
      >
        <Gift className="size-6" aria-hidden="true" />
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
        className="grid size-12 place-items-center rounded-full border-2 border-dashed border-highlight text-[var(--highlight-ink)]"
      >
        <Hourglass className="size-5" aria-hidden="true" />
      </span>
    );
  }
  return (
    <span className="relative grid size-12 place-items-center">
      {fresh ? (
        <span
          className="animate-stamp-ring absolute inset-0 rounded-full border-4 border-primary"
          style={{ animationDelay: `${delay + 250}ms` }}
          aria-hidden="true"
        />
      ) : null}
      <span
        role="img"
        aria-label={t(filled ? 'card.filled' : 'card.empty')}
        className={cn(
          'grid size-12 place-items-center rounded-full',
          filled ? 'bg-primary text-primary-foreground shadow-md' : 'border-2 border-dashed border-border text-muted-foreground/40',
          fresh && 'animate-stamp-pop',
          // Açılışda toplanmış möhürlər soldan sağa bir-bir yanır (yeni vurulanın öz animasiyası var)
          filled && !fresh && 'animate-stamp-light',
        )}
        style={fresh ? { animationDelay: `${delay}ms` } : ({ '--i': index } as CSSProperties)}
      >
        {filled ? <Check className="size-6" strokeWidth={3} aria-hidden="true" /> : <Star className="size-6" aria-hidden="true" />}
      </span>
    </span>
  );
}
