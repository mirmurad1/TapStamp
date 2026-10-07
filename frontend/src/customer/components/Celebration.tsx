import { Gift, PartyPopper } from 'lucide-react';
import { useMemo, type CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { Button } from '@/shared/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/shared/ui/dialog';

const COLORS = ['var(--primary)', 'var(--accent)', 'var(--highlight)', '#22c55e', '#0ea5e9'];

/**
 * Konfeti: dekorativ, ekran oxuyucularından gizlidir; "azaldılmış hərəkət" rejimində animasiya söndürülür.
 * Hər möhürdə kiçik (az və xırda parçalar), kart dolanda böyük.
 */
export function Confetti({ count = 60, small = false }: { count?: number; small?: boolean }) {
  const pieces = useMemo(
    () =>
      Array.from({ length: count }, (_, i) => ({
        left: `${(i * 37) % 100}%`,
        color: COLORS[i % COLORS.length],
        size: (small ? 4 : 6) + ((i * 7) % (small ? 4 : 7)),
        delay: `${(i % 12) * 0.08}s`,
        duration: `${(small ? 1.6 : 2.2) + ((i * 13) % 10) / 10}s`,
        drift: `${((i * 53) % 160) - 80}px`,
        spin: `${360 + ((i * 97) % 540)}deg`,
        round: i % 3 === 0,
      })),
    [count, small],
  );
  return (
    <div className="pointer-events-none fixed inset-0 z-[60] overflow-hidden" aria-hidden="true">
      {pieces.map((piece, index) => (
        <span
          key={index}
          className="animate-confetti absolute top-0 block"
          style={
            {
              left: piece.left,
              width: piece.size,
              height: piece.round ? piece.size : piece.size * 1.6,
              background: piece.color,
              borderRadius: piece.round ? '9999px' : '2px',
              '--delay': piece.delay,
              '--duration': piece.duration,
              '--drift': piece.drift,
              '--spin': piece.spin,
            } as CSSProperties
          }
        />
      ))}
    </div>
  );
}

interface CelebrationProps {
  open: boolean;
  rewardName?: string;
  onClose: () => void;
}

/** Kart dolanda: konfeti, təbrik və hədiyyə kartına keçid. */
export function Celebration({ open, rewardName, onClose }: CelebrationProps) {
  const { t } = useTranslation('profile');
  return (
    <>
      {open ? <Confetti /> : null}
      <Dialog open={open} onOpenChange={(next) => (next ? undefined : onClose())}>
        <DialogContent className="text-center">
          <div className="mx-auto grid size-24 place-items-center rounded-full bg-linear-to-br from-accent to-highlight text-white shadow-lg">
            <Gift className="animate-gift-bounce size-12" aria-hidden="true" />
          </div>
          <DialogTitle className="flex items-center justify-center gap-2 text-3xl font-bold">
            <PartyPopper className="size-7 text-highlight" aria-hidden="true" />
            {t('celebrate.title')}
          </DialogTitle>
          <DialogDescription className="text-base text-foreground">
            {rewardName ? t('celebrate.subtitle', { reward: rewardName }) : t('celebrate.subtitleGeneric')}
          </DialogDescription>
          <p className="text-sm text-muted-foreground">{t('celebrate.hint')}</p>
          <div className="flex flex-col gap-2 pt-2">
            <Button asChild size="lg">
              <Link to="/gift-cards" onClick={onClose}>
                {t('celebrate.open')}
              </Link>
            </Button>
            <Button variant="ghost" onClick={onClose}>
              {t('celebrate.later')}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
