import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Laptop, ShieldCheck, ShieldOff, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { z } from 'zod';

import { SUPPORTED_LANGUAGES, toSupportedLanguage, type Language } from '@/shared/i18n/languages';
import { changeLanguage, currentLanguage } from '@/shared/i18n/setup';
import { formatDateTime, formatRelative } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';

import { accountApi } from '../../api/adminApi';
import { useAdminAuth, useMe } from '../../auth/AdminAuthProvider';
import { sessionKeys } from '../../auth/session';
import { useStepUp } from '../../auth/StepUpProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { PasswordInput } from '../../components/PasswordInput';
import { RecoveryCodesList } from '../../components/RecoveryCodesList';
import { newPassword, requiredPassword, totpCode, useValidationMessage } from '../../lib/forms';

export function AccountPage() {
  const { t } = useTranslation('admin');
  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <h1 className="font-display text-2xl font-semibold">{t('account.title')}</h1>
      <ProfileCard />
      <SecurityCard />
      <PasswordCard />
      <SessionsCard />
    </div>
  );
}

const profileSchema = z.object({
  displayName: z.string().trim().min(1, 'required').max(80, 'tooLong'),
  preferredLanguage: z.enum(SUPPORTED_LANGUAGES),
});

function ProfileCard() {
  const { t } = useTranslation(['admin', 'common']);
  const validation = useValidationMessage();
  const { realm } = useAdminAuth();
  const me = useMe();
  const queryClient = useQueryClient();
  const form = useForm<z.infer<typeof profileSchema>>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      displayName: me.displayName ?? '',
      preferredLanguage: toSupportedLanguage(me.preferredLanguage) ?? currentLanguage(),
    },
  });
  const save = useMutation({
    mutationFn: (values: z.infer<typeof profileSchema>) => accountApi.updateMe(realm, values),
    onSuccess: async (updated) => {
      queryClient.setQueryData(sessionKeys.me, updated);
      const lang = toSupportedLanguage(updated.preferredLanguage);
      if (lang) {
        await changeLanguage(lang);
      }
      form.reset({ displayName: updated.displayName ?? '', preferredLanguage: lang ?? currentLanguage() });
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('admin:account.profile.title')}</CardTitle>
        <CardDescription>{me.email}</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={form.handleSubmit((values) => save.mutate(values))} className="flex flex-col gap-4" noValidate>
          {save.isSuccess && !form.formState.isDirty ? <Alert tone="success">{t('admin:account.profile.saved')}</Alert> : null}
          <ApiErrorAlert error={save.error} />
          <Field
            id="display-name"
            label={t('admin:account.profile.displayName')}
            error={validation(form.formState.errors.displayName?.message)}
          >
            <Input {...form.register('displayName')} autoComplete="name" />
          </Field>
          <Field id="language" label={t('admin:account.profile.language')} hint={t('admin:account.profile.languageHint')}>
            <Select {...form.register('preferredLanguage')}>
              {SUPPORTED_LANGUAGES.map((lang: Language) => (
                <option key={lang} value={lang} lang={lang}>
                  {t(`common:language.${lang}`)}
                </option>
              ))}
            </Select>
          </Field>
          <Button type="submit" className="self-start" disabled={save.isPending || !form.formState.isDirty}>
            {t('common:actions.save')}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function SecurityCard() {
  const { t } = useTranslation(['admin', 'common', 'auth']);
  const { realm } = useAdminAuth();
  const me = useMe();
  const withStepUp = useStepUp();
  const queryClient = useQueryClient();
  const [codes, setCodes] = useState<string[]>();
  const regenerate = useMutation({
    mutationFn: () => withStepUp(() => accountApi.regenerateRecoveryCodes(realm)),
    onSuccess: (data) => setCodes(data.recoveryCodes ?? []),
  });

  const [prompt, setPrompt] = useState<MfaAction>();
  const [notice, setNotice] = useState<string>();

  const done = () => {
    setCodes(undefined);
    void queryClient.invalidateQueries({ queryKey: sessionKeys.me });
  };

  const finished = (action: MfaAction) => {
    setPrompt(undefined);
    setNotice(t(`admin:account.security.done.${action}`));
    void queryClient.invalidateQueries({ queryKey: sessionKeys.me });
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle>{t('admin:account.security.title')}</CardTitle>
          {me.totpEnabled ? (
            <Badge tone="success">
              <ShieldCheck className="mr-1 size-3.5" aria-hidden="true" />
              {t('admin:account.security.enabled')}
            </Badge>
          ) : me.totpPaused ? (
            <Badge tone="warning">
              <ShieldOff className="mr-1 size-3.5" aria-hidden="true" />
              {t('admin:account.security.paused')}
            </Badge>
          ) : (
            <Badge tone="danger">
              <ShieldOff className="mr-1 size-3.5" aria-hidden="true" />
              {t('admin:account.security.disabled')}
            </Badge>
          )}
        </div>
        {me.totpEnabled ? (
          <CardDescription>
            {t('admin:account.security.recoveryRemaining', { count: me.recoveryCodesRemaining ?? 0 })}
          </CardDescription>
        ) : null}
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {codes ? (
          <RecoveryCodesList codes={codes} onDone={done} doneLabel={t('common:actions.close')} />
        ) : (
          <>
            {notice ? <Alert tone="success">{notice}</Alert> : null}
            {me.totpEnabled ? (
              <>
                <ApiErrorAlert error={regenerate.error} />
                <div className="flex flex-col gap-1">
                  <Button variant="outline" className="self-start" onClick={() => regenerate.mutate()} disabled={regenerate.isPending}>
                    {t('admin:account.security.regenerate')}
                  </Button>
                  <p className="text-xs text-muted-foreground">{t('admin:account.security.regenerateHint')}</p>
                </div>
              </>
            ) : me.totpPaused ? (
              <p className="text-sm text-muted-foreground">{t('admin:account.security.pausedHint')}</p>
            ) : (
              <Button asChild className="self-start">
                <Link to="/mfa/setup">{t('auth:mfaSetup.title')}</Link>
              </Button>
            )}
            {me.totpEnabled || me.totpPaused ? (
              <div className="flex flex-wrap gap-2 border-t border-border pt-4">
                {me.totpEnabled ? (
                  <Button variant="outline" onClick={() => setPrompt('pause')}>
                    <ShieldOff className="size-4" aria-hidden="true" />
                    {t('admin:account.security.pause')}
                  </Button>
                ) : (
                  <Button onClick={() => setPrompt('resume')}>
                    <ShieldCheck className="size-4" aria-hidden="true" />
                    {t('admin:account.security.resume')}
                  </Button>
                )}
                <Button
                  variant="ghost"
                  className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                  onClick={() => setPrompt('remove')}
                >
                  <Trash2 className="size-4" aria-hidden="true" />
                  {t('admin:account.security.remove')}
                </Button>
              </div>
            ) : null}
          </>
        )}
      </CardContent>
      {prompt ? <MfaActionDialog action={prompt} onClose={() => setPrompt(undefined)} onDone={finished} /> : null}
    </Card>
  );
}

type MfaAction = 'pause' | 'resume' | 'remove';

/**
 * 2FA-nın söndürülməsi/silinməsi cari şifrə ilə təsdiqlənir (2FA kodu tələb olunmur — telefon əldə olmasa da olar);
 * yenidən aktivləşdirmə autentifikator tətbiqinin kodu ilə.
 */
function MfaActionDialog({
  action,
  onClose,
  onDone,
}: {
  action: MfaAction;
  onClose: () => void;
  onDone: (action: MfaAction) => void;
}) {
  const { t } = useTranslation(['admin', 'common', 'auth']);
  const validation = useValidationMessage();
  const { realm } = useAdminAuth();
  const me = useMe();
  const withCode = action === 'resume';
  const form = useForm<{ secret: string }>({
    resolver: zodResolver(z.object({ secret: withCode ? totpCode : requiredPassword })),
    defaultValues: { secret: '' },
  });
  const run = useMutation({
    mutationFn: (secret: string) =>
      action === 'pause'
        ? accountApi.pauseMfa(realm, secret)
        : action === 'resume'
          ? accountApi.resumeMfa(realm, secret)
          : accountApi.removeMfa(realm, secret),
    onSuccess: () => onDone(action),
  });
  const danger = action === 'remove';

  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t(`admin:account.security.dialog.${action}.title`)}</DialogTitle>
          <DialogDescription>{t(`admin:account.security.dialog.${action}.description`)}</DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(({ secret }) => run.mutate(secret))} className="flex flex-col gap-4" noValidate>
          <ApiErrorAlert error={run.error} />
          {withCode ? (
            <Field id="mfa-action-code" label={t('auth:mfa.code')} error={validation(form.formState.errors.secret?.message)}>
              <Input {...form.register('secret')} inputMode="numeric" autoComplete="one-time-code" maxLength={6} autoFocus />
            </Field>
          ) : (
            <>
              <input type="hidden" name="username" autoComplete="username" value={me.email ?? ''} readOnly />
              <Field
                id="mfa-action-password"
                label={t('admin:account.password.current')}
                error={validation(form.formState.errors.secret?.message)}
              >
                <PasswordInput {...form.register('secret')} autoComplete="current-password" autoFocus />
              </Field>
            </>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={onClose}>
              {t('common:actions.cancel')}
            </Button>
            <Button
              type="submit"
              disabled={run.isPending}
              className={danger ? 'bg-destructive text-white hover:bg-destructive/90' : undefined}
            >
              {t(`admin:account.security.dialog.${action}.confirm`)}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

const passwordSchema = z
  .object({ currentPassword: requiredPassword, newPassword, confirm: z.string().min(1, 'required') })
  .refine((values) => values.newPassword === values.confirm, { path: ['confirm'], message: 'passwordMismatch' });
type PasswordValues = z.infer<typeof passwordSchema>;

function PasswordCard() {
  const { t } = useTranslation(['admin', 'auth']);
  const validation = useValidationMessage();
  const { realm } = useAdminAuth();
  const me = useMe();
  const queryClient = useQueryClient();
  const form = useForm<PasswordValues>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirm: '' },
  });
  const change = useMutation({
    mutationFn: (values: PasswordValues) =>
      accountApi.changePassword(realm, { currentPassword: values.currentPassword, newPassword: values.newPassword }),
    onSuccess: () => {
      form.reset();
      void queryClient.invalidateQueries({ queryKey: ['admin-sessions'] });
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('admin:account.password.title')}</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={form.handleSubmit((values) => change.mutate(values))} className="flex flex-col gap-4" noValidate>
          {change.isSuccess ? <Alert tone="success">{t('admin:account.password.changed')}</Alert> : null}
          <ApiErrorAlert error={change.error} />
          <input type="hidden" name="username" autoComplete="username" value={me.email ?? ''} readOnly />
          <Field
            id="current-password"
            label={t('admin:account.password.current')}
            error={validation(form.formState.errors.currentPassword?.message)}
          >
            <PasswordInput {...form.register('currentPassword')} autoComplete="current-password" />
          </Field>
          <Field
            id="new-password"
            label={t('admin:account.password.new')}
            hint={t('auth:passwordPolicy.hint')}
            error={validation(form.formState.errors.newPassword?.message)}
          >
            <PasswordInput {...form.register('newPassword')} autoComplete="new-password" />
          </Field>
          <Field id="confirm-password" label={t('admin:account.password.confirm')} error={validation(form.formState.errors.confirm?.message)}>
            <PasswordInput {...form.register('confirm')} autoComplete="new-password" />
          </Field>
          <Button type="submit" className="self-start" disabled={change.isPending}>
            {t('admin:account.password.submit')}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

/** Brauzerin adını User-Agent-dən qısa göstərmək üçün (dəqiq olması vacib deyil). */
function describeDevice(userAgent: string | undefined): string | undefined {
  if (!userAgent) {
    return undefined;
  }
  const browser = /Edg\//.test(userAgent)
    ? 'Edge'
    : /Firefox\//.test(userAgent)
      ? 'Firefox'
      : /Chrome\//.test(userAgent)
        ? 'Chrome'
        : /Safari\//.test(userAgent)
          ? 'Safari'
          : undefined;
  const os = /Windows/.test(userAgent)
    ? 'Windows'
    : /Android/.test(userAgent)
      ? 'Android'
      : /iPhone|iPad/.test(userAgent)
        ? 'iOS'
        : /Mac OS X/.test(userAgent)
          ? 'macOS'
          : /Linux/.test(userAgent)
            ? 'Linux'
            : undefined;
  return [browser, os].filter(Boolean).join(' · ') || undefined;
}

function SessionsCard() {
  const { t } = useTranslation('admin');
  const { realm } = useAdminAuth();
  const queryClient = useQueryClient();
  const lang = currentLanguage();
  const sessions = useQuery({ queryKey: ['admin-sessions'], queryFn: () => accountApi.sessions(realm) });
  const revoke = useMutation({
    mutationFn: (sessionId: string) => accountApi.revokeSession(realm, sessionId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['admin-sessions'] }),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('account.sessions.title')}</CardTitle>
        <CardDescription>{t('account.sessions.subtitle')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <ApiErrorAlert error={sessions.error ?? revoke.error} />
        {sessions.isPending ? <InlineLoading /> : null}
        <ul className="flex flex-col divide-y divide-border">
          {(sessions.data ?? []).map((session) => (
            <li key={session.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="flex min-w-0 items-start gap-3">
                <Laptop className="mt-0.5 size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
                <div className="min-w-0 text-sm">
                  <p className="font-medium">
                    {describeDevice(session.userAgent) ?? t('account.sessions.unknownDevice')}
                    {session.current ? (
                      <Badge tone="success" className="ms-2">
                        {t('account.sessions.current')}
                      </Badge>
                    ) : null}
                  </p>
                  <p className="text-muted-foreground">
                    {session.lastActivityAt
                      ? t('account.sessions.lastActive', { time: formatRelative(session.lastActivityAt, lang) })
                      : null}
                    {session.ip ? ` · ${session.ip}` : null}
                  </p>
                  {session.createdAt ? (
                    <p className="text-xs text-muted-foreground">
                      {t('account.sessions.started', { time: formatDateTime(session.createdAt, lang) })}
                    </p>
                  ) : null}
                </div>
              </div>
              {!session.current && session.id ? (
                <Button variant="outline" size="sm" onClick={() => revoke.mutate(session.id ?? '')} disabled={revoke.isPending}>
                  {t('account.sessions.revoke')}
                </Button>
              ) : null}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}
