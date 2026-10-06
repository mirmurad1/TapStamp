import { useTranslation } from 'react-i18next';

import { Card, CardDescription, CardHeader } from '@/shared/ui/card';

import type { AdminSection } from '../layout/navigation';

/** Hələ hazırlanmamış bölmələr üçün müvəqqəti səhifə. */
export function PlaceholderPage({ section }: { section: AdminSection }) {
  const { t } = useTranslation(['admin', 'common']);

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-display text-2xl font-semibold">{t(`admin:nav.${section}`)}</h1>
      <Card>
        <CardHeader>
          <CardDescription>{t('common:states.comingSoon')}</CardDescription>
        </CardHeader>
      </Card>
    </div>
  );
}
