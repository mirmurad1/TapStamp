import { Download, Share, Smartphone } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

/** Chrome/Edge/Samsung brauzerinin quraşdırma hadisəsi (standart tiplərdə yoxdur). */
interface InstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

function isStandalone(): boolean {
  return (
    Boolean(window.matchMedia?.('(display-mode: standalone)').matches) ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  );
}

function isIos(): boolean {
  return /iphone|ipad|ipod/i.test(navigator.userAgent);
}

/**
 * "Ana ekrana əlavə et": Android-də brauzerin quraşdırma pəncərəsi düymə ilə açılır, iPhone-da (Safari quraşdırma
 * düyməsi vermir) qısa təlimat göstərilir. Tətbiq artıq quraşdırılıbsa, kart görünmür.
 */
export function InstallCard() {
  const { t } = useTranslation('profile');
  const [promptEvent, setPromptEvent] = useState<InstallPromptEvent>();
  const [installed, setInstalled] = useState(isStandalone);

  useEffect(() => {
    const onPrompt = (event: Event) => {
      event.preventDefault();
      setPromptEvent(event as InstallPromptEvent);
    };
    const onInstalled = () => setInstalled(true);
    window.addEventListener('beforeinstallprompt', onPrompt);
    window.addEventListener('appinstalled', onInstalled);
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt);
      window.removeEventListener('appinstalled', onInstalled);
    };
  }, []);

  // iPhone-da həmişə təlimat: Safari quraşdırma pəncərəsi vermir (kompüterdəki iPhone emulyasiyasında Chrome-un
  // hadisəsi gəlsə də düymə göstərilmir)
  const ios = isIos();
  if (installed || (!promptEvent && !ios)) {
    return null;
  }

  const install = async () => {
    if (!promptEvent) {
      return;
    }
    await promptEvent.prompt();
    const choice = await promptEvent.userChoice;
    setPromptEvent(undefined);
    if (choice.outcome === 'accepted') {
      setInstalled(true);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Smartphone className="size-5 text-primary" aria-hidden="true" />
          {t('install.title')}
        </CardTitle>
        <CardDescription>{t('install.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        {promptEvent && !ios ? (
          <Button onClick={() => void install()}>
            <Download className="size-4" aria-hidden="true" />
            {t('install.button')}
          </Button>
        ) : (
          <p className="text-sm">
            {t('install.iosStep1')}{' '}
            <Share className="inline size-4 align-text-bottom text-primary" aria-label={t('install.share')} />{' '}
            {t('install.iosStep2')}
          </p>
        )}
      </CardContent>
    </Card>
  );
}
