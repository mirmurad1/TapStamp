import { useQuery } from '@tanstack/react-query';
import { ChevronRight, Gift, Hourglass, LoaderCircle, MapPin, TimerReset } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatCountdown } from '@/shared/lib/brand';
import { Button } from '@/shared/ui/button';

import { publicApi, type Me } from '../api/customerApi';
import { useCustomerSession } from '../auth/CustomerSessionProvider';
import { useJustSignedIn } from '../auth/justSignedIn';
import { BonusRulesBanner } from '../components/BonusRulesBanner';
import { InviteFriendRow } from '../components/InviteFriendCard';
import { StampCard, StampCardNote } from '../components/StampCard';
import { TierChip, TierNote } from '../components/TierCard';
import { useMyTier } from '../lib/useMyTier';
import { useSecondsUntil } from '../lib/useSecondsUntil';
import { InstallRow } from '../pwa/InstallCard';
import { NotificationsRow } from '../pwa/NotificationsCard';

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
  const justSignedIn = useJustSignedIn();
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const card = me.card;
  const rewards = me.availableRewards ?? [];
  const tier = useMyTier();
  return (
    <>
      <section className="pt-2">
        <h1 className="font-display text-3xl font-extrabold leading-tight tracking-tight">
          {me.name ? t('greeting.named', { name: me.name }) : t('greeting.guest')}
        </h1>
        <p className="mt-1.5 text-sm text-muted-foreground">{t('intro')}</p>
      </section>
      <BonusRulesBanner />
      {card ? (
        <StampCard
          stampsRequired={card.stampsRequired ?? 9}
          current={card.currentStamps ?? 0}
          pending={card.pendingStamps ?? 0}
          showReturnHint={justSignedIn}
          rewardName={resolveText(card.rewardName, lang)}
          badge={tier?.current ? <TierChip /> : undefined}
          footer={
            <>
              {card.pendingStamps ? (
                <StampCardNote tone="warn" icon={<Hourglass aria-hidden="true" />}>
                  {t('card.pending', { count: card.pendingStamps })}
                </StampCardNote>
              ) : null}
              <NextStamp at={card.nextStampAt} />
              <TierNote />
            </>
          }
        />
      ) : null}
      {rewards.length > 0 ? (
        // Bilet formalı blok: solda hədiyyə ikonu, qırıq xətt və iki yarımdairə kəsik, sağda hazır hədiyyələr
        // Kölgə valideyndədir: kəsilmiş bilet formasını izləyir
        <div className="drop-shadow-[0_6px_10px_rgb(0_0_0/0.16)]">
          <Link
            to="/gift-cards"
            className="shine animate-attention ticket-ends relative flex items-center gap-3 overflow-hidden rounded-2xl bg-primary p-4 text-primary-foreground [--notch-x:calc(3.75rem-1px)] [--shine-delay:1.2s] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            {/* Hədiyyə ikonu vaxtaşırı yellənir, yanında hazır hədiyyələrin sayı */}
            <span className="relative grid w-11 shrink-0 place-items-center border-r-2 border-dashed border-white/40 pr-3">
              <Gift className="animate-gift-wiggle size-7" aria-hidden="true" />
              <span className="absolute -top-2 right-1 grid h-[18px] min-w-[18px] place-items-center rounded-full bg-white px-1 text-[10.5px] font-extrabold text-primary shadow">
                {rewards.length}
              </span>
            </span>
            <span className="min-w-0 flex-1 pl-1">
              <span className="block text-base font-bold">{t('rewards.title')}</span>
              <span className="block truncate text-sm opacity-90">
                {rewards.map((reward) => resolveText(reward.name, lang)).join(', ')}
              </span>
            </span>
            <ChevronRight className="animate-nudge size-6 shrink-0" aria-label={t('rewards.open')} />
          </Link>
        </div>
      ) : null}
      {/* Təkliflər bir siyahıdadır; heç biri göstərilmirsə siyahı da görünmür */}
      <div className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card empty:hidden">
        <InviteFriendRow />
        <NotificationsRow />
        <InstallRow />
      </div>
    </>
  );
}

/** Cooldown: növbəti möhürə qalan vaxt (şirkətin standart qaydası ilə) — kartın alt zolağında. */
function NextStamp({ at }: { at: string | undefined }) {
  const { t } = useTranslation('profile');
  const until = at ? Date.parse(at) : undefined;
  const secondsLeft = useSecondsUntil(until);
  if (!secondsLeft) {
    return null;
  }
  return (
    <StampCardNote icon={<TimerReset aria-hidden="true" />}>
      {t('card.nextStampIn', { time: formatCountdown(secondsLeft) })}
    </StampCardNote>
  );
}

function Guest() {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const company = useQuery({ queryKey: ['public-company'], queryFn: publicApi.company, staleTime: 5 * 60_000 });
  const rule = company.data?.rewardRule;
  const branches = company.data?.branches ?? [];
  const required = rule?.stampsRequired ?? 0;
  return (
    <>
      <section className="pt-2">
        <h1 className="font-display text-3xl font-extrabold leading-tight tracking-tight">
          {resolveText(company.data?.name, lang) || t('greeting.guest')}
        </h1>
        <p className="mt-1.5 text-muted-foreground">{resolveText(company.data?.description, lang) || t('intro')}</p>
      </section>
      <BonusRulesBanner />
      {rule && required > 0 ? (
        // Qayda boş kartla göstərilir: müştəri nə toplayacağını görür
        <section className="rounded-3xl border border-primary/15 bg-card p-5 shadow-[0_14px_30px_-18px_color-mix(in_srgb,var(--primary)_55%,transparent)]">
          <p className="font-bold">
            {t('guest.rule', { count: required, reward: resolveText(rule.rewardName, lang) })}
          </p>
          <ol className="mt-4 grid grid-cols-5 gap-2.5" aria-hidden="true">
            {Array.from({ length: required }, (_, index) =>
              index === required - 1 ? (
                <li key={index} className="flex justify-center">
                  <span className="flex size-12 items-center justify-center rounded-full border-2 border-primary/35 bg-primary/10 text-primary">
                    <Gift className="size-5" />
                  </span>
                </li>
              ) : (
                <li key={index} className="flex justify-center">
                  <span className="flex size-12 items-center justify-center rounded-full border-2 border-dashed border-border text-sm leading-none font-bold text-muted-foreground/50">
                    {index + 1}
                  </span>
                </li>
              ),
            )}
          </ol>
        </section>
      ) : null}
      <Button asChild size="lg">
        <Link to="/login">{t('guest.cta')}</Link>
      </Button>
      {branches.length > 0 ? (
        <section className="flex flex-col gap-2">
          <h2 className="px-0.5 text-sm font-bold text-muted-foreground">{t('guest.branches')}</h2>
          <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card">
            {branches.map((branch) => (
              <li key={branch.id} className="flex items-start gap-3 px-4 py-3.5">
                <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary">
                  <MapPin className="size-5" aria-hidden="true" />
                </span>
                <span className="min-w-0">
                  <span className="block text-sm font-semibold">{resolveText(branch.name, lang)}</span>
                  <span className="block text-[13px] text-muted-foreground">{resolveText(branch.address, lang)}</span>
                </span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </>
  );
}
