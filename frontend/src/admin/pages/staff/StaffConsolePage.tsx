import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, Hourglass, X } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatRelative, formatTime } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';

import { contextApi, staffConsoleApi, type PendingStamp } from '../../api/adminApi';
import { useMe } from '../../auth/AdminAuthProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { hasAnyRole } from '../../layout/navigation';

/** İşçi konsolu: filialın təsdiq gözləyən möhürləri (5 saniyədən bir yenilənir). */
export function StaffConsolePage() {
  const { t } = useTranslation('admin');
  const me = useMe();
  const lang = currentLanguage();
  const company = useQuery({ queryKey: ['public-company'], queryFn: contextApi.company });
  const allBranches = company.data?.branches ?? [];
  const companyWide = hasAnyRole(me, ['COMPANY_OWNER']);
  const myBranchIds = new Set((me.roles ?? []).map((grant) => grant.branchId).filter(Boolean));
  const branches = allBranches.filter((branch) => companyWide || myBranchIds.has(branch.id));
  const [selected, setSelected] = useState<string>();
  const branchId = selected ?? branches[0]?.id;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-bold">{t('staffConsole.title')}</h1>
          <p className="mt-1 text-muted-foreground">{t('staffConsole.subtitle')}</p>
        </div>
        {branches.length > 1 ? (
          <Field id="console-branch" label={t('staffConsole.branch')}>
            <Select value={branchId} onChange={(event) => setSelected(event.target.value)}>
              {branches.map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {resolveText(branch.name, lang)}
                </option>
              ))}
            </Select>
          </Field>
        ) : null}
      </div>
      {branchId ? <PendingStamps branchId={branchId} /> : company.isPending ? <InlineLoading /> : null}
    </div>
  );
}

function PendingStamps({ branchId }: { branchId: string }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const key = ['pending-stamps', branchId];
  const [notice, setNotice] = useState<string>();
  const [rejecting, setRejecting] = useState<PendingStamp>();
  const pending = useQuery({
    queryKey: key,
    queryFn: () => staffConsoleApi.pendingStamps(branchId),
    refetchInterval: 5_000,
    refetchIntervalInBackground: false,
  });
  const confirm = useMutation({
    mutationFn: (stampId: string) => staffConsoleApi.confirmStamp(stampId),
    onSuccess: () => {
      setNotice(t('staffConsole.pending.confirmed'));
      void queryClient.invalidateQueries({ queryKey: key });
    },
    onSettled: () => void queryClient.invalidateQueries({ queryKey: key }),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Hourglass className="size-5 text-highlight" aria-hidden="true" />
          {t('staffConsole.pending.title')}
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {notice ? <Alert tone="success">{notice}</Alert> : null}
        <ApiErrorAlert error={pending.error ?? confirm.error} />
        {pending.isPending ? <InlineLoading /> : null}
        {pending.data?.length === 0 ? <p className="text-muted-foreground">{t('staffConsole.pending.empty')}</p> : null}
        <ul className="flex flex-col divide-y divide-border">
          {(pending.data ?? []).map((stamp) => (
            <li key={stamp.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="min-w-0">
                <p className="font-semibold">{stamp.customerName || t('staffConsole.pending.anonymous')}</p>
                <p className="text-sm text-muted-foreground" dir="ltr">
                  {stamp.phoneMasked}
                </p>
                <p className="text-xs text-muted-foreground">
                  {stamp.createdAt ? formatRelative(stamp.createdAt, lang) : null}
                  {stamp.expiresAt ? ` · ${t('staffConsole.pending.expires', { time: formatTime(stamp.expiresAt, lang) })}` : null}
                </p>
              </div>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => setRejecting(stamp)}>
                  <X aria-hidden="true" />
                  {t('staffConsole.pending.reject')}
                </Button>
                <Button disabled={confirm.isPending} onClick={() => confirm.mutate(stamp.id ?? '')}>
                  <Check aria-hidden="true" />
                  {t('staffConsole.pending.confirm')}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      </CardContent>
      <RejectDialog
        stamp={rejecting}
        onClose={() => setRejecting(undefined)}
        onRejected={() => {
          setRejecting(undefined);
          setNotice(t('staffConsole.pending.rejected'));
          void queryClient.invalidateQueries({ queryKey: key });
        }}
      />
    </Card>
  );
}

function RejectDialog({ stamp, onClose, onRejected }: { stamp?: PendingStamp; onClose: () => void; onRejected: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const [reason, setReason] = useState('');
  const reject = useMutation({
    mutationFn: () => staffConsoleApi.rejectStamp(stamp?.id ?? '', reason.trim()),
    onSuccess: () => {
      setReason('');
      onRejected();
    },
  });
  return (
    <Dialog open={stamp !== undefined} onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('admin:staffConsole.pending.rejectTitle')}</DialogTitle>
          <DialogDescription>{stamp?.customerName || stamp?.phoneMasked}</DialogDescription>
        </DialogHeader>
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            reject.mutate();
          }}
        >
          <ApiErrorAlert error={reject.error} />
          <Field id="reject-reason" label={t('admin:staffConsole.pending.rejectReason')} hint={t('admin:staffConsole.pending.rejectHint')}>
            <Input value={reason} onChange={(event) => setReason(event.target.value)} maxLength={200} autoFocus />
          </Field>
          <DialogFooter>
            <Button variant="outline" onClick={onClose}>
              {t('common:actions.cancel')}
            </Button>
            <Button type="submit" disabled={reason.trim().length === 0 || reject.isPending}>
              {t('admin:staffConsole.pending.reject')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
