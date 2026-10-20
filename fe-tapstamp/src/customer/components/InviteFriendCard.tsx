import { useQuery } from '@tanstack/react-query';
import { Check, Copy, Share2, UserPlus } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

import { referralApi } from '../api/customerApi';

/** Ana səhifədə "Dostunu dəvət et": şəxsi link, paylaşma və dəvətlərin nəticəsi. Proqram söndürülübsə görünmür. */
export function InviteFriendCard() {
  const { t } = useTranslation('profile');
  const referral = useQuery({ queryKey: ['my-referral'], queryFn: referralApi.mine });
  const [copied, setCopied] = useState(false);
  const data = referral.data;
  if (!data?.enabled || !data.code) {
    return null;
  }
  const link = `${window.location.origin}/r/${data.code}`;
  const message = t('referral.shareText', { count: data.friendStamps ?? 1, link });

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

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <UserPlus className="size-5 text-primary" aria-hidden="true" />
          {t('referral.title')}
        </CardTitle>
        <CardDescription>
          {t('referral.description', { mine: data.referrerStamps ?? 1, friend: data.friendStamps ?? 1 })}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <p className="break-all rounded-xl bg-muted px-3 py-2 font-mono text-sm" aria-label={t('referral.link')}>
          {link}
        </p>
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => void share()}>
            <Share2 aria-hidden="true" />
            {t('referral.share')}
          </Button>
          <Button variant="outline" onClick={() => void copy()}>
            {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
            {copied ? t('referral.copied') : t('referral.copy')}
          </Button>
        </div>
        {(data.invited ?? 0) > 0 ? (
          <p className="text-sm text-muted-foreground">
            {t('referral.stats', { invited: data.invited ?? 0, rewarded: data.rewarded ?? 0, pending: data.pending ?? 0 })}
          </p>
        ) : null}
        <p className="text-xs text-muted-foreground">
          {t('referral.rules', { cap: data.monthlyCap ?? 5, days: data.validDays ?? 30 })}
        </p>
      </CardContent>
    </Card>
  );
}
