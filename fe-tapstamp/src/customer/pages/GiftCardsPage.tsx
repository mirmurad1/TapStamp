import { useQuery } from '@tanstack/react-query';
import { Gift, LoaderCircle, PartyPopper, QrCode as QrIcon } from 'lucide-react';
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
import { Confetti } from '../components/Celebration';
import { ErrorMessage } from '../components/ErrorMessage';
import { ScratchReveal } from '../components/ScratchReveal';

/** Hədiyyə kartları: istifadəyə hazır olanlar (kod və QR ilə) və tarixçə. */
export function GiftCardsPage() {
  const { t } = useTranslation('profile');
  const { status } = useCustomerSession();
  const [shown, setShown] = useState<GiftCard>();
  // Kod "poz-qazan" örtüyü altındadır — hər açılışda yenidən qazınır
  const [revealed, setRevealed] = useState(false);
  const open = (card: GiftCard) => {
    setRevealed(false);
    setShown(card);
  };
  // Kod işçiyə göstərilərkən status hər 2,5 saniyədən bir yoxlanılır — təhvil olunan kimi təbrik görünür
  const cards = useQuery({
    queryKey: customerKeys.giftCards,
    queryFn: giftCardApi.list,
    enabled: status === 'signedIn',
    refetchInterval: shown ? 2_500 : false,
  });
  const live = cards.data?.find((card) => card.id === shown?.id) ?? shown;
  const redeemed = live?.status === 'REDEEMED';

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
            <GiftCardTile key={card.id} card={card} onShow={() => open(card)} />
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
        {live && redeemed ? (
          <DialogContent className="text-center">
            <Confetti count={50} />
            <div className="mx-auto grid size-20 place-items-center rounded-full bg-success text-white">
              <PartyPopper className="size-10" aria-hidden="true" />
            </div>
            <DialogTitle className="text-2xl font-bold">{t('giftCards.redeemedTitle')}</DialogTitle>
            <DialogDescription className="text-base">
              {resolveText(live.name, currentLanguage())} · {t('giftCards.redeemedHint')}
            </DialogDescription>
            <Button onClick={() => setShown(undefined)}>{t('giftCards.close')}</Button>
          </DialogContent>
        ) : shown ? (
          // Avtomatik fokus yoxdur: əks halda klaviatura üçün olan "Kodu aç" düyməsi fokus alıb örtüyün üstündə görünərdi
          <DialogContent className="text-center" onOpenAutoFocus={(event) => event.preventDefault()}>
            <DialogTitle className="text-2xl font-bold">{resolveText(shown.name, currentLanguage())}</DialogTitle>
            <DialogDescription>{t('giftCards.showHint')}</DialogDescription>
            <QrCode value={shown.code ?? ''} label={t('giftCards.qrLabel')} className="mx-auto w-56 rounded-xl" />
            <p className="text-xs uppercase tracking-wider text-muted-foreground">{t('giftCards.code')}</p>
            <ScratchReveal
              revealed={revealed}
              onReveal={() => setRevealed(true)}
              revealLabel={t('giftCards.scratch.reveal')}
              hiddenLabel={t('giftCards.scratch.hidden')}
              className="mx-auto w-full max-w-xs bg-muted"
            >
              <p className="py-3 font-mono text-3xl font-bold tracking-[0.2em] text-primary" data-testid="gift-card-code">
                {shown.code}
              </p>
            </ScratchReveal>
            {!revealed ? <p className="text-sm text-muted-foreground">{t('giftCards.scratch.hint')}</p> : null}
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
  // Müddəti bitməyə 7 gün və daha az qalıbsa — xatırlatma
  const daysLeft = card.expiresAt
    ? Math.max(0, Math.ceil((Date.parse(card.expiresAt) - Date.now()) / 86_400_000))
    : undefined;
  return (
    <article
      className={cn(
        'relative overflow-hidden rounded-2xl p-5 shadow-sm',
        // Hazır hədiyyə kartının üstündən vaxtaşırı işıq zolağı keçir (hologram)
        active ? 'shine bg-linear-to-br from-primary via-primary to-accent text-primary-foreground' : 'bg-card text-muted-foreground',
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-lg font-bold">{resolveText(card.name, lang)}</p>
        </div>
        {active ? (
          <span className="flex flex-col items-end gap-2">
            <Gift className="size-8 shrink-0 opacity-90" aria-hidden="true" />
            {daysLeft !== undefined && daysLeft <= 7 ? (
              <span className="rounded-full bg-white px-2.5 py-0.5 text-xs font-bold text-primary">
                {t('giftCards.expiresSoon', { count: daysLeft })}
              </span>
            ) : null}
          </span>
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
