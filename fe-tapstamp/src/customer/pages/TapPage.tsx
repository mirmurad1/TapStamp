import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Hourglass, LoaderCircle, MapPin, Nfc, TimerReset, TriangleAlert } from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';

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
import { Celebration } from '../components/Celebration';
import { STAMP_BURST_MS, StampBurst } from '../components/StampBurst';
import { useCompanyName } from '../lib/companyInitial';
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
  const navigate = useNavigate();
  const started = useRef(false);
  const claim = useMutation({
    mutationFn: () => tapApi.claim(token),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: customerKeys.me });
      void queryClient.invalidateQueries({ queryKey: customerKeys.giftCards });
      if (result.giftCardId || result.reachedTier) {
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
      <Celebration
        open={celebrate}
        rewardName={rewardName || undefined}
        cardFull={Boolean(claim.data.giftCardId)}
        tier={
          claim.data.reachedTier
            ? {
                name: resolveText(claim.data.reachedTier, currentLanguage()),
                gift: Boolean(claim.data.tierGiftCardId),
                stamps: claim.data.reachedTierStamps ?? 0,
              }
            : undefined
        }
        onClose={() => {
          setCelebrate(false);
          navigate('/', { replace: true });
        }}
      />
    </>
  );
}

/** Kart dolanda təbrik pəncərəsi möhür səhnəsi bitəndən sonra açılır. */
const STAMP_ANIMATION_MS = STAMP_BURST_MS;
/** Səhnə (~3 san) bitəndən sonra kartı və zolağı görməyə vaxt, sonra kart ekranına keçid. */
const SUCCESS_REDIRECT_MS = 5000;
/** Təsdiq gözləyən möhürdə mətni oxumağa vaxt. */
const PENDING_REDIRECT_MS = 4000;

/**
 * Möhür vurulandan sonrakı ekran: tam ekran möhür səhnəsi (dalğa, zərbə, sayğac, kart). Animasiyalar bitəndən sonra
 * ana səhifə (kart) avtomatik açılır.
 */
function ClaimSuccess({ result, branch }: { result: ClaimResult; branch: string }) {
  const { t } = useTranslation('profile');
  const pending = result.status === 'PENDING';
  const completed = Boolean(result.giftCardId);
  const companyName = useCompanyName();
  const navigate = useNavigate();

  // Animasiyalar bitəndən sonra kart ekranı avtomatik açılır (geri düyməsi istifadə olunmuş toxunuşa qaytarmır).
  // Kart dolubsa, təbrik pəncərəsi açılır — keçid onu bağlayanda olur.
  useEffect(() => {
    if (completed) {
      return undefined;
    }
    const timer = window.setTimeout(() => navigate('/', { replace: true }), pending ? PENDING_REDIRECT_MS : SUCCESS_REDIRECT_MS);
    return () => window.clearTimeout(timer);
  }, [completed, pending, navigate]);

  if (pending) {
    return (
      <div className="flex flex-col items-center gap-3 pt-10 text-center">
        <span className="animate-gift-breathe grid size-28 place-items-center rounded-full bg-highlight text-white shadow-lg">
          <Hourglass className="size-12" aria-hidden="true" />
        </span>
        <h1 className="mt-3 text-2xl font-extrabold tracking-tight">{t('tap.pending')}</h1>
        <p className="text-muted-foreground">{t('tap.pendingHint')}</p>
        {branch ? (
          <p className="flex items-center gap-1 text-sm text-muted-foreground">
            <MapPin className="size-4" aria-hidden="true" />
            {branch}
          </p>
        ) : null}
      </div>
    );
  }

  const required = result.stampsRequired ?? 9;
  // Kart dolanda server sayğacı sıfırlayır — səhnədə dolu kart göstərilir
  const after = completed ? required : Math.min(Math.max(result.currentStamps ?? 1, 1), required);
  const remaining = required - after;
  return (
    <StampBurst
      before={after - 1}
      after={after}
      stampsRequired={required}
      companyName={companyName}
      branch={branch}
      progressText={remaining > 0 ? t('card.progress', { count: remaining }) : t('card.complete')}
      redirectMs={completed ? undefined : SUCCESS_REDIRECT_MS - STAMP_BURST_MS}
    />
  );
}

const FINAL_TAP_ERRORS = ['BRANCH_CLOSED', 'TAG_DISABLED', 'OUTSIDE_BUSINESS_HOURS'] as const;

export function TapError({ error, message }: { error?: unknown; message?: string }) {
  const { t } = useTranslation('profile');
  const errorMessage = useErrorMessage();
  const retryAfter = isApiError(error) ? error.problem.retryAfterSeconds : undefined;
  const [until] = useState(() => (retryAfter ? Date.now() + retryAfter * 1000 : undefined));
  const secondsLeft = useSecondsUntil(until);
  const cooldown = isApiError(error, 'STAMP_COOLDOWN_ACTIVE') || isApiError(error, 'STAMP_DAILY_LIMIT_REACHED');
  // Təkrar toxunmaq kömək etmir: filial bağlıdır, çip deaktivdir və ya filial iş saatından kənardadır
  const final = FINAL_TAP_ERRORS.some((code) => isApiError(error, code));
  return (
    <Card className="animate-fade-up">
      <CardHeader className="items-center text-center">
        <span className="mb-2 grid size-16 place-items-center rounded-full bg-muted text-[var(--highlight-ink)]">
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
        {!cooldown && !final ? <p className="text-sm text-muted-foreground">{t('tap.tryAgain')}</p> : null}
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
