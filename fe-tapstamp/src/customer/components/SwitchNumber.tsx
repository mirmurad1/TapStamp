import { useMutation } from '@tanstack/react-query';
import { LogOut, RefreshCw } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { Button } from '@/shared/ui/button';

import { customerAuthApi } from '../api/customerApi';
import { disablePush } from '../pwa/push';
import { Sheet } from './Sheet';

/**
 * Parametrlər ekranında: hesabdan çıxıb başqa nömrə ilə daxil olmaq. Düyməyə səhvən toxunula bilər — çıxışdan əvvəl
 * təsdiq vərəqi açılır.
 */
export function SwitchNumber() {
  const { t } = useTranslation('profile');
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState(false);
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
      <Button variant="outline" size="lg" className="w-full" disabled={logout.isPending} onClick={() => setConfirming(true)}>
        <RefreshCw aria-hidden="true" />
        {t('switchNumber')}
      </Button>
      <Sheet
        open={confirming}
        onOpenChange={setConfirming}
        icon={<LogOut />}
        title={t('switchNumberConfirm.title')}
        description={t('switchNumberConfirm.description')}
      >
        <Button size="lg" disabled={logout.isPending} onClick={() => logout.mutate()}>
          {t('switchNumberConfirm.confirm')}
        </Button>
        <Button size="lg" variant="outline" onClick={() => setConfirming(false)}>
          {t('switchNumberConfirm.cancel')}
        </Button>
      </Sheet>
    </section>
  );
}
