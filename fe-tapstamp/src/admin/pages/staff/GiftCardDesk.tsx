import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Gift, QrCode, ScanLine, ShieldAlert } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';
import { formatDate, formatDateTime } from '@/shared/lib/format';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Input } from '@/shared/ui/input';

import { giftCardConsoleApi, type GiftCardCheck } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { QrScanner } from '../../components/QrScanner';

/** İşçi konsolu: hədiyyə kartının kod və ya QR ilə yoxlanması və təhvili (planşet üçün böyük elementlər). */
export function GiftCardDesk({ branchId }: { branchId: string }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const [code, setCode] = useState('');
  const [scanning, setScanning] = useState(false);
  const recentKey = ['gift-card-recent', branchId];
  const check = useMutation({ mutationFn: (value: string) => giftCardConsoleApi.check(value) });
  const redeem = useMutation({
    mutationFn: (value: string) => giftCardConsoleApi.redeem(value, branchId),
    onSuccess: (card) => {
      check.reset();
      void queryClient.invalidateQueries({ queryKey: recentKey });
      setCode(card.code ?? '');
    },
  });
  const card = redeem.data ?? check.data;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    redeem.reset();
    check.mutate(code.trim());
  };
  const reset = () => {
    setCode('');
    check.reset();
    redeem.reset();
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Gift className="size-5 text-accent" aria-hidden="true" />
          {t('admin:giftDesk.title')}
        </CardTitle>
        <CardDescription>{t('admin:giftDesk.subtitle')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <form className="flex flex-wrap items-end gap-3" onSubmit={submit}>
          <Field id="gift-card-code" label={t('admin:giftDesk.code')}>
            <Input
              value={code}
              onChange={(event) => setCode(event.target.value.toUpperCase().slice(0, 12))}
              placeholder={t('admin:giftDesk.codePlaceholder')}
              autoComplete="off"
              autoCapitalize="characters"
              spellCheck={false}
              className="h-14 font-mono text-2xl tracking-[0.2em]"
            />
          </Field>
          <Button type="submit" size="lg" className="h-14" disabled={code.trim().length < 8 || check.isPending}>
            {t('admin:giftDesk.check')}
          </Button>
          <Button type="button" size="lg" variant="outline" className="h-14" onClick={() => setScanning(true)}>
            <ScanLine aria-hidden="true" />
            {t('admin:giftDesk.scan')}
          </Button>
        </form>
        <ApiErrorAlert error={check.error ?? redeem.error} />
        {card ? (
          <GiftCardResult
            card={card}
            justRedeemed={redeem.isSuccess}
            redeeming={redeem.isPending}
            onRedeem={() => redeem.mutate(card.code ?? '')}
            onReset={reset}
          />
        ) : null}
        <RecentRedemptions branchId={branchId} queryKey={recentKey} />
      </CardContent>
      <Dialog open={scanning} onOpenChange={setScanning}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <QrCode className="size-5" aria-hidden="true" />
              {t('admin:giftDesk.scanTitle')}
            </DialogTitle>
            <DialogDescription>{t('admin:giftDesk.scanHint')}</DialogDescription>
          </DialogHeader>
          {scanning ? (
            <QrScanner
              onResult={(value) => {
                setScanning(false);
                const scanned = value.trim().toUpperCase().slice(0, 12);
                setCode(scanned);
                redeem.reset();
                check.mutate(scanned);
              }}
            />
          ) : null}
          <Button variant="outline" onClick={() => setScanning(false)}>
            {t('admin:giftDesk.close')}
          </Button>
        </DialogContent>
      </Dialog>
    </Card>
  );
}

interface ResultProps {
  card: GiftCardCheck;
  justRedeemed: boolean;
  redeeming: boolean;
  onRedeem: () => void;
  onReset: () => void;
}

function GiftCardResult({ card, justRedeemed, redeeming, onRedeem, onReset }: ResultProps) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const available = card.status === 'AVAILABLE';
  return (
    <section
      aria-live="polite"
      className={cn(
        'rounded-2xl border-2 p-5',
        justRedeemed || available ? 'border-success bg-success/10' : 'border-destructive bg-destructive/10',
      )}
      data-testid="gift-card-result"
    >
      <p className="flex items-center gap-2 text-lg font-bold">
        {justRedeemed || available ? (
          <CheckCircle2 className="size-6 text-success" aria-hidden="true" />
        ) : (
          <ShieldAlert className="size-6 text-destructive" aria-hidden="true" />
        )}
        {justRedeemed
          ? t('giftDesk.redeemed')
          : available
            ? t('giftDesk.valid')
            : t(`giftDesk.status.${card.status ?? 'EXPIRED'}`)}
      </p>
      <dl className="mt-4 grid gap-3 sm:grid-cols-2">
        <Item label={t('giftDesk.reward')} value={resolveText(card.rewardName, lang)} strong />
        <Item label={t('giftDesk.customer')} value={[card.customerName, card.phoneMasked].filter(Boolean).join(' · ')} />
        <Item label={t('giftDesk.earned')} value={card.earnedAt ? formatDate(card.earnedAt, lang) : ''} />
        <Item
          label={t('giftDesk.expires')}
          value={card.expiresAt ? formatDate(card.expiresAt, lang) : t('giftDesk.noExpiry')}
        />
      </dl>
      {card.status === 'REDEEMED' && card.redeemedAt ? (
        <p className="mt-3 text-sm">
          {t('giftDesk.usedAt', {
            date: formatDateTime(card.redeemedAt, lang),
            branch: resolveText(card.redeemedBranchName, lang),
          })}
        </p>
      ) : null}
      <div className="mt-5 flex flex-wrap gap-3">
        {available ? (
          <Button size="lg" className="h-14 flex-1 text-lg" disabled={redeeming} onClick={onRedeem}>
            <Gift aria-hidden="true" />
            {t('giftDesk.redeem')}
          </Button>
        ) : null}
        <Button size="lg" variant="outline" className="h-14" onClick={onReset}>
          {t('giftDesk.anotherCode')}
        </Button>
      </div>
    </section>
  );
}

function Item({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className={cn('mt-0.5', strong ? 'text-xl font-bold' : 'font-medium')}>{value}</dd>
    </div>
  );
}

function RecentRedemptions({ branchId, queryKey }: { branchId: string; queryKey: unknown[] }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const recent = useQuery({ queryKey, queryFn: () => giftCardConsoleApi.recent(branchId), refetchInterval: 30_000 });
  return (
    <section className="flex flex-col gap-2 border-t border-border pt-4">
      <h3 className="text-sm font-semibold">{t('giftDesk.recent')}</h3>
      {recent.data?.length === 0 ? <p className="text-sm text-muted-foreground">{t('giftDesk.recentEmpty')}</p> : null}
      <ul className="flex flex-col gap-1 text-sm">
        {(recent.data ?? []).map((item) => (
          <li key={item.id} className="flex flex-wrap justify-between gap-2">
            <span>
              <span className="font-mono">{item.code}</span> · {resolveText(item.rewardName, lang)}
            </span>
            <span className="text-muted-foreground">{item.redeemedAt ? formatDateTime(item.redeemedAt, lang) : ''}</span>
          </li>
        ))}
      </ul>
      <p className="text-xs text-muted-foreground">{t('giftDesk.recentNote')}</p>
      <ApiErrorAlert error={recent.error} />
    </section>
  );
}
