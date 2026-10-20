import { useQuery } from '@tanstack/react-query';
import { Gift, LoaderCircle } from 'lucide-react';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useParams } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

import { publicApi, referralApi } from '../api/customerApi';
import { useCustomerSession } from '../auth/CustomerSessionProvider';
import { isReferralCode, storeReferralCode } from '../lib/referral';

/** Dəvət linki ({@code /r/:code}): kod yadda saxlanılır və qonaq qeydiyyata yönləndirilir. */
export function ReferralPage() {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const { code } = useParams();
  const { status } = useCustomerSession();
  const valid = isReferralCode(code);
  const program = useQuery({ queryKey: ['referral-program'], queryFn: referralApi.program, enabled: valid });
  const company = useQuery({ queryKey: ['public-company'], queryFn: publicApi.company, staleTime: 5 * 60_000 });

  useEffect(() => {
    if (valid && status === 'guest') {
      storeReferralCode(code);
    }
  }, [valid, code, status]);

  if (!valid) {
    return <Navigate to="/" replace />;
  }
  if (status === 'loading' || program.isPending) {
    return (
      <div className="flex flex-1 items-center justify-center py-16 text-muted-foreground" role="status">
        <LoaderCircle className="size-5 animate-spin" aria-hidden="true" />
      </div>
    );
  }
  // Proqram söndürülübsə — adi giriş
  if (!program.data?.enabled) {
    return <Navigate to={status === 'signedIn' ? '/' : '/login'} replace />;
  }
  const companyName = resolveText(company.data?.name, lang);

  return (
    <Card className="mt-4">
      <CardHeader>
        <Gift className="size-10 text-primary" aria-hidden="true" />
        <CardTitle className="text-2xl">{t('referral.landing.title', { company: companyName })}</CardTitle>
        <CardDescription>
          {t(program.data.rewardOn === 'REGISTRATION' ? 'referral.landing.descriptionRegistration' : 'referral.landing.description', {
            count: program.data.friendStamps ?? 1,
          })}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {status === 'signedIn' ? (
          <>
            <p className="text-sm text-muted-foreground">{t('referral.landing.alreadyMember')}</p>
            <Button asChild size="lg">
              <Link to="/">{t('referral.landing.home')}</Link>
            </Button>
          </>
        ) : (
          <Button asChild size="lg">
            <Link to="/login">{t('referral.landing.join')}</Link>
          </Button>
        )}
      </CardContent>
    </Card>
  );
}
