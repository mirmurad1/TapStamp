import { LoaderCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';

export function FullPageLoading() {
  const { t } = useTranslation('common');
  return (
    <div className="grid min-h-dvh place-items-center" role="status" aria-live="polite">
      <div className="flex items-center gap-3 text-muted-foreground">
        <LoaderCircle className="size-5 animate-spin" aria-hidden="true" />
        <span>{t('states.loading')}</span>
      </div>
    </div>
  );
}

export function FullPageError({ message, onRetry }: { message: string; onRetry: () => void }) {
  const { t } = useTranslation('common');
  return (
    <div className="grid min-h-dvh place-items-center px-4">
      <div className="flex w-full max-w-sm flex-col gap-4">
        <Alert tone="error">{message}</Alert>
        <Button onClick={onRetry}>{t('actions.retry')}</Button>
      </div>
    </div>
  );
}

/** Kartın içində kiçik yüklənmə göstəricisi. */
export function InlineLoading() {
  const { t } = useTranslation('common');
  return (
    <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground" role="status">
      <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
      <span>{t('states.loading')}</span>
    </div>
  );
}
