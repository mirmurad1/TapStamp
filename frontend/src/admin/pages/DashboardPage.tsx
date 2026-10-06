import { useTranslation } from 'react-i18next';

import { Card, CardDescription, CardHeader } from '@/shared/ui/card';

/** İdarəetmə panelinin skeleti; göstəricilər və qrafiklər Mərhələ 7-də əlavə olunur. */
export function DashboardPage() {
  const { t } = useTranslation(['admin', 'common']);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">{t('admin:dashboard.title')}</h1>
        <p className="mt-1 text-muted-foreground">{t('admin:dashboard.subtitle')}</p>
      </div>
      <Card>
        <CardHeader>
          <CardDescription>{t('common:states.comingSoon')}</CardDescription>
        </CardHeader>
      </Card>
    </div>
  );
}
