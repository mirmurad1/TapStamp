import { useQuery } from '@tanstack/react-query';
import { Gift, LoaderCircle, MapPin } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatDate } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';

import { publicApi, type Me } from '../api/customerApi';
import { useCustomerSession } from '../auth/CustomerSessionProvider';
import { StampCard } from '../components/StampCard';

/** Ana səhifə: daxil olmuş müştəri üçün möhür kartı, qonaq üçün kafe haqqında qısa məlumat və giriş düyməsi. */
export function HomePage() {
  const { status, me } = useCustomerSession();
  if (status === 'loading') {
    return <Loading />;
  }
  return status === 'signedIn' && me ? <Profile me={me} /> : <Guest />;
}

function Loading() {
  const { t } = useTranslation('common');
  return (
    <div className="flex flex-1 items-center justify-center gap-2 py-16 text-muted-foreground" role="status">
      <LoaderCircle className="size-5 animate-spin" aria-hidden="true" />
      {t('states.loading')}
    </div>
  );
}

function Profile({ me }: { me: Me }) {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const card = me.card;
  const rewards = me.availableRewards ?? [];
  return (
    <>
      <section className="pt-2">
        <h1 className="font-display text-3xl font-semibold leading-tight">
          {me.name ? t('greeting.named', { name: me.name }) : t('greeting.guest')}
        </h1>
        <p className="mt-2 text-muted-foreground">{t('intro')}</p>
      </section>
      {card ? (
        <StampCard
          stampsRequired={card.stampsRequired ?? 9}
          current={card.currentStamps ?? 0}
          rewardName={resolveText(card.rewardName, lang)}
        />
      ) : null}
      {rewards.length > 0 ? (
        <Card>
          <CardHeader>
            <CardTitle>{t('rewards.title')}</CardTitle>
            <p className="text-sm text-muted-foreground">{t('rewards.hint')}</p>
          </CardHeader>
          <CardContent>
            <ul className="flex flex-col gap-3">
              {rewards.map((reward) => (
                <li key={reward.id} className="flex items-center justify-between gap-3 rounded-xl bg-muted px-4 py-3">
                  <span className="flex min-w-0 items-center gap-3">
                    <Gift className="size-5 shrink-0 text-accent" aria-hidden="true" />
                    <span className="min-w-0">
                      <span className="block font-medium">{resolveText(reward.name, lang)}</span>
                      {reward.earnedAt ? (
                        <span className="block text-xs text-muted-foreground">{formatDate(reward.earnedAt, lang)}</span>
                      ) : null}
                    </span>
                  </span>
                  <Badge tone="success">{t('rewards.ready')}</Badge>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      ) : null}
    </>
  );
}

function Guest() {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const company = useQuery({ queryKey: ['public-company'], queryFn: publicApi.company, staleTime: 5 * 60_000 });
  const rule = company.data?.rewardRule;
  return (
    <>
      <section className="pt-2">
        <h1 className="font-display text-3xl font-semibold leading-tight">
          {resolveText(company.data?.name, lang) || t('greeting.guest')}
        </h1>
        <p className="mt-2 text-muted-foreground">
          {resolveText(company.data?.description, lang) || t('intro')}
        </p>
      </section>
      {rule?.stampsRequired ? (
        <Card className="bg-primary text-primary-foreground">
          <CardContent className="flex items-center gap-4 pt-5 sm:pt-6">
            <Gift className="size-8 shrink-0" aria-hidden="true" />
            <p className="font-medium">
              {t('guest.rule', { count: rule.stampsRequired, reward: resolveText(rule.rewardName, lang) })}
            </p>
          </CardContent>
        </Card>
      ) : null}
      <Button asChild size="lg">
        <Link to="/login">{t('guest.cta')}</Link>
      </Button>
      {company.data?.branches && company.data.branches.length > 0 ? (
        <Card>
          <CardHeader>
            <CardTitle>{t('guest.branches')}</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="flex flex-col gap-3">
              {company.data.branches.map((branch) => (
                <li key={branch.id} className="flex items-start gap-3">
                  <MapPin className="mt-0.5 size-5 shrink-0 text-accent" aria-hidden="true" />
                  <span>
                    <span className="block font-medium">{resolveText(branch.name, lang)}</span>
                    <span className="block text-sm text-muted-foreground">{resolveText(branch.address, lang)}</span>
                  </span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      ) : null}
    </>
  );
}
