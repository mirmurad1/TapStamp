import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, BellOff, X } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

import { pushApi } from '../api/customerApi';
import { useCustomerSession } from '../auth/CustomerSessionProvider';
import { CompanyIcon } from '../components/CompanyIcon';
import { useCompanyName } from '../lib/companyInitial';
import { currentSubscription, disablePush, enablePush, notificationPermission, pushSupport } from './push';

const SUBSCRIPTION_KEY = ['push-subscription'] as const;
const DISMISSED_KEY = 'tapstamp.push-offer-dismissed';
/** Bağlanan banner bu müddətdən sonra yenidən görünür. */
const SNOOZE_MS = 7 * 24 * 60 * 60 * 1000;

/** Banner bağlanıbmı: yaddaşda "bu vaxta qədər gizlət" anı saxlanılır (köhnə "1" dəyəri artıq keçmiş sayılır). */
function offerDismissed(): boolean {
  try {
    const until = Number(localStorage.getItem(DISMISSED_KEY));
    return Number.isFinite(until) && until > Date.now();
  } catch {
    return false;
  }
}

/** Bildirişlərin vəziyyəti: serverdə push qurulubmu, brauzer dəstəkləyirmi, bu cihaz abunədirmi. */
function usePushState() {
  const config = useQuery({ queryKey: ['push-config'], queryFn: pushApi.config, staleTime: 10 * 60_000 });
  const subscription = useQuery({ queryKey: SUBSCRIPTION_KEY, queryFn: currentSubscription });
  return { config: config.data, subscribed: Boolean(subscription.data), loaded: subscription.isFetched && config.isFetched };
}

function useToggle() {
  const queryClient = useQueryClient();
  const { config } = usePushState();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<'denied' | 'failed'>();
  const run = async (action: () => Promise<boolean | void>) => {
    setBusy(true);
    setError(undefined);
    try {
      const result = await action();
      if (result === false) {
        setError('denied');
      }
    } catch {
      setError('failed');
    } finally {
      setBusy(false);
      await queryClient.invalidateQueries({ queryKey: SUBSCRIPTION_KEY });
    }
  };
  return {
    busy,
    error,
    enable: () => run(() => enablePush(config?.publicKey ?? '')),
    disable: () => run(disablePush),
  };
}

/** Parametrlər: bu cihazda bildirişləri açmaq/söndürmək. Serverdə push qurulmayıbsa görünmür. */
export function NotificationsSettings() {
  const { t } = useTranslation('profile');
  const { config, subscribed } = usePushState();
  const toggle = useToggle();
  if (!config?.enabled) {
    return null;
  }
  const support = pushSupport();
  const denied = notificationPermission() === 'denied';
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Bell className="size-5 text-primary" aria-hidden="true" />
          {t('push.title')}
        </CardTitle>
        <CardDescription>{t('push.description')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {support === 'needs-install' ? <Alert tone="info">{t('push.needsInstall')}</Alert> : null}
        {support === 'unsupported' ? <Alert tone="info">{t('push.unsupported')}</Alert> : null}
        {support === 'supported' && denied && !subscribed ? <Alert tone="info">{t('push.blocked')}</Alert> : null}
        {toggle.error ? <Alert tone="error">{t(`push.errors.${toggle.error}`)}</Alert> : null}
        {support === 'supported' && !(denied && !subscribed) ? (
          subscribed ? (
            <Button variant="outline" className="self-start" disabled={toggle.busy} onClick={() => void toggle.disable()}>
              <BellOff aria-hidden="true" />
              {t('push.disable')}
            </Button>
          ) : (
            <Button className="self-start" disabled={toggle.busy} onClick={() => void toggle.enable()}>
              <Bell aria-hidden="true" />
              {t('push.enable')}
            </Button>
          )
        ) : null}
        {subscribed ? <p className="text-sm text-muted-foreground">{t('push.enabledOnDevice')}</p> : null}
      </CardContent>
    </Card>
  );
}

/**
 * Ana səhifənin yuxarısında "Hədiyyəni qaçırma" banneri: push dəstəklənir, icazə hələ soruşulmayıb və müştəri onu
 * son 7 gündə bağlamayıb. iPhone-da yalnız ana ekrana əlavə olunmuş tətbiqdə göstərilir. Nümunə bildiriş müştəriyə
 * nə alacağını göstərir; "Bildirişləri aç" bir toxunuşla icazə istəyir.
 */
export function NotificationsBanner() {
  const { t } = useTranslation(['profile', 'common']);
  const { me } = useCustomerSession();
  const { config, subscribed, loaded } = usePushState();
  const toggle = useToggle();
  const [dismissed, setDismissed] = useState(offerDismissed);
  const companyName = useCompanyName();
  if (!loaded || !config?.enabled || subscribed || dismissed || pushSupport() !== 'supported') {
    return null;
  }
  if (notificationPermission() !== 'default') {
    return null;
  }
  const dismiss = () => {
    try {
      localStorage.setItem(DISMISSED_KEY, String(Date.now() + SNOOZE_MS));
    } catch {
      // Yaddaş bağlıdır — banner növbəti dəfə yenə görünə bilər
    }
    setDismissed(true);
  };
  const card = me?.card;
  const reward = resolveText(card?.rewardName, currentLanguage());
  return (
    <section
      aria-labelledby="push-banner-title"
      className="shine animate-drop-in relative overflow-hidden rounded-2xl bg-primary p-4 text-primary-foreground shadow-md [--shine-delay:1.4s]"
    >
      <button
        type="button"
        onClick={dismiss}
        aria-label={t('push.later')}
        className="absolute top-2 right-2 z-10 grid size-9 place-items-center rounded-full opacity-80 transition hover:bg-white/15 hover:opacity-100 focus-visible:ring-2 focus-visible:ring-white focus-visible:outline-none"
      >
        <X className="size-4" aria-hidden="true" />
      </button>
      <div className="flex items-start gap-3 pr-8">
        {/* Zəng vaxtaşırı yellənir, qırmızı nöqtə "oxunmamış bildiriş" hissi yaradır */}
        <span className="relative grid size-11 shrink-0 place-items-center rounded-xl bg-white/20" aria-hidden="true">
          <Bell className="animate-bell size-6" />
          <span className="absolute top-1.5 right-1.5 size-2.5 rounded-full bg-red-500 ring-2 ring-primary" />
        </span>
        <div className="min-w-0">
          <p id="push-banner-title" className="text-base leading-snug font-bold text-balance">
            {t('push.offerTitle')}
          </p>
          <p className="mt-0.5 text-sm opacity-90">{t('push.bannerText')}</p>
        </div>
      </div>
      {/* Nümunə bildiriş: müştəri telefonunda nə görəcəyini əvvəlcədən görür */}
      <div
        className="mt-3 flex items-center gap-2.5 rounded-xl bg-card p-2.5 text-left text-[13px] text-card-foreground shadow-sm"
        aria-hidden="true"
      >
        <CompanyIcon className="size-8 rounded-lg text-xs" />
        <span className="min-w-0 flex-1">
          <span className="block truncate font-semibold">{companyName}</span>
          <span className="line-clamp-2 leading-snug">
            {reward ? t('push.previewReward', { reward }) : t('celebrate.subtitleGeneric')}
          </span>
        </span>
        <span className="shrink-0 self-start text-[11px] text-muted-foreground">{t('push.previewNow')}</span>
      </div>
      {toggle.error ? (
        <Alert tone="error" className="mt-3">
          {t(`push.errors.${toggle.error}`)}
        </Alert>
      ) : null}
      <Button
        size="lg"
        className="mt-3 w-full bg-white font-bold text-primary hover:bg-white/90"
        disabled={toggle.busy}
        onClick={() => void toggle.enable()}
      >
        <Bell aria-hidden="true" />
        {t('push.enable')}
      </Button>
    </section>
  );
}
