import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FileText, Smartphone, Trash2 } from 'lucide-react';
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useNavigate } from 'react-router';

import { currentLanguage } from '@/shared/i18n/setup';
import { formatPhone, formatRelative } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Input } from '@/shared/ui/input';
import { LanguageSwitcher } from '@/shared/ui/LanguageSwitcher';

import { customerAuthApi, LEGAL_SLUGS, meApi } from '../api/customerApi';
import { useCustomerSession } from '../auth/CustomerSessionProvider';
import { customerKeys } from '../auth/queryClient';
import { ErrorMessage } from '../components/ErrorMessage';
import { SwitchNumber } from '../components/SwitchNumber';

/** Parametrlər: dil, cihazlar, hüquqi sənədlər və hesabın silinməsi (çıxış — möhür kartının altında). */
export function SettingsPage() {
  const { t } = useTranslation('profile');
  const { status, me, clear } = useCustomerSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  // Silinmə: naviqasiya transition kimi gedir, keşin təmizlənməsi isə təcili yeniləmədir — bu səhifə qısa
  // müddət "qonaq" kimi render oluna bilər. O zaman öz yönləndirməsi naviqasiyanın state-ini (bildirişi) əvəz etməsin.
  const leaving = useRef(false);
  const leave = async (state?: { accountDeleted: boolean }) => {
    leaving.current = true;
    await navigate('/', { replace: true, state });
    clear();
  };
  const saveLanguage = useMutation({
    mutationFn: (lang: string) => meApi.update({ preferredLanguage: lang }),
    onSuccess: (updated) => queryClient.setQueryData(customerKeys.me, updated),
  });

  if (status === 'guest') {
    return leaving.current ? null : <Navigate to="/" replace />;
  }
  if (!me) {
    return null;
  }

  return (
    <div className="flex flex-col gap-4 pt-2">
      <h1 className="font-display text-3xl font-semibold">{t('settings.title')}</h1>

      <Card>
        <CardHeader>
          <CardTitle>{t('settings.account')}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex items-center justify-between gap-3">
            <span className="text-sm text-muted-foreground">{t('settings.phone')}</span>
            <span className="font-medium" dir="ltr">
              {formatPhone(me.phone)}
            </span>
          </div>
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between gap-3">
              <span className="text-sm text-muted-foreground">{t('settings.language')}</span>
              <LanguageSwitcher onSelect={(lang) => saveLanguage.mutate(lang)} />
            </div>
            <p className="text-xs text-muted-foreground">{t('settings.languageHint')}</p>
          </div>
        </CardContent>
      </Card>

      <MarketingCard />

      <SwitchNumber />

      <DevicesCard />

      <Card>
        <CardHeader>
          <CardTitle>{t('settings.legal.title')}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {LEGAL_SLUGS.map((slug) => (
            <Link
              key={slug}
              to={`/legal/${slug}`}
              className="flex min-h-11 items-center gap-3 rounded-xl bg-muted px-4 font-medium hover:underline"
            >
              <FileText className="size-5 shrink-0 text-accent" aria-hidden="true" />
              {t(`legal.${slug}`)}
            </Link>
          ))}
        </CardContent>
      </Card>

      <DeleteAccount onDeleted={() => void leave({ accountDeleted: true })} />
    </div>
  );
}

/** Kampaniya SMS-ləri: müştəri istənilən an söndürə bilər (məxfilik siyasəti). */
function MarketingCard() {
  const { t } = useTranslation('profile');
  const queryClient = useQueryClient();
  const key = ['me', 'marketing'] as const;
  const marketing = useQuery({ queryKey: key, queryFn: meApi.marketing });
  const save = useMutation({
    mutationFn: (enabled: boolean) => meApi.setMarketing(enabled),
    onSuccess: (updated) => queryClient.setQueryData(key, updated),
  });
  const enabled = save.isPending ? Boolean(save.variables) : Boolean(marketing.data?.enabled);

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('settings.marketing.title')}</CardTitle>
        <CardDescription>{t('settings.marketing.description')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-2">
        <label className="flex min-h-11 cursor-pointer items-center justify-between gap-3">
          <span className="font-medium">{t('settings.marketing.label')}</span>
          <input
            type="checkbox"
            role="switch"
            className="size-5 accent-primary"
            checked={enabled}
            disabled={marketing.isPending || save.isPending}
            onChange={(event) => save.mutate(event.target.checked)}
          />
        </label>
        {save.error || marketing.error ? <ErrorMessage error={save.error ?? marketing.error} /> : null}
      </CardContent>
    </Card>
  );
}

function DevicesCard() {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const [notice, setNotice] = useState<string>();
  const devices = useQuery({ queryKey: customerKeys.devices, queryFn: meApi.devices });
  const revoke = useMutation({
    mutationFn: (sessionId: string) => meApi.revokeDevice(sessionId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: customerKeys.devices }),
  });
  const revokeOthers = useMutation({
    mutationFn: meApi.revokeOtherDevices,
    onSuccess: (result) => {
      setNotice(t('settings.devices.signedOutOthers', { count: result.revoked ?? 0 }));
      void queryClient.invalidateQueries({ queryKey: customerKeys.devices });
    },
  });
  const others = (devices.data ?? []).filter((device) => !device.current);

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('settings.devices.title')}</CardTitle>
        <CardDescription>{t('settings.devices.subtitle')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {notice ? <Alert tone="success">{notice}</Alert> : null}
        <ErrorMessage error={devices.error ?? revoke.error ?? revokeOthers.error} />
        <ul className="flex flex-col divide-y divide-border">
          {(devices.data ?? []).map((device) => (
            <li key={device.id} className="flex items-center justify-between gap-3 py-3">
              <span className="flex min-w-0 items-start gap-3">
                <Smartphone className="mt-0.5 size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
                <span className="min-w-0">
                  <span className="block font-medium">
                    {device.deviceLabel || t('settings.devices.unknown')}
                    {device.current ? (
                      <Badge tone="success" className="ms-2">
                        {t('settings.devices.current')}
                      </Badge>
                    ) : null}
                  </span>
                  {device.lastSeenAt ? (
                    <span className="block text-xs text-muted-foreground">
                      {t('settings.devices.lastActive', { time: formatRelative(device.lastSeenAt, lang) })}
                    </span>
                  ) : null}
                </span>
              </span>
              {!device.current ? (
                <Button
                  variant="outline"
                  size="sm"
                  disabled={revoke.isPending}
                  onClick={() => revoke.mutate(device.id ?? '')}
                >
                  {t('settings.devices.signOut')}
                </Button>
              ) : null}
            </li>
          ))}
        </ul>
        {others.length > 0 ? (
          <Button variant="outline" onClick={() => revokeOthers.mutate()} disabled={revokeOthers.isPending}>
            {t('settings.devices.signOutOthers')}
          </Button>
        ) : null}
      </CardContent>
    </Card>
  );
}

/** Hesabın silinməsi: SMS kodu ilə təkrar təsdiq → silinmə. */
function DeleteAccount({ onDeleted }: { onDeleted: () => void }) {
  const { t } = useTranslation(['profile', 'auth', 'common']);
  const [open, setOpen] = useState(false);
  const [challengeId, setChallengeId] = useState<string>();
  const [code, setCode] = useState('');
  const sendCode = useMutation({
    mutationFn: customerAuthApi.requestStepUp,
    onSuccess: (challenge) => setChallengeId(challenge.challengeId),
  });
  const confirm = useMutation({
    mutationFn: async () => {
      await customerAuthApi.verifyStepUp(challengeId ?? '', code.trim());
      await meApi.deleteAccount();
    },
    onSuccess: () => {
      setOpen(false);
      onDeleted();
    },
    onError: () => setCode(''),
  });

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) {
          setChallengeId(undefined);
          setCode('');
          sendCode.reset();
          confirm.reset();
        }
      }}
    >
      <DialogTrigger asChild>
        <Button variant="ghost" className="text-destructive">
          <Trash2 aria-hidden="true" />
          {t('profile:settings.delete.open')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('profile:settings.delete.title')}</DialogTitle>
          <DialogDescription>{t('profile:settings.delete.description')}</DialogDescription>
        </DialogHeader>
        <ErrorMessage error={sendCode.error ?? confirm.error} />
        {challengeId ? (
          <form
            className="flex flex-col gap-4"
            onSubmit={(event) => {
              event.preventDefault();
              confirm.mutate();
            }}
          >
            <Field id="delete-code" label={t('auth:otp.label')} hint={t('profile:settings.delete.codeHint')}>
              <Input
                value={code}
                onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
                inputMode="numeric"
                autoComplete="one-time-code"
                className="text-center font-mono text-xl tracking-[0.4em]"
                autoFocus
              />
            </Field>
            <DialogFooter>
              <Button variant="outline" onClick={() => setOpen(false)}>
                {t('common:actions.cancel')}
              </Button>
              <Button
                type="submit"
                className="bg-destructive text-white hover:bg-destructive/90"
                disabled={code.length !== 6 || confirm.isPending}
              >
                {t('profile:settings.delete.confirm')}
              </Button>
            </DialogFooter>
          </form>
        ) : (
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              {t('common:actions.cancel')}
            </Button>
            <Button onClick={() => sendCode.mutate()} disabled={sendCode.isPending}>
              {t('profile:settings.delete.sendCode')}
            </Button>
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  );
}
