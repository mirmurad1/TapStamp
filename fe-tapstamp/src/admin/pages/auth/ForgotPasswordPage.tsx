import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { z } from 'zod';

import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input } from '@/shared/ui/input';

import { authApi } from '../../api/adminApi';
import { useAdminAuth } from '../../auth/AdminAuthProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { emailField, useValidationMessage } from '../../lib/forms';

const schema = z.object({ email: emailField });

/** Cavab həmişə eynidir — hesabın mövcud olub-olmadığı açıqlanmır. */
export function ForgotPasswordPage() {
  const { t } = useTranslation('auth');
  const validation = useValidationMessage();
  const { realm } = useAdminAuth();
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { email: '' } });
  const forgot = useMutation({ mutationFn: (email: string) => authApi.forgotPassword(realm, email) });

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('forgot.title')}</CardTitle>
        <CardDescription>{t('forgot.subtitle')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {forgot.isSuccess ? (
          <Alert tone="success">{t('forgot.sent')}</Alert>
        ) : (
          <form onSubmit={form.handleSubmit(({ email }) => forgot.mutate(email))} className="flex flex-col gap-4" noValidate>
            <ApiErrorAlert error={forgot.error} />
            <Field id="email" label={t('admin.email')} error={validation(form.formState.errors.email?.message)}>
              <Input {...form.register('email')} type="email" autoComplete="username" inputMode="email" autoFocus />
            </Field>
            <Button type="submit" size="lg" disabled={forgot.isPending}>
              {t('forgot.submit')}
            </Button>
          </form>
        )}
        <Button asChild variant="ghost">
          <Link to="/login">
            <ArrowLeft aria-hidden="true" />
            {t('forgot.back')}
          </Link>
        </Button>
      </CardContent>
    </Card>
  );
}
