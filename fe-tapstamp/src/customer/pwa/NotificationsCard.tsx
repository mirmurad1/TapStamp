import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, BellOff } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';
import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

import { pushApi } from '../api/customerApi';
import { useCustomerSession } from '../auth/CustomerSessionProvider';
import { OfferRow, Sheet } from '../components/Sheet';
import { useCompanyInitial, useCompanyName } from '../lib/companyInitial';
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
 * Ana səhifədə "Hədiyyəni qaçırma" sətri və vərəqi: push dəstəklənir, icazə hələ soruşulmayıb və müştəri təklifi
 * bağlamayıb. iPhone-da yalnız ana ekrana əlavə olunmuş tətbiqdə göstərilir. Vərəqdə nümunə bildirişlər müştəriyə nə
 * alacağını göstərir.
 */
export function NotificationsRow() {
  const { t } = useTranslation(['profile', 'common']);
  const { me } = useCustomerSession();
  const { config, subscribed, loaded } = usePushState();
  const toggle = useToggle();
  const [dismissed, setDismissed] = useState(offerDismissed);
  const [open, setOpen] = useState(false);
  const companyName = useCompanyName();
  const initial = useCompanyInitial();
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
    setOpen(false);
    setDismissed(true);
  };
  const card = me?.card;
  const total = card?.stampsRequired ?? 9;
  const reward = resolveText(card?.rewardName, currentLanguage());
  const previews = [
    { text: reward ? t('push.previewReward', { reward }) : t('celebrate.subtitleGeneric'), time: t('push.previewNow') },
    { text: t('push.previewStamp', { count: Math.min((card?.currentStamps ?? 0) + 1, total), total }), time: '' },
  ];
  return (
    <>
      <OfferRow icon={<Bell />} title={t('push.offerTitle')} hint={t('push.rowHint')} onClick={() => setOpen(true)} />
      <Sheet
        open={open}
        onOpenChange={setOpen}
        icon={<Bell className="animate-bell" />}
        title={t('push.offerTitle')}
        description={t('push.offerDescription')}
      >
        <div className="flex flex-col gap-2" aria-hidden="true">
          {previews.map((preview, index) => (
            <div
              key={index}
              className={cn(
                'animate-drop-in flex items-start gap-2.5 rounded-2xl border border-border bg-background p-3 text-left text-[13px] shadow-sm',
                index > 0 && 'scale-[0.97] opacity-75',
              )}
              style={{ animationDelay: `${0.3 + index * 0.3}s` }}
            >
              <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-primary text-xs font-extrabold text-primary-foreground">
                {initial}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block font-semibold">{companyName}</span>
                {preview.text}
              </span>
              <span className="text-[11px] text-muted-foreground">{preview.time}</span>
            </div>
          ))}
        </div>
        {toggle.error ? <Alert tone="error">{t(`push.errors.${toggle.error}`)}</Alert> : null}
        <Button size="lg" disabled={toggle.busy} onClick={() => void toggle.enable()}>
          <Bell aria-hidden="true" />
          {t('push.enable')}
        </Button>
        <Button size="lg" variant="outline" onClick={dismiss}>
          {t('push.dismiss')}
        </Button>
      </Sheet>
    </>
  );
}
