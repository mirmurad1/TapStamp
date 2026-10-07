import { Gift } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { cn } from '@/shared/lib/cn';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';

interface StampCardProps {
  /** Hədiyyə üçün lazım olan möhür sayı (məs. 9 → 10-cu qəhvə pulsuzdur). */
  stampsRequired: number;
  /** Cari kartdakı möhürlər. */
  current: number;
  /** Hədiyyənin adı (cari dildə). */
  rewardName?: string;
}

/** Möhür kartı: N fincan + hədiyyə fincanı. Animasiyalar Mərhələ 5-də əlavə olunur. */
export function StampCard({ stampsRequired, current, rewardName }: StampCardProps) {
  const { t } = useTranslation('profile');
  const remaining = Math.max(stampsRequired - current, 0);

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('card.title')}</CardTitle>
        <p className="text-sm text-muted-foreground">{t('card.progress', { count: remaining })}</p>
        {rewardName ? <p className="text-sm font-medium text-accent">{t('card.reward', { name: rewardName })}</p> : null}
      </CardHeader>
      <CardContent>
        <ol className="grid grid-cols-5 gap-3">
          {Array.from({ length: stampsRequired }, (_, index) => {
            const filled = index < current;
            return (
              <li key={index} className="flex justify-center">
                <CupIcon filled={filled} label={t(filled ? 'card.filledCup' : 'card.emptyCup')} />
              </li>
            );
          })}
          <li className="flex justify-center">
            <span
              role="img"
              aria-label={t('card.giftCup')}
              className="grid size-12 place-items-center rounded-full border-2 border-dashed border-accent text-accent"
            >
              <Gift className="size-6" aria-hidden="true" />
            </span>
          </li>
        </ol>
        <div
          className="mt-5 h-2 overflow-hidden rounded-full bg-muted"
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={stampsRequired}
          aria-valuenow={current}
          aria-label={t('card.title')}
        >
          <div
            className="h-full rounded-full bg-accent transition-[width] duration-500"
            style={{ width: `${(Math.min(current, stampsRequired) / stampsRequired) * 100}%` }}
          />
        </div>
      </CardContent>
    </Card>
  );
}

function CupIcon({ filled, label }: { filled: boolean; label: string }) {
  return (
    <svg
      role="img"
      aria-label={label}
      viewBox="0 0 48 48"
      className={cn('size-12', filled ? 'text-primary' : 'text-muted-foreground/45')}
    >
      <path
        d="M10 18h22v10a10 10 0 0 1-10 10h-2a10 10 0 0 1-10-10z"
        fill={filled ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="2.5"
        strokeLinejoin="round"
      />
      <path d="M32 21h3a5 5 0 0 1 0 10h-3" fill="none" stroke="currentColor" strokeWidth="2.5" />
      {filled && (
        <path
          d="M17 8c-1.5 2.5 1.5 4 0 6.5M24 7c-1.5 2.5 1.5 4 0 6.5"
          fill="none"
          className="stroke-accent"
          strokeWidth="2"
          strokeLinecap="round"
        />
      )}
    </svg>
  );
}
