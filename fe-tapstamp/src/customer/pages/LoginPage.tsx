import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { Trans, useTranslation } from 'react-i18next';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router';
import { z } from 'zod';

import { isApiError } from '@/shared/api/client';
import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input } from '@/shared/ui/input';

import { customerAuthApi, publicApi, referralApi } from '../api/customerApi';
import { useCustomerSession } from '../auth/CustomerSessionProvider';
import { markJustSignedIn } from '../auth/justSignedIn';
import { ErrorMessage } from '../components/ErrorMessage';
import { clearReferralCode, readReferralCode } from '../lib/referral';

type Step =
  | { name: 'phone' }
  | { name: 'code'; phone: string; challengeId: string; resendAt: number }
  | { name: 'register'; registrationToken: string };

/** Giriş: telefon → SMS kodu → (yeni nömrədirsə) qeydiyyatın tamamlanması. Dil dəyişəndə daxil edilmiş məlumat qalır. */
export function LoginPage() {
  const { status, refresh, clear } = useCustomerSession();
  const navigate = useNavigate();
  const switchingNumber = Boolean((useLocation().state as { switchNumber?: boolean } | null)?.switchNumber);
  const tapToken = useTapToken();
  // NFC toxunuşundan gəlibsə — girişdən sonra möhürü almaq üçün toxunuş səhifəsinə qayıdır
  const afterLogin = tapToken ? `/tap/${encodeURIComponent(tapToken)}` : '/';
  const [step, setStep] = useState<Step>({ name: 'phone' });

  // 'Fərqli nömrə ilə yoxla': serverdə çıxış artıq olub, sessiya keşi isə burada — bu səhifə ekrana gələndən sonra
  // təmizlənir. Parametrlərdə təmizlənsəydi, naviqasiya (keçid) bitməmiş Parametrlər səhifəsi istifadəçini "qonaq"
  // görüb ana səhifəyə yönləndirə bilərdi (yavaş cihazda giriş forması əvəzinə ana səhifə açılırdı).
  const clearAfterSwitch = switchingNumber && status === 'signedIn';
  useEffect(() => {
    if (clearAfterSwitch) {
      clear();
    }
  }, [clearAfterSwitch, clear]);

  if (status === 'signedIn') {
    // Nömrə dəyişdirilir: keş təmizlənənə qədər bir an daxil olmuş görünür — geri yönləndirilmir
    return switchingNumber ? null : <Navigate to={afterLogin} replace />;
  }

  const finish = async () => {
    markJustSignedIn();
    await refresh();
    void navigate(afterLogin, { replace: true });
  };

  const stepIndex = step.name === 'phone' ? 1 : step.name === 'code' ? 2 : 3;
  return (
    <div className="flex flex-col gap-4 pt-2">
      <StepBar current={stepIndex} total={3} />
      {step.name === 'phone' ? (
        <PhoneStep
          onSent={(phone, challengeId, resendAt) => setStep({ name: 'code', phone, challengeId, resendAt })}
        />
      ) : null}
      {step.name === 'code' ? (
        <CodeStep
          key={step.challengeId}
          phone={step.phone}
          challengeId={step.challengeId}
          resendAt={step.resendAt}
          onResent={(challengeId, resendAt) => setStep({ ...step, challengeId, resendAt })}
          onChangePhone={() => setStep({ name: 'phone' })}
          onAuthenticated={() => {
            // Mövcud müştəri daxil oldu — saxlanmış dəvət kodu ona aid deyil; bu cihazda sonra qeydiyyatdan keçən
            // başqa müştəri səhvən "dəvət olunmuş" sayılmasın
            clearReferralCode();
            void finish();
          }}
          onRegistrationRequired={(registrationToken) => setStep({ name: 'register', registrationToken })}
        />
      ) : null}
      {step.name === 'register' ? (
        <RegisterStep registrationToken={step.registrationToken} onRegistered={() => void finish()} />
      ) : null}
    </div>
  );
}

/** Girişin addımları (nömrə, SMS kodu, qeydiyyat): keçilmiş addımlar brend rəngində. */
function StepBar({ current, total }: { current: number; total: number }) {
  const { t } = useTranslation('auth');
  return (
    <div className="flex gap-1.5" role="img" aria-label={t('login.step', { current, total })}>
      {Array.from({ length: total }, (_, index) => (
        <span
          key={index}
          className={cn('h-1 flex-1 rounded-full transition-colors duration-500', index < current ? 'bg-primary' : 'bg-border')}
        />
      ))}
    </div>
  );
}

const phoneSchema = z.object({ phone: z.string().trim().min(1, 'required').max(32, 'tooLong') });

function PhoneStep({ onSent }: { onSent: (phone: string, challengeId: string, resendAt: number) => void }) {
  const { t } = useTranslation(['auth', 'common']);
  const tapToken = useTapToken();
  const form = useForm<z.infer<typeof phoneSchema>>({ resolver: zodResolver(phoneSchema), defaultValues: { phone: '' } });
  const request = useMutation({
    mutationFn: (phone: string) => customerAuthApi.requestOtp(phone, currentLanguage(), tapToken),
    onSuccess: (challenge, phone) =>
      onSent(phone, challenge.challengeId ?? '', Date.parse(challenge.resendAvailableAt ?? '') || Date.now() + 60_000),
  });
  const error = form.formState.errors.phone?.message;

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('auth:login.title')}</CardTitle>
        <CardDescription>{t('auth:login.subtitle')}</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={form.handleSubmit(({ phone }) => request.mutate(phone))} className="flex flex-col gap-4" noValidate>
          <ErrorMessage error={request.error} />
          <Field
            id="phone"
            label={t('auth:phone.label')}
            hint={t('auth:phone.hint')}
            error={error ? t(`common:validation.${error === 'tooLong' ? 'tooLong' : 'required'}`) : undefined}
          >
            <Input
              {...form.register('phone')}
              type="tel"
              inputMode="tel"
              autoComplete="tel-national"
              placeholder={t('auth:login.phonePlaceholder')}
              className="text-lg"
              autoFocus
            />
          </Field>
          <Button type="submit" size="lg" disabled={request.isPending}>
            {t('auth:login.sendCode')}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

const codeSchema = z.object({ code: z.string().trim().regex(/^\d{6}$/, 'code') });

interface CodeStepProps {
  phone: string;
  challengeId: string;
  resendAt: number;
  onResent: (challengeId: string, resendAt: number) => void;
  onChangePhone: () => void;
  onAuthenticated: () => void;
  onRegistrationRequired: (registrationToken: string) => void;
}

function useCountdown(until: number): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  return Math.max(0, Math.min(120, Math.ceil((until - now) / 1000)));
}

function CodeStep({ phone, challengeId, resendAt, onResent, onChangePhone, onAuthenticated, onRegistrationRequired }: CodeStepProps) {
  const { t } = useTranslation(['auth', 'common']);
  const tapToken = useTapToken();
  const [resendUntil, setResendUntil] = useState(resendAt);
  const secondsLeft = useCountdown(resendUntil);
  const form = useForm<z.infer<typeof codeSchema>>({ resolver: zodResolver(codeSchema), defaultValues: { code: '' } });
  const verify = useMutation({
    mutationFn: (code: string) => customerAuthApi.verifyOtp(challengeId, code),
    onSuccess: (result) => {
      if (result.status === 'REGISTRATION_REQUIRED' && result.registrationToken) {
        onRegistrationRequired(result.registrationToken);
      } else {
        onAuthenticated();
      }
    },
    onError: () => form.reset({ code: '' }),
  });
  const resend = useMutation({
    mutationFn: () => customerAuthApi.requestOtp(phone, currentLanguage(), tapToken),
    onSuccess: (challenge) =>
      onResent(challenge.challengeId ?? challengeId, Date.parse(challenge.resendAvailableAt ?? '') || Date.now() + 60_000),
    onError: (error) => {
      if (isApiError(error, 'RATE_LIMITED') && error.problem.retryAfterSeconds) {
        setResendUntil(Date.now() + error.problem.retryAfterSeconds * 1000);
      }
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('auth:login.codeTitle')}</CardTitle>
        <CardDescription>{t('auth:login.codeSent', { phone })}</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={form.handleSubmit(({ code }) => verify.mutate(code))} className="flex flex-col gap-4" noValidate>
          <ErrorMessage error={verify.error ?? resend.error} />
          <Field
            id="otp"
            label={t('auth:otp.label')}
            error={form.formState.errors.code ? t('common:validation.code') : undefined}
          >
            <Input
              {...form.register('code')}
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              className="text-center font-mono text-2xl tracking-[0.5em]"
              autoFocus
            />
          </Field>
          <Button type="submit" size="lg" disabled={verify.isPending}>
            {t('auth:login.verify')}
          </Button>
          <Button variant="ghost" disabled={secondsLeft > 0 || resend.isPending} onClick={() => resend.mutate()}>
            {secondsLeft > 0 ? t('auth:otp.resendIn', { count: secondsLeft }) : t('auth:otp.resend')}
          </Button>
          <Button variant="ghost" size="sm" onClick={onChangePhone}>
            <ArrowLeft aria-hidden="true" />
            {t('auth:login.changePhone')}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

const registerSchema = z.object({
  name: z.string().trim().max(80, 'tooLong'),
  consent: z.boolean().refine((accepted) => accepted, 'consentRequired'),
});
type RegisterValues = z.infer<typeof registerSchema>;

function RegisterStep({ registrationToken, onRegistered }: { registrationToken: string; onRegistered: () => void }) {
  const { t } = useTranslation(['auth', 'common']);
  const documents = useQuery({ queryKey: ['legal-documents'], queryFn: publicApi.legalDocuments });
  // Dəvət linki ilə gəlibsə — kod qeydiyyatla göndərilir
  const [referralCode] = useState(readReferralCode);
  const program = useQuery({ queryKey: ['referral-program'], queryFn: referralApi.program, enabled: Boolean(referralCode) });
  const form = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { name: '', consent: false },
  });
  const register = useMutation({
    mutationFn: (values: RegisterValues) =>
      customerAuthApi.register({
        registrationToken,
        name: values.name || undefined,
        acceptedDocuments: (documents.data ?? []).map((document) => ({
          type: document.type ?? 'TERMS',
          version: document.version ?? 1,
        })),
        language: currentLanguage(),
        referralCode,
      }),
    onSuccess: () => {
      clearReferralCode();
      onRegistered();
    },
  });
  const tokenExpired = isApiError(register.error, 'TOKEN_INVALID');

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('auth:register.title')}</CardTitle>
        <CardDescription>{t('auth:register.subtitle')}</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={form.handleSubmit((values) => register.mutate(values))} className="flex flex-col gap-4" noValidate>
          <ErrorMessage error={register.error} />
          {referralCode && program.data?.enabled ? (
            <p className="rounded-xl bg-highlight/15 px-4 py-3 text-sm font-medium">
              {t(program.data.rewardOn === 'REGISTRATION' ? 'auth:register.referralRegistration' : 'auth:register.referral', {
                count: program.data.friendStamps ?? 1,
              })}
            </p>
          ) : null}
          {tokenExpired ? (
            <Button asChild>
              <Link to="/login" reloadDocument>
                {t('auth:login.title')}
              </Link>
            </Button>
          ) : null}
          <Field
            id="name"
            label={t('auth:register.name')}
            error={form.formState.errors.name ? t('common:validation.tooLong') : undefined}
          >
            <Input {...form.register('name')} autoComplete="given-name" maxLength={80} autoFocus />
          </Field>
          <div className="flex flex-col gap-1.5">
            <label className="flex items-start gap-3 text-sm leading-relaxed">
              <input
                type="checkbox"
                {...form.register('consent')}
                aria-invalid={form.formState.errors.consent ? true : undefined}
                className="mt-1 size-5 shrink-0 accent-[var(--primary)]"
              />
              <span>
                <Trans
                  ns="auth"
                  i18nKey="register.consent"
                  components={{
                    privacy: <LegalLink slug="privacy" />,
                    terms: <LegalLink slug="terms" />,
                  }}
                />
              </span>
            </label>
            {form.formState.errors.consent ? (
              <p role="alert" className="text-xs font-medium text-destructive">
                {t('auth:register.consentRequired')}
              </p>
            ) : null}
          </div>
          <Button type="submit" size="lg" disabled={register.isPending || documents.isPending}>
            {t('auth:register.submit')}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

/** Hüquqi sənəd yeni tabda açılır — doldurulmuş forma itmir. */
function LegalLink({ slug, children }: { slug: string; children?: ReactNode }) {
  return (
    <a
      href={`/legal/${slug}`}
      target="_blank"
      rel="noopener noreferrer"
      className="font-medium text-foreground underline underline-offset-4"
    >
      {children}
    </a>
  );
}

/** NFC toxunuşunun tokeni ({@code /login?tap=…}): OTP sorğusu ilə göndərilir və 10 dəqiqəlik rezerv olunur. */
function useTapToken(): string | undefined {
  const [params] = useSearchParams();
  const token = params.get('tap');
  return token && /^[A-Za-z0-9_-]{20,128}$/.test(token) ? token : undefined;
}
