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
import { PasswordInput } from '../components/PasswordInput';
import { requiredPassword, totpCode, useValidationMessage } from '../lib/forms';
import { useAdminAuth } from './AdminAuthProvider';

type WithStepUp = <T>(action: () => Promise<T>) => Promise<T>;

const StepUpContext = createContext<WithStepUp | null>(null);

/**
 * Həssas əməliyyatı icra edir; backend {@code STEP_UP_REQUIRED} qaytararsa, kimlik yenidən təsdiqlənir (2FA aktivdirsə
 * TOTP kodu, deyilsə cari şifrə) və əməliyyat təsdiqdən sonra avtomatik təkrarlanır. İstifadəçi pəncərəni bağlayarsa, ilkin xəta qaytarılır.
 */
export function useStepUp(): WithStepUp {
  const value = useContext(StepUpContext);
  if (!value) {
    throw new Error('useStepUp must be used inside StepUpProvider');
  }
  return value;
}

const codeSchema = z.object({ secret: totpCode });
const passwordSchema = z.object({ secret: requiredPassword });

interface Pending {
  resolve: () => void;
  reject: () => void;
}

export function StepUpProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation(['auth', 'common']);
  const validation = useValidationMessage();
  const { realm, me } = useAdminAuth();
  const useCode = Boolean(me?.totpEnabled);
  const [open, setOpen] = useState(false);
  const pending = useRef<Pending | null>(null);
  const form = useForm<{ secret: string }>({
    resolver: zodResolver(useCode ? codeSchema : passwordSchema),
    defaultValues: { secret: '' },
  });
  const stepUp = useMutation({ mutationFn: (secret: string) => authApi.stepUp(realm, useCode ? { code: secret } : { password: secret }),
  });

  const requestStepUp = useCallback(
    () =>
      new Promise<void>((resolve, reject) => {
        pending.current = { resolve, reject };
        form.reset({ secret: '' });
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

  const submit = form.handleSubmit(async ({ secret }) => {
    await stepUp.mutateAsync(secret).then(
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
            <DialogDescription>{useCode ? t('auth:stepUp.description') : t('auth:stepUp.passwordDescription')}</DialogDescription>
          </DialogHeader>
          <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
            <ApiErrorAlert error={stepUp.error} />
            {useCode ? (
              <Field id="step-up-code" label={t('auth:mfa.code')} error={validation(form.formState.errors.secret?.message)}>
                <Input
                  {...form.register('secret')}
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  autoFocus
                />
              </Field>
            ) : (
              <Field
                id="step-up-password"
                label={t('auth:stepUp.password')}
                error={validation(form.formState.errors.secret?.message)}
              >
                <PasswordInput {...form.register('secret')} autoComplete="current-password" autoFocus />
              </Field>
            )}
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
