import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Crown, Pause, Play, Store, UserCheck, Users, UsersRound } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useParams, useSearchParams } from 'react-router';
import { z } from 'zod';

import { SUPPORTED_LANGUAGES } from '@/shared/i18n/languages';
import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';
import { formatDateTime, formatNumber } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';
import { useConfirm } from '@/shared/ui/ConfirmProvider';
import { Tabs } from '@/shared/ui/tabs';

import { platformApi, type Tenant, type TenantOwner } from '../../api/adminApi';
import { useStepUp } from '../../auth/StepUpProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { Kpi } from '../../components/Kpi';
import { emailField, useValidationMessage } from '../../lib/forms';
import { DomainsCard } from './DomainsCard';
import { TenantSmsLimitCard } from './SmsLimitCards';
import { TenantAddonsCard } from './TenantAddonsCard';
import { TenantAuditCard } from './TenantAuditCard';
import { TenantLogo } from './TenantLogo';
import { TenantPlanCard } from './TenantPlanCard';
import { TenantTagsCard } from './TenantTagsCard';
import { TENANTS_KEY } from './keys';
import { TenantStatusBadge } from './TenantStatusBadge';

type TenantTab = 'overview' | 'tags' | 'sms' | 'domains' | 'audit';
const TABS: readonly TenantTab[] = ['overview', 'tags', 'sms', 'domains', 'audit'];
/** Şirkət aktiv deyilsə, onun bazasına qoşulmaq olmur — yalnız bu bölmələr açıqdır. */
const OFFLINE_TABS: readonly TenantTab[] = ['overview', 'domains'];

export function TenantDetailPage() {
  const { t } = useTranslation('admin');
  const { tenantId = '' } = useParams();
  const created = Boolean((useLocation().state as { created?: boolean } | null)?.created);
  const [notice, setNotice] = useState<string | undefined>(created ? t('platform.provision.created') : undefined);
  const tenant = useQuery({ queryKey: ['platform-tenant', tenantId], queryFn: () => platformApi.tenant(tenantId) });
  const active = tenant.data?.status === 'ACTIVE';
  // Seçilmiş bölmə ünvanda saxlanılır: səhifə yenilənəndə və ya link paylaşılanda eyni bölmə açılır
  const [params, setParams] = useSearchParams();
  const available = active ? TABS : OFFLINE_TABS;
  const requested = params.get('tab') as TenantTab | null;
  const tab: TenantTab = requested && available.includes(requested) ? requested : 'overview';
  const selectTab = (value: TenantTab) => setParams(value === 'overview' ? {} : { tab: value }, { replace: true });

  return (
    <div className="flex flex-col gap-6">
      <Link
        to="/"
        className="inline-flex items-center gap-1.5 self-start text-sm font-medium text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        {t('platform.detail.back')}
      </Link>
      <ApiErrorAlert error={tenant.error} />
      {tenant.isPending ? <InlineLoading /> : null}
      {tenant.data ? (
        <>
          <TenantHeader tenant={tenant.data} onNotice={setNotice} />
          {notice ? <Alert tone="success">{notice}</Alert> : null}
          <Tabs
            label={t('platform.detail.sections')}
            value={tab}
            onChange={selectTab}
            options={available.map((value) => ({ value, label: t(`platform.detail.tabs.${value}`) }))}
          />
          {tab === 'overview' ? (
            <>
              {active ? <StatsRow tenantId={tenantId} /> : <Alert tone="info">{t('platform.detail.unavailable')}</Alert>}
              <div className="grid items-start gap-6 xl:grid-cols-2">
                <TenantPlanCard tenantId={tenantId} />
                <div className="flex flex-col gap-6">
                  <TenantAddonsCard tenantId={tenantId} />
                  {active ? <OwnersCard tenantId={tenantId} onNotice={setNotice} /> : null}
                </div>
              </div>
            </>
          ) : null}
          {tab === 'tags' ? <TenantTagsCard tenantId={tenantId} /> : null}
          {tab === 'sms' ? <TenantSmsLimitCard tenantId={tenantId} /> : null}
          {tab === 'domains' ? <DomainsCard tenantId={tenantId} /> : null}
          {tab === 'audit' ? <TenantAuditCard tenantId={tenantId} /> : null}
        </>
      ) : null}
    </div>
  );
}

/** Səhifənin başlığı (digər səhifələr kimi): şirkətin adı, texniki adı, statusu, domenləri və əsas əməliyyat sağda. */
function TenantHeader({ tenant, onNotice }: { tenant: Tenant; onNotice: (message: string) => void }) {
  const { t } = useTranslation('admin');
  const queryClient = useQueryClient();
  const withStepUp = useStepUp();
  const lang = currentLanguage();
  const suspended = tenant.status === 'SUSPENDED';
  const confirm = useConfirm();
  const toggle = useMutation({
    mutationFn: () => withStepUp(() => platformApi.updateStatus(tenant.id ?? '', suspended ? 'ACTIVE' : 'SUSPENDED')),
    onSuccess: (updated) => {
      queryClient.setQueryData(['platform-tenant', tenant.id], updated);
      void queryClient.invalidateQueries({ queryKey: TENANTS_KEY });
      onNotice(updated.status === 'SUSPENDED' ? t('platform.status.SUSPENDED') : t('platform.status.ACTIVE'));
    },
  });

  const onToggle = () => {
    if (suspended) {
      toggle.mutate();
      return;
    }
    void confirm({ message: t('platform.detail.suspendConfirm'), danger: true }).then((ok) => ok && toggle.mutate());
  };
  const name = resolveText(tenant.displayName, lang);

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex min-w-0 items-center gap-4">
          <TenantLogo
            tenantId={tenant.id ?? ''}
            name={name}
            lang={lang}
            className="size-14 rounded-2xl font-display text-2xl ring-1 ring-border"
          />
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="font-display text-2xl font-semibold">{name}</h1>
              <TenantStatusBadge status={tenant.status} />
            </div>
            <p className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground">
              <span className="font-mono">{tenant.slug}</span>
              {(tenant.domains ?? []).map((domain) => (
                // Domen sətrə keçəndə ayırıcı nöqtə sətrin əvvəlində tək qalmasın deyə nöqtəsiz, nişan kimi göstərilir
                <span
                  key={domain}
                  className={cn(
                    'rounded-full border border-border px-2 py-0.5 text-xs',
                    domain === tenant.primaryDomain && 'border-primary/30 bg-primary/5 font-medium text-foreground',
                  )}
                >
                  {domain}
                </span>
              ))}
            </p>
          </div>
        </div>
        {tenant.status === 'ACTIVE' || suspended ? (
          <Button variant={suspended ? 'default' : 'outline'} onClick={onToggle} disabled={toggle.isPending}>
            {suspended ? <Play aria-hidden="true" /> : <Pause aria-hidden="true" />}
            {suspended ? t('platform.detail.activate') : t('platform.detail.suspend')}
          </Button>
        ) : null}
      </div>
      <ApiErrorAlert error={toggle.error} />
    </div>
  );
}

/** Şirkətin ümumi göstəriciləri — şirkətin öz göstəricilər panelindəki kartlarla eyni görünüşdə. */
function StatsRow({ tenantId }: { tenantId: string }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const stats = useQuery({ queryKey: ['platform-tenant-stats', tenantId], queryFn: () => platformApi.stats(tenantId) });
  const n = (value: number | undefined) => (value === undefined ? '–' : formatNumber(value, lang));
  const loading = stats.isPending;
  return (
    <section aria-label={t('platform.detail.stats')} className="flex flex-col gap-3">
      <ApiErrorAlert error={stats.error} />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Kpi icon={<Users />} label={t('platform.detail.totalCustomers')} value={n(stats.data?.totalCustomers)} loading={loading} />
        <Kpi icon={<UserCheck />} label={t('platform.detail.customers')} value={n(stats.data?.activeCustomers)} loading={loading} />
        <Kpi icon={<Store />} label={t('platform.detail.branches')} value={n(stats.data?.branches)} loading={loading} />
        <Kpi icon={<UsersRound />} label={t('platform.detail.staff')} value={n(stats.data?.activeStaff)} loading={loading} />
      </div>
    </section>
  );
}

const inviteSchema = z.object({ email: emailField, language: z.enum(SUPPORTED_LANGUAGES) });
type InviteValues = z.infer<typeof inviteSchema>;

function OwnersCard({ tenantId, onNotice }: { tenantId: string; onNotice: (message: string) => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const validation = useValidationMessage();
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const withStepUp = useStepUp();
  const ownersKey = ['platform-tenant-owners', tenantId];
  const owners = useQuery({ queryKey: ownersKey, queryFn: () => platformApi.owners(tenantId) });
  const form = useForm<InviteValues>({ resolver: zodResolver(inviteSchema), defaultValues: { email: '', language: 'az' } });
  const invite = useMutation({
    mutationFn: (values: InviteValues) => platformApi.inviteOwner(tenantId, values),
    onSuccess: () => {
      form.reset({ email: '', language: form.getValues('language') });
      onNotice(t('admin:platform.detail.invited'));
    },
  });
  const confirm = useConfirm();
  const resetMfa = useMutation({
    mutationFn: (owner: TenantOwner) => withStepUp(() => platformApi.resetOwnerMfa(tenantId, owner.id ?? '')),
    onSuccess: () => {
      onNotice(t('admin:platform.detail.mfaReset'));
      void queryClient.invalidateQueries({ queryKey: ownersKey });
    },
  });

  // Bərpa emaili çatmayanda: link paneldə göstərilir, platforma onu sahibə özü göndərir (məs. WhatsApp)
  const [resetLink, setResetLink] = useState<{ name: string; link: string; expiresAt?: string }>();
  const [copied, setCopied] = useState(false);
  const issueResetLink = useMutation({
    mutationFn: (owner: TenantOwner) => withStepUp(() => platformApi.ownerPasswordResetLink(tenantId, owner.id ?? '')),
    onSuccess: (data, owner) => {
      setCopied(false);
      setResetLink({ name: owner.displayName || owner.email || '', link: data.link ?? '', expiresAt: data.expiresAt });
    },
  });
  const copyLink = () => {
    if (resetLink) {
      void navigator.clipboard.writeText(resetLink.link).then(() => setCopied(true));
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Crown className="size-5 text-primary" aria-hidden="true" />
          {t('admin:platform.detail.owners')}
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        <ApiErrorAlert error={owners.error ?? resetMfa.error ?? issueResetLink.error} />
        {resetLink ? (
          <div className="flex flex-col gap-2 rounded-xl border border-border bg-muted/50 p-4 text-sm">
            <p className="font-semibold">{t('admin:platform.detail.resetLinkTitle', { name: resetLink.name })}</p>
            <p className="text-muted-foreground">
              {t('admin:platform.detail.resetLinkHint', {
                time: resetLink.expiresAt ? formatDateTime(resetLink.expiresAt, lang) : '',
              })}
            </p>
            <div className="flex flex-wrap gap-2">
              <Input readOnly value={resetLink.link} onFocus={(event) => event.target.select()} className="min-w-0 flex-1" />
              <Button type="button" onClick={copyLink}>
                {copied ? t('admin:platform.detail.copied') : t('admin:platform.detail.copyLink')}
              </Button>
            </div>
          </div>
        ) : null}
        {owners.isPending ? <InlineLoading /> : null}
        {owners.data?.length === 0 ? <p className="text-sm text-muted-foreground">{t('admin:platform.detail.noOwners')}</p> : null}
        <ul className="flex flex-col divide-y divide-border">
          {(owners.data ?? []).map((owner) => {
            const name = owner.displayName || owner.email || '';
            return (
              <li key={owner.id} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
                <div className="min-w-0">
                  <p className="font-medium">{name}</p>
                  <p className="break-all text-muted-foreground">{owner.email}</p>
                  <p className="text-xs text-muted-foreground">
                    {owner.lastLoginAt ? formatDateTime(owner.lastLoginAt, lang) : t('admin:team.never')}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <Badge tone={owner.totpEnabled ? 'success' : 'danger'}>
                    {owner.totpEnabled ? t('admin:account.security.enabled') : t('admin:account.security.disabled')}
                  </Badge>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={issueResetLink.isPending}
                    onClick={() => {
                      void confirm({ message: t('admin:platform.detail.resetLinkConfirm', { name }) }).then(
                        (ok) => ok && issueResetLink.mutate(owner),
                      );
                    }}
                  >
                    {t('admin:platform.detail.resetLink')}
                  </Button>
                  {owner.totpEnabled ? (
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={resetMfa.isPending}
                      onClick={() => {
                        void confirm({ message: t('admin:platform.detail.resetMfaConfirm', { name }), danger: true }).then(
                          (ok) => ok && resetMfa.mutate(owner),
                        );
                      }}
                    >
                      {t('admin:platform.detail.resetMfa')}
                    </Button>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
        <form
          onSubmit={form.handleSubmit((values) => invite.mutate(values))}
          className="flex flex-col gap-4 rounded-xl border border-border p-4"
          noValidate
        >
          <p className="text-sm font-semibold">{t('admin:platform.detail.inviteOwner')}</p>
          <ApiErrorAlert error={invite.error} />
          <div className="grid gap-4 sm:grid-cols-[1fr_12rem]">
            <Field id="owner-email" label={t('admin:platform.detail.ownerEmail')} error={validation(form.formState.errors.email?.message)}>
              <Input {...form.register('email')} type="email" inputMode="email" autoComplete="off" />
            </Field>
            <Field id="owner-language" label={t('admin:platform.detail.language')}>
              <Select {...form.register('language')}>
                {SUPPORTED_LANGUAGES.map((value) => (
                  <option key={value} value={value} lang={value}>
                    {t(`common:language.${value}`)}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <Button type="submit" className="self-start" disabled={invite.isPending}>
            {t('admin:platform.detail.invite')}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
