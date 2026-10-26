import { useQuery } from '@tanstack/react-query';
import { ChevronRight, Gift, LoaderCircle, PartyPopper, QrCode as QrIcon } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';
import { formatDate } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { QrCode } from '@/shared/ui/QrCode';

import { giftCardApi, type GiftCard } from '../api/customerApi';
import { useCustomerSession } from '../auth/CustomerSessionProvider';
import { customerKeys } from '../auth/queryClient';
import { Confetti } from '../components/Celebration';
import { ErrorMessage } from '../components/ErrorMessage';
import { ScratchReveal } from '../components/ScratchReveal';
import { Sheet } from '../components/Sheet';

/** Hədiyyə kartları: istifadəyə hazır olanlar (kod və QR ilə) və tarixçə. */
export function GiftCardsPage() {
  const { t } = useTranslation('profile');
  const { status, me } = useCustomerSession();
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
  const lang = currentLanguage();

  return (
    <div className="flex flex-col gap-4 pt-2">
      <section>
        <h1 className="font-display text-3xl font-extrabold tracking-tight">{t('giftCards.title')}</h1>
        <p className="mt-1.5 text-sm text-muted-foreground">{t('giftCards.subtitle')}</p>
      </section>
      <ErrorMessage error={cards.error} />
      {cards.isPending ? (
        <div className="flex justify-center py-10 text-primary" role="status">
          <LoaderCircle className="size-6 animate-spin" aria-hidden="true" />
        </div>
      ) : null}
      {cards.data && cards.data.length === 0 ? <EmptyState current={me?.card?.currentStamps} required={me?.card?.stampsRequired} /> : null}
      {available.length > 0 ? (
        <section className="flex flex-col gap-3">
          <h2 className="text-base font-bold">{t('giftCards.available')}</h2>
          {available.map((card) => (
            <GiftCardTile key={card.id} card={card} onShow={() => open(card)} />
          ))}
        </section>
      ) : null}
      {history.length > 0 ? (
        <section className="flex flex-col gap-3">
          <h2 className="text-base font-bold">{t('giftCards.history')}</h2>
          {history.map((card) => (
            <GiftCardTile key={card.id} card={card} />
          ))}
        </section>
      ) : null}
      {live && redeemed ? (
        <>
          <Confetti count={50} />
          <Sheet
            open={shown !== undefined}
            onOpenChange={(next) => (next ? undefined : setShown(undefined))}
            icon={<PartyPopper />}
            title={t('giftCards.redeemedTitle')}
            description={`${resolveText(live.name, lang)} · ${t('giftCards.redeemedHint')}`}
          >
            <Button size="lg" onClick={() => setShown(undefined)}>
              {t('giftCards.close')}
            </Button>
          </Sheet>
        </>
      ) : (
        // Avtomatik fokus yoxdur: əks halda klaviatura üçün olan "Kodu aç" düyməsi fokus alıb örtüyün üstündə görünərdi
        <Sheet
          open={shown !== undefined}
          onOpenChange={(next) => (next ? undefined : setShown(undefined))}
          title={resolveText(shown?.name, lang)}
          description={t('giftCards.showHint')}
          preventAutoFocus
        >
          {shown ? (
            <>
              <QrCode value={shown.code ?? ''} label={t('giftCards.qrLabel')} className="mx-auto w-52 rounded-xl" />
              <p className="text-xs text-muted-foreground">{t('giftCards.code')}</p>
              <ScratchReveal
                revealed={revealed}
                onReveal={() => setRevealed(true)}
                revealLabel={t('giftCards.scratch.reveal')}
                hiddenLabel={t('giftCards.scratch.hidden')}
                className="mx-auto w-full max-w-xs bg-primary/10"
              >
                <p className="py-3 font-mono text-3xl font-bold tracking-[0.2em] text-primary" data-testid="gift-card-code">
                  {shown.code}
                </p>
              </ScratchReveal>
              {!revealed ? <p className="text-sm text-muted-foreground">{t('giftCards.scratch.hint')}</p> : null}
              <Button size="lg" variant="outline" onClick={() => setShown(undefined)}>
                {t('giftCards.close')}
              </Button>
            </>
          ) : null}
        </Sheet>
      )}
    </div>
  );
}

/** Boş vəziyyət: hələ hədiyyə kartı yoxdur — hədəfə nə qədər qaldığı göstərilir. */
function EmptyState({ current = 0, required }: { current?: number; required?: number }) {
  const { t } = useTranslation('profile');
  return (
    <div className="flex flex-col items-center gap-3 rounded-3xl border-2 border-dashed border-border px-6 py-9 text-center text-muted-foreground">
      <span className="grid size-14 place-items-center rounded-full bg-primary/10 text-primary">
        <Gift className="size-7" aria-hidden="true" />
      </span>
      {t('giftCards.empty')}
      {required ? (
        <div className="w-full max-w-60">
          <p className="mb-1.5 flex justify-between text-xs font-semibold text-foreground">
            <span>{t('card.progress', { count: Math.max(required - current, 0) })}</span>
            <span className="text-primary">
              {current}/{required}
            </span>
          </p>
          <div className="h-1.5 overflow-hidden rounded-full bg-muted">
            <div className="animate-progress-grow h-full rounded-full bg-primary" style={{ width: `${(current / required) * 100}%` }} />
          </div>
        </div>
      ) : null}
    </div>
  );
}

/**
 * Hədiyyə kartı bilet kimidir: üstdə ad və tarixlər, perforasiya xətti (iki yarımdairə kəsik), altda "Kodu göstər".
 * Hazır kart brend rəngindədir və üstündən vaxtaşırı işıq keçir; tarixçədəki kartlar solğundur.
 */
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
        'relative overflow-hidden rounded-3xl',
        active ? 'shine bg-primary text-primary-foreground shadow-md' : 'border border-border bg-card text-muted-foreground',
      )}
    >
      <div className="flex items-start justify-between gap-3 px-5 pt-4 pb-3.5">
        <div className="min-w-0">
          <p className={cn('font-extrabold tracking-tight', active ? 'text-lg' : 'text-base text-foreground')}>
            {resolveText(card.name, lang)}
          </p>
          <p className="mt-0.5 flex flex-col text-xs opacity-90">
            {card.earnedAt ? <span>{t('giftCards.earnedAt', { date: formatDate(card.earnedAt, lang) })}</span> : null}
            {card.status === 'REDEEMED' && card.redeemedAt ? (
              <span>{t('giftCards.redeemedAt', { date: formatDate(card.redeemedAt, lang) })}</span>
            ) : (
              <span>
                {card.expiresAt ? t('giftCards.expiresAt', { date: formatDate(card.expiresAt, lang) }) : t('giftCards.noExpiry')}
              </span>
            )}
          </p>
        </div>
        {active ? (
          daysLeft !== undefined && daysLeft <= 7 ? (
            <span className="shrink-0 rounded-full bg-white px-2.5 py-0.5 text-xs font-bold whitespace-nowrap text-primary">
              {t('giftCards.expiresSoon', { count: daysLeft })}
            </span>
          ) : (
            <Gift className="size-7 shrink-0 opacity-90" aria-hidden="true" />
          )
        ) : (
          <Badge>{t(`giftCards.status.${card.status ?? 'EXPIRED'}`)}</Badge>
        )}
      </div>
      {active && onShow ? (
        <>
          {/* Perforasiya: qırıq xətt və kənarlarda yarımdairə kəsiklər */}
          <div className="relative mx-4 border-t-2 border-dashed border-white/35" aria-hidden="true">
            <span className="absolute -top-[9px] -left-6 size-4 rounded-full bg-background" />
            <span className="absolute -top-[9px] -right-6 size-4 rounded-full bg-background" />
          </div>
          <button
            type="button"
            onClick={onShow}
            className="flex w-full items-center gap-2 px-5 py-3.5 text-sm font-bold focus-visible:bg-white/10 focus-visible:outline-none"
          >
            <QrIcon className="size-[18px]" aria-hidden="true" />
            {t('giftCards.show')}
            <ChevronRight className="ml-auto size-5" aria-hidden="true" />
          </button>
        </>
      ) : null}
    </article>
  );
}
