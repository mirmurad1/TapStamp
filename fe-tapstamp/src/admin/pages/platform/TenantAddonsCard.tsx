import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { PackagePlus } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

import { ADDONS, platformPlanApi, type AddonName } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';

/**
 * Şirkətin əlavələri (paketdən asılı olmayan, ayrıca satılan bölmələr): platforma admini açıb-bağlayır. Bağlanan
 * əlavənin məlumatları silinmir — yenidən açılanda qayıdır.
 */
export function TenantAddonsCard({ tenantId }: { tenantId: string }) {
  const { t } = useTranslation('admin');
  const queryClient = useQueryClient();
  const key = ['tenant-addons', tenantId];
  const addons = useQuery({ queryKey: key, queryFn: () => platformPlanApi.addons(tenantId) });
  const toggle = useMutation({
    mutationFn: ({ addon, enabled }: { addon: AddonName; enabled: boolean }) =>
      platformPlanApi.setAddon(tenantId, addon, enabled),
    onSuccess: (updated) => queryClient.setQueryData(key, updated),
  });
  const enabled = new Set(addons.data?.addons ?? []);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <PackagePlus className="size-5 text-primary" aria-hidden="true" />
          {t('platform.addons.title')}
        </CardTitle>
        <CardDescription>{t('platform.addons.description')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <ApiErrorAlert error={addons.error ?? toggle.error} />
        {addons.isPending ? <InlineLoading /> : null}
        {addons.data
          ? ADDONS.map((addon) => {
              const on = enabled.has(addon);
              return (
                <div key={addon} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border p-3">
                  <div className="min-w-0">
                    <p className="flex items-center gap-2 font-medium">
                      {t(`platform.addons.names.${addon}`)}
                      {on ? <Badge tone="success">{t('platform.addons.on')}</Badge> : <Badge>{t('platform.addons.off')}</Badge>}
                    </p>
                    <p className="text-sm text-muted-foreground">{t(`platform.addons.hints.${addon}`)}</p>
                  </div>
                  <Button
                    variant={on ? 'outline' : 'default'}
                    disabled={toggle.isPending}
                    onClick={() => toggle.mutate({ addon, enabled: !on })}
                  >
                    {t(on ? 'platform.addons.disable' : 'platform.addons.enable')}
                  </Button>
                </div>
              );
            })
          : null}
      </CardContent>
    </Card>
  );
}
