import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

export function NotFoundPage() {
  const { t } = useTranslation('common');

  return (
    <Card className="mt-6">
      <CardHeader>
        <CardTitle>{t('notFound.title')}</CardTitle>
        <CardDescription>{t('notFound.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        <Button asChild>
          <Link to="/">{t('notFound.home')}</Link>
        </Button>
      </CardContent>
    </Card>
  );
}
