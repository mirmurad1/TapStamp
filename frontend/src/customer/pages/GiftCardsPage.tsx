import { useQuery } from '@tanstack/react-query';
import { Gift, LoaderCircle, QrCode as QrIcon } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';
import { formatDate } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/shared/ui/dialog';
import { QrCode } from '@/shared/ui/QrCode';

import { giftCardApi, type GiftCard } from '../api/customerApi';
import { useCustomerSession } from '../auth/CustomerSessionProvider';
import { customerKeys } from '../auth/queryClient';
import { ErrorMessage } from '../components/ErrorMessage';

/** Hədiyyə kartları: istifadəyə hazır olanlar (kod və QR ilə) və tarixçə. */
export function GiftCardsPage() {
  const { t } = useTranslation('profile');
  const { status } = useCustomerSession();
  const cards = useQuery({ queryKey: customerKeys.giftCards, queryFn: giftCardApi.list, enabled: status === 'signedIn' });
  const [shown, setShown] = useState<GiftCard>();

  if (status === 'guest') {
    return <Navigate to="/login" replace />;
  }
  const available = (cards.data ?? []).filter((card) => card.status === 'AVAILABLE');
  const history = (cards.data ?? []).filter((card) => card.status !== 'AVAILABLE');

  return (
    <div className="flex flex-col gap-4 pt-2">
      <section>
        <h1 className="text-3xl font-bold">{t('giftCards.title')}</h1>
        <p className="mt-2 text-muted-foreground">{t('giftCards.subtitle')}</p>
      </section>
      <ErrorMessage error={cards.error} />
      {cards.isPending ? (
        <div className="flex justify-center py-10 text-primary" role="status">
          <LoaderCircle className="size-6 animate-spin" aria-hidden="true" />
        </div>
      ) : null}
      {cards.data && cards.data.length === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-2xl border-2 border-dashed border-border px-6 py-10 text-center text-muted-foreground">
          <Gift className="size-10 text-accent" aria-hidden="true" />
          {t('giftCards.empty')}
        </div>
      ) : null}
      {available.length > 0 ? (
        <section className="flex flex-col gap-3">
          <h2 className="text-lg font-semibold">{t('giftCards.available')}</h2>
          {available.map((card) => (
            <GiftCardTile key={card.id} card={card} onShow={() => setShown(card)} />
          ))}
        </section>
      ) : null}
      {history.length > 0 ? (
        <section className="flex flex-col gap-3">
          <h2 className="text-lg font-semibold">{t('giftCards.history')}</h2>
          {history.map((card) => (
            <GiftCardTile key={card.id} card={card} />
          ))}
        </section>
      ) : null}
      <Dialog open={shown !== undefined} onOpenChange={(open) => (open ? undefined : setShown(undefined))}>
        {shown ? (
          <DialogContent className="text-center">
            <DialogTitle className="text-2xl font-bold">{resolveText(shown.name, currentLanguage())}</DialogTitle>
            <DialogDescription>{t('giftCards.showHint')}</DialogDescription>
            <QrCode value={shown.code ?? ''} label={t('giftCards.qrLabel')} className="mx-auto w-56 rounded-xl" />
            <p className="text-xs uppercase tracking-wider text-muted-foreground">{t('giftCards.code')}</p>
            <p className="font-mono text-3xl font-bold tracking-[0.2em] text-primary" data-testid="gift-card-code">
              {shown.code}
            </p>
            <Button variant="outline" onClick={() => setShown(undefined)}>
              {t('giftCards.close')}
            </Button>
          </DialogContent>
        ) : null}
      </Dialog>
    </div>
  );
}

function GiftCardTile({ card, onShow }: { card: GiftCard; onShow?: () => void }) {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const active = card.status === 'AVAILABLE';
  return (
    <article
      className={cn(
        'relative overflow-hidden rounded-2xl p-5 shadow-sm',
        active ? 'bg-linear-to-br from-primary via-primary to-accent text-primary-foreground' : 'bg-card text-muted-foreground',
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-lg font-bold">{resolveText(card.name, lang)}</p>
          <p className="mt-1 font-mono text-sm tracking-widest opacity-90">{card.code}</p>
        </div>
        {active ? (
          <Gift className="size-8 shrink-0 opacity-90" aria-hidden="true" />
        ) : (
          <Badge>{t(`giftCards.status.${card.status ?? 'EXPIRED'}`)}</Badge>
        )}
      </div>
      <div className="mt-4 flex flex-wrap items-end justify-between gap-3 text-xs opacity-90">
        <div className="flex flex-col gap-0.5">
          {card.earnedAt ? <span>{t('giftCards.earnedAt', { date: formatDate(card.earnedAt, lang) })}</span> : null}
          {card.status === 'REDEEMED' && card.redeemedAt ? (
            <span>{t('giftCards.redeemedAt', { date: formatDate(card.redeemedAt, lang) })}</span>
          ) : (
            <span>
              {card.expiresAt ? t('giftCards.expiresAt', { date: formatDate(card.expiresAt, lang) }) : t('giftCards.noExpiry')}
            </span>
          )}
        </div>
        {active && onShow ? (
          <Button size="sm" variant="outline" className="border-white/60 bg-white/15 text-inherit hover:bg-white/25" onClick={onShow}>
            <QrIcon aria-hidden="true" />
            {t('giftCards.show')}
          </Button>
        ) : null}
      </div>
    </article>
  );
}
