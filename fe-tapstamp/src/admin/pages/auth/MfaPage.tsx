import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { z } from 'zod';

import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input } from '@/shared/ui/input';

import { authApi } from '../../api/adminApi';
import { useAdminAuth } from '../../auth/AdminAuthProvider';
import { clearUserData } from '../../auth/session';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { recoveryCode, totpCode, useValidationMessage } from '../../lib/forms';

const totpSchema = z.object({ code: totpCode });
const recoverySchema = z.object({ code: recoveryCode });
type Values = { code: string };

/** Şifrədən sonrakı addım: TOTP kodu və ya (telefon itəndə) birdəfəlik bərpa kodu. */
export function MfaPage() {
  const { t } = useTranslation('auth');
  const validation = useValidationMessage();
  const { realm, refresh } = useAdminAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [useRecovery, setUseRecovery] = useState(false);
  const form = useForm<Values>({
    resolver: zodResolver(useRecovery ? recoverySchema : totpSchema),
    defaultValues: { code: '' },
  });

  const verify = useMutation({
    mutationFn: ({ code }: Values) => authApi.verifyMfa(realm, code),
    onSuccess: async () => {
      await refresh();
      void navigate('/', { replace: true });
    },
    onError: () => form.reset({ code: '' }),
  });

  const switchMode = () => {
    setUseRecovery((value) => !value);
    form.reset({ code: '' });
    verify.reset();
  };

  const otherAccount = async () => {
    await authApi.logout(realm).catch(() => undefined);
    clearUserData(queryClient);
    void navigate('/login', { replace: true });
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('mfa.title')}</CardTitle>
        <CardDescription>{useRecovery ? t('mfa.recoverySubtitle') : t('mfa.subtitle')}</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={form.handleSubmit((values) => verify.mutate(values))} className="flex flex-col gap-4" noValidate>
          <ApiErrorAlert error={verify.error} />
          <Field
            id="mfa-code"
            label={useRecovery ? t('mfa.recoveryCode') : t('mfa.code')}
            error={validation(form.formState.errors.code?.message)}
          >
            {useRecovery ? (
              <Input {...form.register('code')} autoComplete="off" autoCapitalize="none" spellCheck={false} autoFocus />
            ) : (
              <Input
                {...form.register('code')}
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                className="text-center font-mono text-xl tracking-[0.4em]"
                autoFocus
              />
            )}
          </Field>
          <Button type="submit" size="lg" disabled={verify.isPending}>
            {t('mfa.verify')}
          </Button>
          <Button variant="ghost" onClick={switchMode}>
            {useRecovery ? t('mfa.useTotp') : t('mfa.useRecovery')}
          </Button>
          <Button variant="ghost" size="sm" onClick={() => void otherAccount()}>
            {t('mfa.otherAccount')}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
