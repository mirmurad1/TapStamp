import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';

import { isApiError } from '@/shared/api/client';
import { Button } from '@/shared/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Input } from '@/shared/ui/input';

import { authApi } from '../api/adminApi';
import { ApiErrorAlert } from '../components/ApiErrorAlert';
import { totpCode, useValidationMessage } from '../lib/forms';
import { useAdminAuth } from './AdminAuthProvider';

type WithStepUp = <T>(action: () => Promise<T>) => Promise<T>;

const StepUpContext = createContext<WithStepUp | null>(null);

/**
 * Həssas əməliyyatı icra edir; backend {@code STEP_UP_REQUIRED} qaytararsa, TOTP kodu soruşulur və əməliyyat
 * təsdiqdən sonra avtomatik təkrarlanır. İstifadəçi pəncərəni bağlayarsa, ilkin xəta qaytarılır.
 */
export function useStepUp(): WithStepUp {
  const value = useContext(StepUpContext);
  if (!value) {
    throw new Error('useStepUp must be used inside StepUpProvider');
  }
  return value;
}

const schema = z.object({ code: totpCode });

interface Pending {
  resolve: () => void;
  reject: () => void;
}

export function StepUpProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation(['auth', 'common']);
  const validation = useValidationMessage();
  const { realm } = useAdminAuth();
  const [open, setOpen] = useState(false);
  const pending = useRef<Pending | null>(null);
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { code: '' } });
  const stepUp = useMutation({ mutationFn: (code: string) => authApi.stepUp(realm, code) });

  const requestStepUp = useCallback(
    () =>
      new Promise<void>((resolve, reject) => {
        pending.current = { resolve, reject };
        form.reset({ code: '' });
        stepUp.reset();
        setOpen(true);
      }),
    [form, stepUp],
  );

  const withStepUp = useCallback<WithStepUp>(
    async (action) => {
      try {
        return await action();
      } catch (error) {
        if (!isApiError(error, 'STEP_UP_REQUIRED')) {
          throw error;
        }
        await requestStepUp().catch(() => {
          throw error;
        });
        return action();
      }
    },
    [requestStepUp],
  );

  const close = () => {
    setOpen(false);
    pending.current?.reject();
    pending.current = null;
  };

  const submit = form.handleSubmit(async ({ code }) => {
    await stepUp.mutateAsync(code).then(
      () => {
        setOpen(false);
        pending.current?.resolve();
        pending.current = null;
      },
      () => undefined,
    );
  });

  return (
    <StepUpContext.Provider value={withStepUp}>
      {children}
      <Dialog open={open} onOpenChange={(next) => (next ? setOpen(true) : close())}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('auth:stepUp.title')}</DialogTitle>
            <DialogDescription>{t('auth:stepUp.description')}</DialogDescription>
          </DialogHeader>
          <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
            <ApiErrorAlert error={stepUp.error} />
            <Field id="step-up-code" label={t('auth:mfa.code')} error={validation(form.formState.errors.code?.message)}>
              <Input
                {...form.register('code')}
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                autoFocus
              />
            </Field>
            <DialogFooter>
              <Button variant="outline" onClick={close}>
                {t('common:actions.cancel')}
              </Button>
              <Button type="submit" disabled={stepUp.isPending}>
                {t('auth:stepUp.confirm')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </StepUpContext.Provider>
  );
}
