import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router';
import { z } from 'zod';

import { isApiError } from '@/shared/api/client';
import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';

import { authApi } from '../../api/adminApi';
import { useAdminAuth } from '../../auth/AdminAuthProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { PasswordInput } from '../../components/PasswordInput';
import { takeFragmentToken } from '../../lib/fragmentToken';
import { newPassword, useValidationMessage } from '../../lib/forms';

const schema = z
  .object({ password: newPassword, confirm: z.string().min(1, 'required') })
  .refine((values) => values.password === values.confirm, { path: ['confirm'], message: 'passwordMismatch' });
type Values = z.infer<typeof schema>;

export function ResetPasswordPage() {
  const { t } = useTranslation('auth');
  const validation = useValidationMessage();
  const { realm } = useAdminAuth();
  const navigate = useNavigate();
  const [token] = useState(() => takeFragmentToken());
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { password: '', confirm: '' } });
  const reset = useMutation({
    mutationFn: (values: Values) => authApi.resetPassword(realm, { token: token ?? '', newPassword: values.password }),
    onSuccess: () => void navigate('/login', { replace: true, state: { passwordReset: true } }),
  });

  const tokenRejected = isApiError(reset.error, 'TOKEN_INVALID');
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('reset.title')}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {!token || tokenRejected ? (
          <>
            {tokenRejected ? <ApiErrorAlert error={reset.error} /> : <Alert tone="error">{t('reset.missingToken')}</Alert>}
            <Button asChild>
              <Link to="/forgot-password">{t('reset.requestNew')}</Link>
            </Button>
          </>
        ) : (
          <form onSubmit={form.handleSubmit((values) => reset.mutate(values))} className="flex flex-col gap-4" noValidate>
            <ApiErrorAlert error={reset.error} />
            <Field
              id="new-password"
              label={t('reset.newPassword')}
              hint={t('passwordPolicy.hint')}
              error={validation(form.formState.errors.password?.message)}
            >
              <PasswordInput {...form.register('password')} autoComplete="new-password" autoFocus />
            </Field>
            <Field id="confirm-password" label={t('reset.confirmPassword')} error={validation(form.formState.errors.confirm?.message)}>
              <PasswordInput {...form.register('confirm')} autoComplete="new-password" />
            </Field>
            <Button type="submit" size="lg" disabled={reset.isPending}>
              {t('reset.submit')}
            </Button>
          </form>
        )}
      </CardContent>
    </Card>
  );
}
