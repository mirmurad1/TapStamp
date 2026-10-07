import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Globe, Star, Trash2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { useConfirm } from '@/shared/ui/ConfirmProvider';
import { Input } from '@/shared/ui/input';

import { platformDomainsApi, type TenantDomain } from '../../api/adminApi';
import { useStepUp } from '../../auth/StepUpProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';

const TLS_TONE = { ISSUED: 'success', PENDING: 'warning', FAILED: 'danger' } as const;

/** Şirkətin domenləri: əlavə etmək, əsas etmək (step-up), silmək (step-up; əsas domen silinmir). */
export function DomainsCard({ tenantId }: { tenantId: string }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const withStepUp = useStepUp();
  const confirm = useConfirm();
  const key = ['platform-domains', tenantId];
  const domains = useQuery({ queryKey: key, queryFn: () => platformDomainsApi.list(tenantId) });
  const [hostname, setHostname] = useState('');
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: key });
    void queryClient.invalidateQueries({ queryKey: ['platform-tenant', tenantId] });
  };

  const add = useMutation({
    mutationFn: () => platformDomainsApi.add(tenantId, hostname.trim()),
    onSuccess: () => {
      setHostname('');
      refresh();
    },
  });
  const primary = useMutation({
    mutationFn: (domain: TenantDomain) => withStepUp(() => platformDomainsApi.makePrimary(tenantId, domain.id ?? '')),
    onSuccess: refresh,
  });
  const remove = useMutation({
    mutationFn: (domain: TenantDomain) => withStepUp(() => platformDomainsApi.remove(tenantId, domain.id ?? '')),
    onSuccess: refresh,
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (hostname.trim()) {
      add.mutate();
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Globe className="size-5 text-primary" aria-hidden="true" />
          {t('admin:platform.domains.title')}
        </CardTitle>
        <CardDescription>{t('admin:platform.domains.description')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <ApiErrorAlert error={domains.error ?? add.error ?? primary.error ?? remove.error} />
        {domains.isPending ? <InlineLoading /> : null}
        <ul className="flex flex-col divide-y divide-border">
          {domains.data?.map((domain) => (
            <li key={domain.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-mono text-sm">{domain.hostname}</span>
                {domain.primary ? <Badge tone="success">{t('admin:platform.domains.primary')}</Badge> : null}
                <Badge tone={TLS_TONE[(domain.tlsStatus ?? 'PENDING') as keyof typeof TLS_TONE] ?? 'neutral'}>
                  {t(`admin:platform.domains.tls.${(domain.tlsStatus ?? 'PENDING') as 'PENDING'}`)}
                </Badge>
              </div>
              {!domain.primary ? (
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={primary.isPending}
                    onClick={() =>
                      void confirm({ message: t('admin:platform.domains.confirmPrimary', { host: domain.hostname }) }).then(
                        (ok) => ok && primary.mutate(domain),
                      )
                    }
                  >
                    <Star className="size-4" aria-hidden="true" />
                    {t('admin:platform.domains.makePrimary')}
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                    disabled={remove.isPending}
                    onClick={() =>
                      void confirm({ message: t('admin:platform.domains.confirmRemove', { host: domain.hostname }), danger: true }).then(
                        (ok) => ok && remove.mutate(domain),
                      )
                    }
                  >
                    <Trash2 className="size-4" aria-hidden="true" />
                    {t('common:actions.delete')}
                  </Button>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
        <form onSubmit={submit} className="flex flex-wrap gap-2">
          <Input
            value={hostname}
            onChange={(event) => setHostname(event.target.value)}
            placeholder={t('admin:platform.domains.placeholder')}
            aria-label={t('admin:platform.domains.add')}
            className="min-w-60 flex-1 font-mono"
            maxLength={253}
          />
          <Button type="submit" disabled={add.isPending || !hostname.trim()}>
            {t('admin:platform.domains.add')}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
