import { useQuery } from '@tanstack/react-query';
import { Check, Copy, Share2, UserPlus } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/shared/ui/button';

import { referralApi } from '../api/customerApi';
import { OfferRow, Sheet } from './Sheet';

/**
 * Ana səhifədə "Dostunu dəvət et" sətri: vərəqdə şəxsi link, paylaşma və dəvətlərin nəticəsi.
 * Proqram söndürülübsə görünmür.
 */
export function InviteFriendRow() {
  const { t } = useTranslation('profile');
  const referral = useQuery({ queryKey: ['my-referral'], queryFn: referralApi.mine });
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const data = referral.data;
  if (!data?.enabled || !data.code) {
    return null;
  }
  const link = `${window.location.origin}/r/${data.code}`;
  const atRegistration = data.rewardOn === 'REGISTRATION';
  const mine = data.referrerStamps ?? 1;
  const friend = data.friendStamps ?? 1;
  const message = t(atRegistration ? 'referral.shareTextRegistration' : 'referral.shareText', { count: friend, link });

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Panoya yazmaq mümkün olmadı — link ekranda görünür
    }
  };
  const share = async () => {
    if (typeof navigator.share === 'function') {
      try {
        await navigator.share({ text: message });
        return;
      } catch {
        // İstifadəçi imtina etdi və ya paylaşma mümkün olmadı
        return;
      }
    }
    await copy();
  };

  const stats = [
    { value: data.invited ?? 0, label: t('referral.statInvited') },
    { value: data.rewarded ?? 0, label: t('referral.statRewarded') },
    { value: data.pending ?? 0, label: t('referral.statPending') },
  ];

  return (
    <>
      <OfferRow
        icon={<UserPlus />}
        title={t('referral.title')}
        hint={t('referral.rowHint', { mine, friend })}
        onClick={() => setOpen(true)}
      />
      <Sheet open={open} onOpenChange={setOpen} title={t('referral.title')}>
        {/* Bonus: "+1 sənə / +1 dostuna" */}
        <div className="shine relative flex items-center gap-4 overflow-hidden rounded-3xl bg-primary p-5 text-left text-primary-foreground">
          {[
            { value: mine, label: t('referral.forYou') },
            { value: friend, label: t('referral.forFriend') },
          ].map((bonus) => (
            <span key={bonus.label} className="flex shrink-0 flex-col items-center">
              <span className="text-3xl leading-none font-extrabold">+{bonus.value}</span>
              <span className="text-xs opacity-85">{bonus.label}</span>
            </span>
          ))}
          <p className="text-sm leading-snug">
            {t(atRegistration ? 'referral.descriptionRegistration' : 'referral.description', { mine, friend })}
          </p>
        </div>
        <div className="flex items-center gap-2 rounded-2xl border border-border bg-background py-1.5 pr-1.5 pl-3.5 text-left">
          <span className="min-w-0 flex-1 truncate text-sm text-muted-foreground" aria-label={t('referral.link')}>
            {link}
          </span>
          <Button size="sm" variant="outline" onClick={() => void copy()}>
            {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
            {copied ? t('referral.copied') : t('referral.copy')}
          </Button>
        </div>
        <Button size="lg" onClick={() => void share()}>
          <Share2 aria-hidden="true" />
          {t('referral.share')}
        </Button>
        {(data.invited ?? 0) > 0 ? (
          <dl className="grid grid-cols-3 gap-2">
            {stats.map((stat) => (
              <div key={stat.label} className="flex flex-col-reverse rounded-2xl border border-border p-2.5">
                <dt className="text-[11.5px] leading-tight text-muted-foreground">{stat.label}</dt>
                <dd className="text-2xl font-extrabold text-primary">{stat.value}</dd>
              </div>
            ))}
          </dl>
        ) : null}
        <p className="text-xs text-muted-foreground">
          {atRegistration
            ? t('referral.rulesRegistration', { cap: data.monthlyCap ?? 5 })
            : t('referral.rules', { cap: data.monthlyCap ?? 5, days: data.validDays ?? 30 })}
        </p>
      </Sheet>
    </>
  );
}
