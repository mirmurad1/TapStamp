import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Hourglass, LoaderCircle, MapPin, Nfc, Stamp, Star, TimerReset, TriangleAlert } from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams, useSearchParams } from 'react-router';

import { ApiError, isApiError } from '@/shared/api/client';
import { useErrorMessage } from '@/shared/api/useErrorMessage';
import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatCountdown } from '@/shared/lib/brand';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

import { publicApi, tapApi, type ClaimResult } from '../api/customerApi';
import { useCustomerSession } from '../auth/CustomerSessionProvider';
import { customerKeys } from '../auth/queryClient';
import { Celebration, Confetti } from '../components/Celebration';
import { useSecondsUntil } from '../lib/useSecondsUntil';

/**
 * NFC toxunuşundan sonrakı səhifə ({@code /tap/:token}). Daxil olmuş müştəri üçün möhür avtomatik alınır;
 * qonaq girişə yönəldilir (toxunuş 10 dəqiqə saxlanılır) və girişdən sonra bura qayıdır.
 */
export function TapPage() {
  const { token = '' } = useParams();
  const { t } = useTranslation(['profile', 'common']);
  const { status: session } = useCustomerSession();
  const tap = useQuery({ queryKey: ['tap', token], queryFn: () => tapApi.status(token), retry: false });

  if (session === 'loading' || tap.isPending) {
    return <Centered icon={<LoaderCircle className="size-8 animate-spin" />} text={t('profile:tap.checking')} />;
  }
  if (tap.isError) {
    return <TapError error={tap.error} />;
  }
  const branch = resolveText(tap.data.branchName, currentLanguage());
  if (!tap.data.sameBrowser) {
    return <TapError message={t('profile:tap.otherBrowser')} />;
  }
  if (session === 'guest') {
    if (tap.data.status !== 'ACTIVE' && tap.data.status !== 'RESERVED') {
      return <TapError error={new ApiError(410, 'TAP_TOKEN_EXPIRED')} />;
    }
    return (
      <Card className="animate-fade-up">
        <CardHeader className="items-center text-center">
          <span className="mb-2 grid size-16 place-items-center rounded-full bg-primary text-primary-foreground">
            <Nfc className="size-8" aria-hidden="true" />
          </span>
          <CardTitle>{t('profile:tap.title')}</CardTitle>
          {branch ? (
            <p className="flex items-center gap-1 text-sm text-muted-foreground">
              <MapPin className="size-4" aria-hidden="true" />
              {branch}
            </p>
          ) : null}
          <CardDescription>{t('profile:tap.signInToCollect')}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button asChild size="lg" className="w-full">
            <Link to={`/login?tap=${encodeURIComponent(token)}`}>{t('profile:tap.signIn')}</Link>
          </Button>
        </CardContent>
      </Card>
    );
  }
  return <Claim token={token} branch={branch} />;
}

function Claim({ token, branch }: { token: string; branch: string }) {
  const { t } = useTranslation('profile');
  const queryClient = useQueryClient();
  const company = useQuery({ queryKey: ['public-company'], queryFn: publicApi.company, staleTime: 5 * 60_000 });
  const [celebrate, setCelebrate] = useState(false);
  const started = useRef(false);
  const claim = useMutation({
    mutationFn: () => tapApi.claim(token),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: customerKeys.me });
      void queryClient.invalidateQueries({ queryKey: customerKeys.giftCards });
      if (result.giftCardId) {
        // Böyük təbrik möhür animasiyası bitəndən sonra açılır
        window.setTimeout(() => setCelebrate(true), STAMP_ANIMATION_MS);
      }
    },
  });

  // Səhifə açılan kimi bir dəfə (StrictMode-da da); təkrar göndəriş server tərəfində də idempotentdir
  useEffect(() => {
    if (!started.current) {
      started.current = true;
      claim.mutate();
    }
  }, [claim]);

  if (claim.isError) {
    return <TapError error={claim.error} />;
  }
  if (!claim.data) {
    return <Centered icon={<LoaderCircle className="size-8 animate-spin" />} text={t('tap.collecting')} />;
  }
  const rewardName = resolveText(company.data?.rewardRule?.rewardName, currentLanguage());
  return (
    <>
      <ClaimSuccess result={claim.data} branch={branch} />
      <Celebration open={celebrate} rewardName={rewardName || undefined} onClose={() => setCelebrate(false)} />
    </>
  );
}

/** Möhür animasiyasının uzunluğu (damğa enir, kağıza vurulur, iz qalır). */
const STAMP_ANIMATION_MS = 2200;

/**
 * Möhür vurulandan sonrakı ekran: damğa animasiyası, "Möhür əlavə olundu!" və konfeti. Kart və digər bölmələr ana
 * səhifədədir ("Kartıma bax").
 */
function ClaimSuccess({ result, branch }: { result: ClaimResult; branch: string }) {
  const { t } = useTranslation('profile');
  const pending = result.status === 'PENDING';
  const completed = Boolean(result.giftCardId);
  const [impact, setImpact] = useState(false);

  // Damğa kağıza dəyən an konfeti başlayır
  useEffect(() => {
    if (pending) {
      return undefined;
    }
    const timer = window.setTimeout(() => setImpact(true), 1100);
    return () => window.clearTimeout(timer);
  }, [pending]);

  return (
    <div className="flex flex-col items-center gap-6 pt-6 text-center">
      {impact && !completed ? <Confetti count={28} small /> : null}
      {pending ? (
        <span className="animate-fade-up grid size-20 place-items-center rounded-full bg-highlight text-white">
          <Hourglass className="size-10" aria-hidden="true" />
        </span>
      ) : (
        <StampImpression />
      )}
      <section className="animate-fade-up flex flex-col items-center gap-2" style={pending ? undefined : { animationDelay: '1.3s' }}>
        <h1 className="text-2xl font-bold">{pending ? t('tap.pending') : t('tap.stamped')}</h1>
        {pending ? <p className="text-muted-foreground">{t('tap.pendingHint')}</p> : null}
        {branch ? (
          <p className="flex items-center gap-1 text-sm text-muted-foreground">
            <MapPin className="size-4" aria-hidden="true" />
            {branch}
          </p>
        ) : null}
      </section>
      <Button asChild size="lg" className="animate-fade-up w-full" style={pending ? undefined : { animationDelay: '1.6s' }}>
        <Link to={completed ? '/gift-cards' : '/'}>{completed ? t('celebrate.open') : t('tap.home')}</Link>
      </Button>
    </div>
  );
}

/**
 * Damğa animasiyası (~2,2 san): möhür yuxarıdan enir, kağıza vurulur (kiçik silkələnmə və mürəkkəb dalğası),
 * qalxıb gedir, yerində şirkətin rəngində möhür izi qalır.
 */
function StampImpression() {
  return (
    <div className="animate-stamp-shake relative grid size-40 place-items-center" aria-hidden="true">
      {/* Kağızda qalan iz */}
      <span className="animate-stamp-imprint absolute inset-3 grid place-items-center rounded-full border-4 border-primary text-primary">
        <span className="absolute inset-2 rounded-full border-2 border-dashed border-primary/70" />
        <Star className="size-14" fill="currentColor" />
      </span>
      {/* Mürəkkəb dalğası */}
      <span className="animate-stamp-ink absolute inset-3 rounded-full border-4 border-primary" />
      {/* Damğanın özü */}
      <span className="animate-stamp-press absolute inset-0 grid place-items-center text-foreground">
        <Stamp className="size-24 drop-shadow-lg" strokeWidth={1.5} />
      </span>
    </div>
  );
}

export function TapError({ error, message }: { error?: unknown; message?: string }) {
  const { t } = useTranslation('profile');
  const errorMessage = useErrorMessage();
  const retryAfter = isApiError(error) ? error.problem.retryAfterSeconds : undefined;
  const [until] = useState(() => (retryAfter ? Date.now() + retryAfter * 1000 : undefined));
  const secondsLeft = useSecondsUntil(until);
  const cooldown = isApiError(error, 'STAMP_COOLDOWN_ACTIVE') || isApiError(error, 'STAMP_DAILY_LIMIT_REACHED');
  return (
    <Card className="animate-fade-up">
      <CardHeader className="items-center text-center">
        <span className="mb-2 grid size-16 place-items-center rounded-full bg-muted text-highlight">
          {cooldown ? <TimerReset className="size-8" aria-hidden="true" /> : <TriangleAlert className="size-8" aria-hidden="true" />}
        </span>
        <CardTitle>{t('tap.errorTitle')}</CardTitle>
        <CardDescription role="alert" className="text-base">
          {message ?? errorMessage(error)}
        </CardDescription>
        {cooldown && secondsLeft > 0 ? (
          <p className="text-3xl font-bold tabular-nums text-primary" aria-live="polite">
            {formatCountdown(secondsLeft)}
          </p>
        ) : null}
        {!cooldown ? <p className="text-sm text-muted-foreground">{t('tap.tryAgain')}</p> : null}
      </CardHeader>
      <CardContent>
        <Button asChild size="lg" variant="outline" className="w-full">
          <Link to="/">{t('tap.home')}</Link>
        </Button>
      </CardContent>
    </Card>
  );
}

function Centered({ icon, text }: { icon: ReactNode; text: string }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16 text-muted-foreground" role="status">
      <span className="text-primary" aria-hidden="true">
        {icon}
      </span>
      {text}
    </div>
  );
}

/** {@code /t/...} yoxlaması alınmayanda backend bura yönləndirir: {@code /tap/error?code=TAP_REPLAYED}. */
export function TapErrorPage() {
  const [params] = useSearchParams();
  const code = (params.get('code') ?? 'UNKNOWN').replace(/[^A-Z_]/g, '').slice(0, 40) || 'UNKNOWN';
  return <TapError error={new ApiError(400, code)} />;
}
