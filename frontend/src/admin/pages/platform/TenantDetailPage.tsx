import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useParams } from 'react-router';
import { z } from 'zod';

import { SUPPORTED_LANGUAGES } from '@/shared/i18n/languages';
import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatDateTime, formatNumber } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';
import { useConfirm } from '@/shared/ui/ConfirmProvider';

import { platformApi, type Tenant, type TenantOwner } from '../../api/adminApi';
import { useStepUp } from '../../auth/StepUpProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { emailField, useValidationMessage } from '../../lib/forms';
import { TENANTS_KEY } from './keys';
import { TenantStatusBadge } from './TenantStatusBadge';

export function TenantDetailPage() {
  const { t } = useTranslation('admin');
  const { tenantId = '' } = useParams();
  const created = Boolean((useLocation().state as { created?: boolean } | null)?.created);
  const [notice, setNotice] = useState<string | undefined>(created ? t('platform.provision.created') : undefined);
  const tenant = useQuery({ queryKey: ['platform-tenant', tenantId], queryFn: () => platformApi.tenant(tenantId) });
  const active = tenant.data?.status === 'ACTIVE';

  return (
    <div className="flex max-w-4xl flex-col gap-6">
      <Button asChild variant="ghost" className="self-start">
        <Link to="/">
          <ArrowLeft aria-hidden="true" />
          {t('platform.detail.back')}
        </Link>
      </Button>
      <ApiErrorAlert error={tenant.error} />
      {tenant.isPending ? <InlineLoading /> : null}
      {tenant.data ? (
        <>
          <TenantHeader tenant={tenant.data} onNotice={setNotice} />
          {notice ? <Alert tone="success">{notice}</Alert> : null}
          {active ? (
            <>
              <StatsCard tenantId={tenantId} />
              <OwnersCard tenantId={tenantId} onNotice={setNotice} />
            </>
          ) : (
            <Alert tone="info">{t('platform.detail.unavailable')}</Alert>
          )}
        </>
      ) : null}
    </div>
  );
}

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

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <CardTitle>{resolveText(tenant.displayName, lang)}</CardTitle>
            <p className="mt-1 font-mono text-xs text-muted-foreground">{tenant.slug}</p>
          </div>
          <TenantStatusBadge status={tenant.status} />
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <ApiErrorAlert error={toggle.error} />
        <div>
          <p className="text-sm font-medium">{t('platform.detail.domains')}</p>
          <ul className="mt-1 flex flex-wrap gap-2">
            {(tenant.domains ?? []).map((domain) => (
              <li key={domain}>
                <Badge tone={domain === tenant.primaryDomain ? 'success' : 'neutral'}>{domain}</Badge>
              </li>
            ))}
          </ul>
        </div>
        {tenant.status === 'ACTIVE' || suspended ? (
          <Button variant={suspended ? 'default' : 'outline'} className="self-start" onClick={onToggle} disabled={toggle.isPending}>
            {suspended ? t('platform.detail.activate') : t('platform.detail.suspend')}
          </Button>
        ) : null}
      </CardContent>
    </Card>
  );
}

function StatsCard({ tenantId }: { tenantId: string }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const stats = useQuery({ queryKey: ['platform-tenant-stats', tenantId], queryFn: () => platformApi.stats(tenantId) });
  const items = [
    { label: t('platform.detail.branches'), value: stats.data?.branches },
    { label: t('platform.detail.customers'), value: stats.data?.activeCustomers },
    { label: t('platform.detail.staff'), value: stats.data?.activeStaff },
  ];
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('platform.detail.stats')}</CardTitle>
      </CardHeader>
      <CardContent>
        <ApiErrorAlert error={stats.error} />
        <dl className="grid gap-4 sm:grid-cols-3">
          {items.map((item) => (
            <div key={item.label} className="rounded-xl bg-muted px-4 py-3">
              <dt className="text-sm text-muted-foreground">{item.label}</dt>
              <dd className="mt-1 font-display text-2xl font-semibold">
                {item.value === undefined ? '–' : formatNumber(item.value, lang)}
              </dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
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

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('admin:platform.detail.owners')}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        <ApiErrorAlert error={owners.error ?? resetMfa.error} />
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
