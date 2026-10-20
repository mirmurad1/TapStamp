import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, BellOff, X } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

import { pushApi } from '../api/customerApi';
import { currentSubscription, disablePush, enablePush, notificationPermission, pushSupport } from './push';

const SUBSCRIPTION_KEY = ['push-subscription'] as const;
const DISMISSED_KEY = 'tapstamp.push-offer-dismissed';

function offerDismissed(): boolean {
  try {
    return localStorage.getItem(DISMISSED_KEY) === '1';
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
 * Ana səhifədə bir dəfəlik təklif: push dəstəklənir, icazə hələ soruşulmayıb və müştəri təklifi bağlamayıb.
 * iPhone-da yalnız ana ekrana əlavə olunmuş tətbiqdə göstərilir.
 */
export function NotificationsOffer() {
  const { t } = useTranslation('profile');
  const { config, subscribed, loaded } = usePushState();
  const toggle = useToggle();
  const [dismissed, setDismissed] = useState(offerDismissed);
  if (!loaded || !config?.enabled || subscribed || dismissed || pushSupport() !== 'supported') {
    return null;
  }
  if (notificationPermission() !== 'default') {
    return null;
  }
  const dismiss = () => {
    try {
      localStorage.setItem(DISMISSED_KEY, '1');
    } catch {
      // Yaddaş bağlıdır — təklif növbəti dəfə yenə görünə bilər
    }
    setDismissed(true);
  };
  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-2">
        <div>
          <CardTitle className="flex items-center gap-2">
            <Bell className="size-5 text-primary" aria-hidden="true" />
            {t('push.offerTitle')}
          </CardTitle>
          <CardDescription>{t('push.offerDescription')}</CardDescription>
        </div>
        <Button variant="ghost" size="icon" className="-mr-2 -mt-2 shrink-0" onClick={dismiss} aria-label={t('push.dismiss')}>
          <X aria-hidden="true" />
        </Button>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {toggle.error ? <Alert tone="error">{t(`push.errors.${toggle.error}`)}</Alert> : null}
        <Button className="self-start" disabled={toggle.busy} onClick={() => void toggle.enable()}>
          <Bell aria-hidden="true" />
          {t('push.enable')}
        </Button>
      </CardContent>
    </Card>
  );
}
