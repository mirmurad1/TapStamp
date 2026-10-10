import { useMutation } from '@tanstack/react-query';
import { RefreshCw } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { Button } from '@/shared/ui/button';

import { customerAuthApi } from '../api/customerApi';
import { useCustomerSession } from '../auth/CustomerSessionProvider';

/** Parametrlər ekranında: hesabdan çıxıb başqa nömrə ilə daxil olmaq. */
export function SwitchNumber() {
  const { t } = useTranslation('profile');
  const { clear } = useCustomerSession();
  const navigate = useNavigate();
  const logout = useMutation({
    mutationFn: customerAuthApi.logout,
    onSettled: async () => {
      // Əvvəlcə naviqasiya (bu düymə qonaq görünüşündə ekrandan gedir), sonra keşin təmizlənməsi; giriş səhifəsi
      // state-dəki işarəyə görə bu arada daxil olmuş istifadəçini geri yönləndirmir
      await navigate('/login', { replace: true, state: { switchNumber: true } });
      clear();
    },
  });
  return (
    <section className="rounded-2xl border border-border bg-card p-4">
      <Button variant="outline" size="lg" className="w-full" disabled={logout.isPending} onClick={() => logout.mutate()}>
        <RefreshCw aria-hidden="true" />
        {t('switchNumber')}
      </Button>
    </section>
  );
}
