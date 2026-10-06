import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { z } from 'zod';

import { toSupportedLanguage } from '@/shared/i18n/languages';
import { changeLanguage } from '@/shared/i18n/setup';
import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input } from '@/shared/ui/input';

import { authApi, type LoginStatus } from '../../api/adminApi';
import { useAdminAuth } from '../../auth/AdminAuthProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { PasswordInput } from '../../components/PasswordInput';
import { RoleLabel } from '../../components/RoleLabel';
import { takeFragmentToken } from '../../lib/fragmentToken';
import { newPassword, useValidationMessage } from '../../lib/forms';

const schema = z
  .object({
    displayName: z.string().trim().min(1, 'required').max(80, 'tooLong'),
    password: newPassword,
    confirm: z.string().min(1, 'required'),
  })
  .refine((values) => values.password === values.confirm, { path: ['confirm'], message: 'passwordMismatch' });
type Values = z.infer<typeof schema>;

const NEXT_STEP: Record<LoginStatus, string> = {
  AUTHENTICATED: '/',
  MFA_REQUIRED: '/mfa',
  MFA_ENROLLMENT_REQUIRED: '/mfa/setup',
};

/** Dəvətin qəbulu: hesab yaradılır, istifadəçi dərhal daxil olur (sahib üçün növbəti addım TOTP-dir). */
export function InvitationPage() {
  const { t } = useTranslation('auth');
  const validation = useValidationMessage();
  const { refresh } = useAdminAuth();
  const navigate = useNavigate();
  const [token] = useState(() => takeFragmentToken());
  const invitation = useQuery({
    queryKey: ['invitation', token],
    queryFn: () => authApi.inspectInvitation(token ?? ''),
    enabled: Boolean(token),
    retry: false,
    staleTime: Infinity,
  });
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { displayName: '', password: '', confirm: '' },
  });

  // İnterfeys dəvət edənin seçdiyi dildə açılır (istifadəçi sonra dəyişə bilər)
  const invitationLanguage = toSupportedLanguage(invitation.data?.language);
  useEffect(() => {
    if (invitationLanguage) {
      void changeLanguage(invitationLanguage);
    }
  }, [invitationLanguage]);

  const accept = useMutation({
    mutationFn: (values: Values) =>
      authApi.acceptInvitation({ token: token ?? '', displayName: values.displayName, password: values.password }),
    onSuccess: async (result) => {
      await refresh();
      void navigate(NEXT_STEP[result.status ?? 'AUTHENTICATED'], { replace: true });
    },
  });

  if (!token || invitation.isError) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>{t('invitation.title')}</CardTitle>
        </CardHeader>
        <CardContent>
          <Alert tone="error">{t('invitation.invalid')}</Alert>
        </CardContent>
      </Card>
    );
  }

  const details = invitation.data;
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('invitation.title')}</CardTitle>
        {details ? <CardDescription>{t('invitation.subtitle', { company: details.companyName })}</CardDescription> : null}
      </CardHeader>
      <CardContent>
        {!details ? (
          <InlineLoading />
        ) : (
          <form onSubmit={form.handleSubmit((values) => accept.mutate(values))} className="flex flex-col gap-4" noValidate>
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 rounded-xl bg-muted px-4 py-3 text-sm">
              <dt className="text-muted-foreground">{t('admin.email')}</dt>
              <dd className="min-w-0 break-all font-medium">{details.email}</dd>
              <dt className="text-muted-foreground">{t('invitation.role')}</dt>
              <dd className="font-medium">{details.role ? <RoleLabel role={details.role} /> : null}</dd>
              {details.branchName ? (
                <>
                  <dt className="text-muted-foreground">{t('invitation.branch')}</dt>
                  <dd className="font-medium">{details.branchName}</dd>
                </>
              ) : null}
            </dl>
            <ApiErrorAlert error={accept.error} />
            <Field id="display-name" label={t('invitation.displayName')} error={validation(form.formState.errors.displayName?.message)}>
              <Input {...form.register('displayName')} autoComplete="name" autoFocus />
            </Field>
            {/* Brauzerin şifrə menecerinə hansı hesab üçün saxlandığını bildirir */}
            <input type="hidden" name="username" autoComplete="username" value={details.email ?? ''} readOnly />
            <Field
              id="new-password"
              label={t('admin.password')}
              hint={t('passwordPolicy.hint')}
              error={validation(form.formState.errors.password?.message)}
            >
              <PasswordInput {...form.register('password')} autoComplete="new-password" />
            </Field>
            <Field id="confirm-password" label={t('reset.confirmPassword')} error={validation(form.formState.errors.confirm?.message)}>
              <PasswordInput {...form.register('confirm')} autoComplete="new-password" />
            </Field>
            <Button type="submit" size="lg" disabled={accept.isPending}>
              {t('invitation.submit')}
            </Button>
          </form>
        )}
      </CardContent>
    </Card>
  );
}
