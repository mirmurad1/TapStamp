import { BookOpen, ChevronLeft, ChevronRight, Copy, Download, Share, Smartphone } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/shared/ui/button';

import { OfferRow, Sheet } from '../components/Sheet';
import { CompanyIcon } from '../components/CompanyIcon';

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
 * "Telefona quraşdırın" sətri və vərəqi: Android-də brauzerin quraşdırma pəncərəsi düymə ilə açılır (ana ekranın
 * sxemində şirkətin ikonu yerinə "düşür"), iPhone-da (Safari quraşdırma düyməsi vermir) iki addımlı təlimat
 * göstərilir. Tətbiq artıq quraşdırılıbsa, sətir görünmür.
 */
export function InstallRow() {
  const { t } = useTranslation(['profile', 'common']);
  const [promptEvent, setPromptEvent] = useState<InstallPromptEvent>();
  const [installed, setInstalled] = useState(isStandalone);
  const [open, setOpen] = useState(false);

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
    setOpen(false);
    if (choice.outcome === 'accepted') {
      setInstalled(true);
    }
  };

  return (
    <>
      <OfferRow icon={<Smartphone />} title={t('install.title')} hint={t('install.rowHint')} onClick={() => setOpen(true)} />
      <Sheet
        open={open}
        onOpenChange={setOpen}
        icon={ios ? <Smartphone /> : undefined}
        title={t('install.title')}
        description={t('install.description')}
      >
        {promptEvent && !ios ? (
          <>
            <HomeScreenPreview />
            <Button size="lg" onClick={() => void install()}>
              <Download aria-hidden="true" />
              {t('install.button')}
            </Button>
          </>
        ) : (
          <IosSteps />
        )}
        <Button size="lg" variant="outline" onClick={() => setOpen(false)}>
          {t('common:actions.close')}
        </Button>
      </Sheet>
    </>
  );
}

/** iPhone: iki ardıcıl addım; Safari panelinin sxemində "Paylaş" düyməsi yanıb-sönən halqa ilə göstərilir. */
function IosSteps() {
  const { t } = useTranslation('profile');
  return (
    <ol className="flex flex-col gap-3 text-left text-sm">
      <li className="flex items-start gap-3">
        <StepNumber value={1} />
        <span>
          {t('install.iosStep1')} <Share className="inline size-4 align-text-bottom text-primary" aria-hidden="true" />{' '}
          {t('install.share')}
        </span>
      </li>
      <li aria-hidden="true" className="flex justify-around rounded-2xl bg-muted py-2.5 text-muted-foreground">
        <ChevronLeft className="size-5" />
        <ChevronRight className="size-5" />
        <span className="relative text-primary">
          <Share className="size-5" />
          <span className="animate-stamp-ring absolute -inset-2 rounded-full border-2 border-primary [animation-iteration-count:infinite]" />
        </span>
        <BookOpen className="size-5" />
        <Copy className="size-5" />
      </li>
      <li className="flex items-start gap-3">
        <StepNumber value={2} />
        <span>{t('install.iosStepAdd')}</span>
      </li>
    </ol>
  );
}

function StepNumber({ value }: { value: number }) {
  return (
    <span className="grid size-6 shrink-0 place-items-center rounded-full bg-primary text-xs font-bold text-primary-foreground">
      {value}
    </span>
  );
}

/** Ana ekranın sxemi: şirkətin ikonu boş yerə "düşür" — quraşdırmadan sonra nə olacağı görünür. */
function HomeScreenPreview() {
  return (
    <div
      aria-hidden="true"
      className="grid grid-cols-4 gap-3 rounded-2xl bg-[linear-gradient(160deg,color-mix(in_srgb,var(--primary)_14%,#dfe4ef),#ece6dd)] p-4"
    >
      {Array.from({ length: 8 }, (_, index) =>
        index === 5 ? (
          <CompanyIcon
            key={index}
            className="animate-stamp-pop aspect-square rounded-xl text-lg leading-none shadow-md [animation-delay:0.35s]"
          />
        ) : (
          <span key={index} className="aspect-square rounded-xl bg-white/55" />
        ),
      )}
    </div>
  );
}
