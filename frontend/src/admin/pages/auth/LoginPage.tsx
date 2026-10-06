import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate } from 'react-router';
import { z } from 'zod';

import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input } from '@/shared/ui/input';

import { authApi, type LoginStatus } from '../../api/adminApi';
import { useAdminAuth } from '../../auth/AdminAuthProvider';
import { consumeExpiredNotice } from '../../auth/session';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { PasswordInput } from '../../components/PasswordInput';
import { emailField, requiredPassword, useValidationMessage } from '../../lib/forms';

const schema = z.object({ email: emailField, password: requiredPassword });
type Values = z.infer<typeof schema>;

const NEXT_STEP: Record<LoginStatus, string> = {
  AUTHENTICATED: '/',
  MFA_REQUIRED: '/mfa',
  MFA_ENROLLMENT_REQUIRED: '/mfa/setup',
};

interface LocationState {
  signedOut?: boolean;
  passwordReset?: boolean;
}

export function LoginPage() {
  const { t } = useTranslation('auth');
  const validation = useValidationMessage();
  const { realm, refresh } = useAdminAuth();
  const navigate = useNavigate();
  const state = (useLocation().state ?? {}) as LocationState;
  const [expired] = useState(consumeExpiredNotice);
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } });

  const login = useMutation({
    mutationFn: (values: Values) => authApi.login(realm, values),
    onSuccess: async (result) => {
      await refresh();
      void navigate(NEXT_STEP[result.status ?? 'AUTHENTICATED'], { replace: true });
    },
    onError: () => form.resetField('password'),
  });

  const notice = expired
    ? t('admin.sessionExpired')
    : state.passwordReset
      ? t('admin.passwordResetDone')
      : state.signedOut
        ? t('admin.signedOut')
        : null;

  return (
    <Card>
      <CardHeader>
        <CardTitle>{realm === 'PLATFORM' ? t('admin.platformLoginTitle') : t('admin.loginTitle')}</CardTitle>
        <CardDescription>{t('admin.loginSubtitle')}</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={form.handleSubmit((values) => login.mutate(values))} className="flex flex-col gap-4" noValidate>
          {notice && !login.error ? <Alert tone={expired ? 'info' : 'success'}>{notice}</Alert> : null}
          <ApiErrorAlert error={login.error} />
          <Field id="email" label={t('admin.email')} error={validation(form.formState.errors.email?.message)}>
            <Input {...form.register('email')} type="email" autoComplete="username" inputMode="email" autoFocus />
          </Field>
          <Field id="password" label={t('admin.password')} error={validation(form.formState.errors.password?.message)}>
            <PasswordInput {...form.register('password')} autoComplete="current-password" />
          </Field>
          <Button type="submit" size="lg" disabled={login.isPending}>
            {t('signIn')}
          </Button>
          <Link
            to="/forgot-password"
            className="self-center rounded text-sm font-medium text-muted-foreground underline-offset-4 hover:text-foreground hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            {t('admin.forgotLink')}
          </Link>
        </form>
      </CardContent>
    </Card>
  );
}
