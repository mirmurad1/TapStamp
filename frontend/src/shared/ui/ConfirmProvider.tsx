import { TriangleAlert } from 'lucide-react';
import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { cn } from '@/shared/lib/cn';

import { Button } from './button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './dialog';

export interface ConfirmOptions {
  /** Əsas sual/xəbərdarlıq mətni. */
  message: string;
  /** Başlıq; verilməzsə ümumi "Təsdiq edin". */
  title?: string;
  /** Təsdiq düyməsinin mətni; verilməzsə "Davam et". */
  confirmLabel?: string;
  /** Geri qaytarılmayan və ya riskli əməliyyat — təsdiq düyməsi qırmızı. */
  danger?: boolean;
}

type Confirm = (options: ConfirmOptions) => Promise<boolean>;

const ConfirmContext = createContext<Confirm | null>(null);

/**
 * Təsdiq pəncərəsi brauzerin {@code window.confirm} əvəzinə: tətbiqin dizaynında, seçilmiş dildə, klaviatura və ekran
 * oxuyucusu ilə əlçatan. İstifadə: {@code if (await confirm({ message })) ...}.
 */
export function useConfirm(): Confirm {
  const value = useContext(ConfirmContext);
  if (!value) {
    throw new Error('useConfirm must be used inside ConfirmProvider');
  }
  return value;
}

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation('common');
  const [options, setOptions] = useState<ConfirmOptions>();
  const resolver = useRef<(value: boolean) => void>(undefined);

  const confirm = useCallback<Confirm>(
    (next) =>
      new Promise<boolean>((resolve) => {
        resolver.current?.(false);
        resolver.current = resolve;
        setOptions(next);
      }),
    [],
  );

  const close = (result: boolean) => {
    resolver.current?.(result);
    resolver.current = undefined;
    setOptions(undefined);
  };

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <Dialog open={options !== undefined} onOpenChange={(open) => (open ? undefined : close(false))}>
        {options ? (
          <DialogContent>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <TriangleAlert
                  className={cn('size-5 shrink-0', options.danger ? 'text-destructive' : 'text-highlight')}
                  aria-hidden="true"
                />
                {options.title ?? t('confirm.title')}
              </DialogTitle>
              <DialogDescription className="text-base text-foreground">{options.message}</DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => close(false)}>
                {t('actions.cancel')}
              </Button>
              <Button
                autoFocus
                className={options.danger ? 'bg-destructive text-white hover:bg-destructive/90' : undefined}
                onClick={() => close(true)}
              >
                {options.confirmLabel ?? t('actions.continue')}
              </Button>
            </DialogFooter>
          </DialogContent>
        ) : null}
      </Dialog>
    </ConfirmContext.Provider>
  );
}
