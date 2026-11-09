import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowRightLeft, Download, History, KeyRound, Nfc, Power, PowerOff } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatNumber } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { useConfirm } from '@/shared/ui/ConfirmProvider';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { platformTagsApi, type NfcTag, type ProvisioningPackage } from '../../api/adminApi';
import { useStepUp } from '../../auth/StepUpProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { EventsDialog, PackageDialog, RegisterDialog, TagCard } from '../tags/tagParts';

/** Şirkətin NFC çipləri: qeydiyyat, provisioning paketi, açar rotasiyası, deaktivasiya və pulsuz/pullu say. */
export function TenantTagsCard({ tenantId }: { tenantId: string }) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const withStepUp = useStepUp();
  const confirm = useConfirm();
  const key = ['platform-tags', tenantId];
  const paging = usePagination();
  const overview = useQuery({
    queryKey: [...key, paging.page, paging.size],
    queryFn: () => platformTagsApi.overview(tenantId, { page: paging.page, size: paging.size }),
    placeholderData: keepPreviousData,
  });
  const tags = overview.data?.tags?.items ?? [];
  const [pkg, setPkg] = useState<{ data: ProvisioningPackage; transfer: boolean }>();
  const [eventsOf, setEventsOf] = useState<NfcTag>();
  const refresh = () => void queryClient.invalidateQueries({ queryKey: key });

  const branches = (overview.data?.branches ?? []).map((branch) => ({ id: branch.id ?? '', name: resolveText(branch.name, lang) }));
  const nameOf = (id: string | undefined) => branches.find((branch) => branch.id === id)?.name;
  const usage = overview.data?.usage;

  const toggle = useMutation({
    mutationFn: (tag: NfcTag) =>
      tag.status === 'ACTIVE' ? platformTagsApi.disable(tenantId, tag.id ?? '') : platformTagsApi.enable(tenantId, tag.id ?? ''),
    onSuccess: refresh,
  });
  const rotate = useMutation({
    mutationFn: (tag: NfcTag) => withStepUp(() => platformTagsApi.rotate(tenantId, tag.id ?? '')),
    onSuccess: refresh,
  });
  const exportPkg = useMutation({
    mutationFn: (tag: NfcTag) => withStepUp(() => platformTagsApi.provisioningPackage(tenantId, tag.id ?? '')),
    onSuccess: (data) => {
      setPkg({ data, transfer: false });
      refresh();
    },
  });

  // Deaktiv çipi başqa şirkətə vermək üçün: çipdəki açarlar (ProgramTag yeni.json --old bu.json)
  const transferPkg = useMutation({
    mutationFn: (tag: NfcTag) => withStepUp(() => platformTagsApi.transferPackage(tenantId, tag.id ?? '')),
    onSuccess: (data) => setPkg({ data, transfer: true }),
  });
  const askTransfer = (tag: NfcTag) =>
    void confirm({ message: t('admin:tags.confirm.transfer', { label: tag.label }) }).then((ok) => ok && transferPkg.mutate(tag));

  const askToggle = (tag: NfcTag) => {
    if (tag.status !== 'ACTIVE') {
      toggle.mutate(tag);
      return;
    }
    void confirm({ message: t('admin:tags.confirm.disable', { label: tag.label }), danger: true }).then((ok) => ok && toggle.mutate(tag));
  };
  const askRotate = (tag: NfcTag) =>
    void confirm({ message: t('admin:tags.confirm.rotate', { label: tag.label }) }).then((ok) => ok && rotate.mutate(tag));

  const stats = [
    { label: t('admin:platform.tags.total'), value: usage?.total },
    { label: t('admin:platform.tags.free'), value: usage?.free },
    { label: t('admin:platform.tags.paid'), value: usage?.paid },
  ];

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <CardTitle className="flex items-center gap-2">
              <Nfc className="size-5 text-primary" aria-hidden="true" />
              {t('admin:platform.tags.title')}
            </CardTitle>
            <CardDescription>{t('admin:platform.tags.description', { limit: usage?.freeLimit ?? 1 })}</CardDescription>
          </div>
          {overview.data ? (
            <RegisterDialog branches={branches} register={(body) => platformTagsApi.register(tenantId, body)} onCreated={refresh} />
          ) : null}
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <ApiErrorAlert error={overview.error ?? toggle.error ?? rotate.error ?? exportPkg.error ?? transferPkg.error} />
        {overview.isPending ? <InlineLoading /> : null}
        {overview.data && branches.length === 0 ? <Alert tone="info">{t('admin:platform.tags.noBranches')}</Alert> : null}
        <dl className="grid gap-4 sm:grid-cols-3">
          {stats.map((item) => (
            <div key={item.label} className="rounded-xl bg-muted px-4 py-3">
              <dt className="text-sm text-muted-foreground">{item.label}</dt>
              <dd className="mt-1 font-display text-2xl font-semibold">{item.value === undefined ? '–' : formatNumber(item.value, lang)}</dd>
            </div>
          ))}
        </dl>
        {overview.data && tags.length === 0 ? <p className="text-sm text-muted-foreground">{t('admin:tags.empty')}</p> : null}
        <div className="grid gap-4 lg:grid-cols-2">
          {tags.map((tag) => {
            const active = tag.status === 'ACTIVE';
            return (
              <TagCard key={tag.id} tag={tag} branchName={nameOf(tag.branchId)}>
                <Button variant="outline" size="sm" onClick={() => setEventsOf(tag)}>
                  <History className="size-4" aria-hidden="true" />
                  {t('admin:tags.actions.events')}
                </Button>
                {tag.pendingKeyVersion && !tag.pendingExported ? (
                  <Button size="sm" onClick={() => exportPkg.mutate(tag)} disabled={exportPkg.isPending}>
                    <Download className="size-4" aria-hidden="true" />
                    {t('admin:tags.actions.package')}
                  </Button>
                ) : null}
                {!active && tag.type !== 'SIMULATED' && tag.activeKeyVersion ? (
                  <Button variant="outline" size="sm" onClick={() => askTransfer(tag)} disabled={transferPkg.isPending}>
                    <ArrowRightLeft className="size-4" aria-hidden="true" />
                    {t('admin:tags.actions.transfer')}
                  </Button>
                ) : null}
                {tag.type !== 'SIMULATED' ? (
                  <Button variant="outline" size="sm" onClick={() => askRotate(tag)} disabled={rotate.isPending}>
                    <KeyRound className="size-4" aria-hidden="true" />
                    {t('admin:tags.actions.rotate')}
                  </Button>
                ) : null}
                <Button
                  variant="ghost"
                  size="sm"
                  className={active ? 'text-destructive hover:bg-destructive/10 hover:text-destructive' : undefined}
                  onClick={() => askToggle(tag)}
                  disabled={toggle.isPending}
                >
                  {active ? <PowerOff className="size-4" aria-hidden="true" /> : <Power className="size-4" aria-hidden="true" />}
                  {active ? t('admin:tags.actions.disable') : t('admin:tags.actions.enable')}
                </Button>
              </TagCard>
            );
          })}
        </div>
        <Pagination state={paging} total={overview.data?.tags?.total ?? 0} />
      </CardContent>
      {pkg ? <PackageDialog pkg={pkg.data} transfer={pkg.transfer} onClose={() => setPkg(undefined)} /> : null}
      {eventsOf ? (
        <EventsDialog
          tag={eventsOf}
          load={(query) => platformTagsApi.events(tenantId, eventsOf.id ?? '', query)}
          onClose={() => setEventsOf(undefined)}
        />
      ) : null}
    </Card>
  );
}
