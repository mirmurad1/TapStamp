import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { Check, Copy } from 'lucide-react';
import QRCode from 'qrcode';
import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { z } from 'zod';

import { isApiError } from '@/shared/api/client';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input } from '@/shared/ui/input';

import { authApi } from '../../api/adminApi';
import { useAdminAuth } from '../../auth/AdminAuthProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { RecoveryCodesList } from '../../components/RecoveryCodesList';
import { totpCode, useValidationMessage } from '../../lib/forms';

const schema = z.object({ code: totpCode });

/** Base32 açarı oxunaqlı qruplara bölür: "MNQW MZJN …". */
function groupSecret(secret: string): string {
  return secret.replace(/(.{4})/g, '$1 ').trim();
}

/** TOTP qeydiyyatı: QR kod → kodla təsdiq → bərpa kodlarının bir dəfə göstərilməsi. */
export function MfaSetupPage() {
  const { t } = useTranslation(['auth', 'common']);
  const validation = useValidationMessage();
  const { realm, refresh } = useAdminAuth();
  const navigate = useNavigate();
  const [qr, setQr] = useState<string>();
  const [copied, setCopied] = useState(false);
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>();
  const started = useRef(false);
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { code: '' } });

  const start = useMutation({
    mutationFn: () => authApi.startEnrollment(realm),
    onSuccess: async (data) => {
      if (data.otpauthUri) {
        setQr(await QRCode.toDataURL(data.otpauthUri, { margin: 1, width: 224, errorCorrectionLevel: 'M' }));
      }
    },
    onError: (error) => {
      if (isApiError(error, 'MFA_ALREADY_ENABLED')) {
        void navigate('/', { replace: true });
      }
    },
  });
  const confirm = useMutation({
    mutationFn: (code: string) => authApi.confirmEnrollment(realm, code),
    onSuccess: (data) => setRecoveryCodes(data.recoveryCodes ?? []),
    onError: () => form.reset({ code: '' }),
  });

  useEffect(() => {
    // StrictMode effekti iki dəfə işlədir — hər dəfə yeni sirr yaranmasın
    if (!started.current) {
      started.current = true;
      start.mutate();
    }
  }, [start]);

  const copySecret = async () => {
    if (start.data?.secret) {
      await navigator.clipboard.writeText(start.data.secret);
      setCopied(true);
    }
  };

  const finish = async () => {
    await refresh();
    void navigate('/', { replace: true });
  };

  if (recoveryCodes) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>{t('auth:mfaSetup.recoveryTitle')}</CardTitle>
          <CardDescription>{t('auth:mfaSetup.recoverySubtitle')}</CardDescription>
        </CardHeader>
        <CardContent>
          <RecoveryCodesList codes={recoveryCodes} onDone={() => void finish()} doneLabel={t('auth:mfaSetup.continue')} />
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('auth:mfaSetup.title')}</CardTitle>
        <CardDescription>{t('auth:mfaSetup.subtitle')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        {start.isPending ? <InlineLoading /> : null}
        {start.isError && !isApiError(start.error, 'MFA_ALREADY_ENABLED') ? <ApiErrorAlert error={start.error} /> : null}
        {start.data ? (
          <>
            <section className="flex flex-col gap-3">
              <h3 className="text-sm font-semibold">{t('auth:mfaSetup.step1')}</h3>
              {qr ? (
                <img
                  src={qr}
                  alt={t('auth:mfaSetup.qrAlt')}
                  width={224}
                  height={224}
                  className="self-center rounded-xl border border-border bg-white p-2"
                />
              ) : null}
              <p className="text-xs text-muted-foreground">{t('auth:mfaSetup.manualEntry')}</p>
              <div className="flex items-center gap-2">
                <code className="min-w-0 flex-1 break-all rounded-lg bg-muted px-3 py-2 font-mono text-sm">
                  {groupSecret(start.data.secret ?? '')}
                </code>
                <Button variant="outline" size="icon" onClick={() => void copySecret()} aria-label={t('common:actions.copy')}>
                  {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
                </Button>
              </div>
            </section>
            <form
              onSubmit={form.handleSubmit(({ code }) => confirm.mutate(code))}
              className="flex flex-col gap-4"
              noValidate
            >
              <h3 className="text-sm font-semibold">{t('auth:mfaSetup.step2')}</h3>
              <ApiErrorAlert error={confirm.error} />
              <Field id="setup-code" label={t('auth:mfa.code')} error={validation(form.formState.errors.code?.message)}>
                <Input
                  {...form.register('code')}
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  className="text-center font-mono text-xl tracking-[0.4em]"
                />
              </Field>
              <Button type="submit" size="lg" disabled={confirm.isPending}>
                {t('auth:mfaSetup.confirm')}
              </Button>
            </form>
          </>
        ) : null}
      </CardContent>
    </Card>
  );
}

