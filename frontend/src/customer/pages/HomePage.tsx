import { useTranslation } from 'react-i18next';

import { StampCard } from '../components/StampCard';

/** Ana səhifənin skeleti. Real profil məlumatı (API-dən) Mərhələ 4–5-də qoşulur. */
export function HomePage() {
  const { t } = useTranslation('profile');

  return (
    <>
      <section className="pt-2">
        <h1 className="font-display text-3xl font-semibold leading-tight">{t('greeting.guest')}</h1>
        <p className="mt-2 text-muted-foreground">{t('intro')}</p>
      </section>
      <StampCard stampsRequired={9} current={0} />
    </>
  );
}
