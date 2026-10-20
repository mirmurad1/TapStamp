import { useMutation } from '@tanstack/react-query';
import { RefreshCw } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { Button } from '@/shared/ui/button';

import { customerAuthApi } from '../api/customerApi';
import { disablePush } from '../pwa/push';

/** Parametrlər ekranında: hesabdan çıxıb başqa nömrə ilə daxil olmaq. */
export function SwitchNumber() {
  const { t } = useTranslation('profile');
  const navigate = useNavigate();
  const logout = useMutation({
    // Bu cihazın bildirişləri də söndürülür — başqa nömrə ilə daxil olan şəxs əvvəlkinin bildirişlərini görməsin
    mutationFn: async () => {
      await disablePush().catch(() => undefined);
      return customerAuthApi.logout();
    },
    // Sessiya keşini giriş səhifəsi özü təmizləyir (switchNumber işarəsi ilə) — bax: LoginPage
    onSettled: () => navigate('/login', { replace: true, state: { switchNumber: true } }),
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
