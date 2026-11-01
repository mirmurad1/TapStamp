import { Lock } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { cn } from '@/shared/lib/cn';
import { FEATURE_PLAN, useAddon, useFeature, type Addon, type Feature } from '@/shared/plan/plan';

/** Paketdə olmayan imkan: nə üçün bağlı olduğu və hansı paketə keçmək lazım olduğu. */
export function PlanLockedNotice({ feature, className }: { feature: Feature; className?: string }) {
  const { t } = useTranslation('admin');
  return (
    <div className={cn('flex items-start gap-3 rounded-xl border border-dashed border-border p-4 text-sm', className)}>
      <Lock className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
      <div>
        <p className="font-medium">{t('plan.lockedTitle', { plan: t(`plan.names.${FEATURE_PLAN[feature]}`) })}</p>
        <p className="text-muted-foreground">{t('plan.lockedBody')}</p>
      </div>
    </div>
  );
}

/** Bölmə ayrıca satılan əlavədir və şirkətdə açıq deyil: səhifənin yerinə izah (backend də 403 qaytarır). */
export function RequireAddon({ addon, children }: { addon: Addon; children: ReactNode }) {
  const { t } = useTranslation('admin');
  const has = useAddon(addon);
  if (has === false) {
    return (
      <div className="flex max-w-2xl items-start gap-3 rounded-xl border border-dashed border-border p-4 text-sm">
        <Lock className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
        <div>
          <p className="font-medium">{t('plan.addonLockedTitle')}</p>
          <p className="text-muted-foreground">{t('plan.addonLockedBody')}</p>
        </div>
      </div>
    );
  }
  return children;
}

/** Bütöv bölmə paketə bağlıdır: imkan yoxdursa, səhifənin yerinə izah göstərilir (backend də 403 qaytarır). */
export function RequireFeature({ feature, children }: { feature: Feature; children: ReactNode }) {
  const has = useFeature(feature);
  if (has === false) {
    return <PlanLockedNotice feature={feature} className="max-w-2xl" />;
  }
  return children;
}
