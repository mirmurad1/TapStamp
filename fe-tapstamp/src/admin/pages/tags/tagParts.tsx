import { useMutation, useQuery } from '@tanstack/react-query';
import { Download, Nfc, Plus } from 'lucide-react';
import { useState, type FormEvent, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { currentLanguage } from '@/shared/i18n/setup';
import { formatDateTime, formatRelative } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';

import type { NfcTag, ProvisioningPackage, TapEvent } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { downloadBlob } from '../../lib/download';

/** Çipin kartı: status, pulsuz/pullu, açarlar, son toxunuş. Düymələr {@code children} ilə verilir. */
export function TagCard({ tag, branchName, children }: { tag: NfcTag; branchName?: string; children?: ReactNode }) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const active = tag.status === 'ACTIVE';
  const simulated = tag.type === 'SIMULATED';
  return (
    <Card>
      <CardContent className="flex flex-col gap-4 pt-5 sm:pt-6">
        <div className="flex items-center gap-3">
          <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary" aria-hidden="true">
            <Nfc className="size-5" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="flex flex-wrap items-center gap-2 font-semibold">
              {tag.label}
              <Badge tone={active ? 'success' : 'danger'}>{t(`admin:tags.status.${active ? 'ACTIVE' : 'DISABLED'}`)}</Badge>
              {simulated ? (
                <Badge>{t('admin:tags.simulated')}</Badge>
              ) : (
                <Badge tone={tag.free ? 'neutral' : 'warning'}>{t(`admin:tags.billing.${tag.free ? 'free' : 'paid'}`)}</Badge>
              )}
            </p>
            <p className="font-mono text-xs text-muted-foreground">{tag.uid}</p>
            <p className="text-sm text-muted-foreground">{branchName}</p>
          </div>
        </div>
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div>
            <dt className="text-muted-foreground">{t('admin:tags.keys')}</dt>
            <dd className="font-medium">
              {simulated
                ? t('admin:tags.simulatorKey')
                : tag.activeKeyVersion
                  ? t('admin:tags.activeKey', { version: tag.activeKeyVersion })
                  : t('admin:tags.notActivated')}
              {tag.pendingKeyVersion ? (
                <span className="block text-xs text-[var(--highlight-ink)]">
                  {tag.pendingExported
                    ? t('admin:tags.pendingExported', { version: tag.pendingKeyVersion })
                    : t('admin:tags.pendingKey', { version: tag.pendingKeyVersion })}
                </span>
              ) : null}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">{t('admin:tags.lastTap')}</dt>
            <dd className="font-medium">
              {tag.lastTapAt ? formatRelative(tag.lastTapAt, lang) : t('admin:team.never')}
              <span className="block text-xs text-muted-foreground">{t('admin:tags.taps7d', { count: tag.tapsLast7Days ?? 0 })}</span>
            </dd>
          </div>
        </dl>
        {children ? <div className="flex flex-wrap gap-2 border-t border-border pt-4">{children}</div> : null}
      </CardContent>
    </Card>
  );
}

export interface RegisterBody {
  uid: string;
  branchId: string;
  label: string;
}

export function RegisterDialog({
  branches,
  register: submitRegister,
  onCreated,
}: {
  branches: { id: string; name: string }[];
  register: (body: RegisterBody) => Promise<unknown>;
  onCreated: () => void;
}) {
  const { t } = useTranslation(['admin', 'common']);
  const [open, setOpen] = useState(false);
  const [uid, setUid] = useState('');
  const [label, setLabel] = useState('');
  const [branchId, setBranchId] = useState('');
  const [invalid, setInvalid] = useState(false);
  const register = useMutation({
    mutationFn: () => submitRegister({ uid: uid.trim(), label: label.trim(), branchId }),
    onSuccess: () => {
      setOpen(false);
      onCreated();
    },
  });

  const start = () => {
    setUid('');
    setLabel('');
    setBranchId(branches[0]?.id ?? '');
    setInvalid(false);
    register.reset();
    setOpen(true);
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!/^([0-9a-fA-F]{2}[:\s]?){6}[0-9a-fA-F]{2}$/.test(uid.trim()) || !label.trim() || !branchId) {
      setInvalid(true);
      return;
    }
    register.mutate();
  };

  return (
    <>
      <Button onClick={start} disabled={branches.length === 0}>
        <Plus className="size-4" aria-hidden="true" />
        {t('admin:tags.register.open')}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('admin:tags.register.title')}</DialogTitle>
            <DialogDescription>{t('admin:tags.register.description')}</DialogDescription>
          </DialogHeader>
          <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
            <ApiErrorAlert error={register.error} />
            {invalid ? <Alert tone="error">{t('admin:tags.register.invalid')}</Alert> : null}
            <Field id="tag-uid" label={t('admin:tags.register.uid')} hint={t('admin:tags.register.uidHint')}>
              <Input value={uid} onChange={(event) => setUid(event.target.value)} className="font-mono" maxLength={32} autoFocus />
            </Field>
            <Field id="tag-label" label={t('admin:tags.register.label')} hint={t('admin:tags.register.labelHint')}>
              <Input value={label} onChange={(event) => setLabel(event.target.value)} maxLength={60} />
            </Field>
            <Field id="tag-branch" label={t('admin:dashboard.filters.branch')}>
              <Select value={branchId} onChange={(event) => setBranchId(event.target.value)}>
                {branches.map((branch) => (
                  <option key={branch.id} value={branch.id}>
                    {branch.name}
                  </option>
                ))}
              </Select>
            </Field>
            <DialogFooter>
              <Button variant="outline" onClick={() => setOpen(false)}>
                {t('common:actions.cancel')}
              </Button>
              <Button type="submit" disabled={register.isPending}>
                {t('admin:tags.register.submit')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}

/** Açarlar yalnız bir dəfə göstərilir — JSON faylı kimi yükləmək təklif olunur. */
export function PackageDialog({ pkg, onClose }: { pkg: ProvisioningPackage; onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const rows: [string, string | number | undefined][] = [
    ['URL', pkg.url],
    ['K0 (AppMasterKey)', pkg.appMasterKey],
    ['K1 (SDMMetaReadKey)', pkg.sdmMetaReadKey],
    ['K2 (SDMFileReadKey)', pkg.sdmFileReadKey],
    ['PICCDataOffset', pkg.piccDataOffset],
    ['SDMMACInputOffset', pkg.sdmMacInputOffset],
    ['SDMMACOffset', pkg.sdmMacOffset],
  ];
  const download = () =>
    downloadBlob(new Blob([JSON.stringify(pkg, null, 2)], { type: 'application/json' }), `tag-${pkg.uid}-v${pkg.keyVersion}.json`);

  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t('admin:tags.package.title', { uid: pkg.uid, version: pkg.keyVersion })}</DialogTitle>
          <DialogDescription>{t('admin:tags.package.description')}</DialogDescription>
        </DialogHeader>
        <Alert tone="error">{t('admin:tags.package.once')}</Alert>
        <dl className="flex flex-col gap-2 text-sm">
          {rows.map(([name, value]) => (
            <div key={name} className="grid gap-1 sm:grid-cols-[11rem_1fr]">
              <dt className="text-muted-foreground">{name}</dt>
              <dd className="break-all font-mono text-xs">{value}</dd>
            </div>
          ))}
        </dl>
        <DialogFooter>
          <Button variant="outline" onClick={download}>
            <Download className="size-4" aria-hidden="true" />
            {t('admin:tags.package.download')}
          </Button>
          <Button onClick={onClose}>{t('common:actions.close')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function EventsDialog({ tag, load, onClose }: { tag: NfcTag; load: () => Promise<TapEvent[]>; onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const events = useQuery({ queryKey: ['tag-events', tag.id], queryFn: load });
  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t('admin:tags.events.title', { label: tag.label })}</DialogTitle>
          <DialogDescription>{t('admin:tags.events.description')}</DialogDescription>
        </DialogHeader>
        <ApiErrorAlert error={events.error} />
        {events.isPending ? <InlineLoading /> : null}
        {events.data && events.data.length === 0 ? <p className="text-sm text-muted-foreground">{t('admin:tags.events.empty')}</p> : null}
        <ul className="flex max-h-[60vh] flex-col divide-y divide-border overflow-y-auto text-sm">
          {events.data?.map((event) => (
            <li key={event.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
              <span className="flex items-center gap-2">
                <Badge tone={event.outcome === 'ACCEPTED' ? 'success' : 'danger'}>
                  {t(`admin:tags.events.outcome.${event.outcome === 'ACCEPTED' ? 'ACCEPTED' : 'REJECTED'}`)}
                </Badge>
                <span className="text-muted-foreground">{t(`admin:tags.events.stage.${event.stage === 'CLAIM' ? 'CLAIM' : 'VERIFY'}`)}</span>
                {event.reason ? <code className="text-xs">{event.reason}</code> : null}
              </span>
              <span className="text-xs text-muted-foreground">
                {event.counter != null ? `#${event.counter} · ` : ''}
                {event.createdAt ? formatDateTime(event.createdAt, lang) : null}
              </span>
            </li>
          ))}
        </ul>
      </DialogContent>
    </Dialog>
  );
}
