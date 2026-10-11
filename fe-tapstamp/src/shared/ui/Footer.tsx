import { useTranslation } from 'react-i18next';

import { cn } from '@/shared/lib/cn';

/** Bütün səhifələrin altı: müəllif hüququ (Trend Solutions). */
export function Footer({ className }: { className?: string }) {
  const { t } = useTranslation('common');
  return <footer className={cn('py-4 text-center text-xs text-muted-foreground', className)}>{t('footer.copyright')}</footer>;
}
