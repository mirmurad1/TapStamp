import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download, History, KeyRound, Nfc, Plus, Power, PowerOff } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { currentLanguage } from '@/shared/i18n/setup';
import { formatDateTime, formatRelative } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';
import { useConfirm } from '@/shared/ui/ConfirmProvider';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';

import { tagsApi, type NfcTag, type ProvisioningPackage } from '../../api/adminApi';
import { useMe } from '../../auth/AdminAuthProvider';
import { useStepUp } from '../../auth/StepUpProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { hasAnyRole } from '../../layout/navigation';
import { useBranchNames } from '../../lib/branches';
import { downloadBlob } from '../../lib/download';

const TAGS_KEY = ['tags'] as const;

export function TagsPage() {
  const { t } = useTranslation(['admin', 'common']);
  const me = useMe();
  const lang = currentLanguage();
  const isOwner = hasAnyRole(me, ['COMPANY_OWNER']);
  const { nameOf } = useBranchNames();
  const queryClient = useQueryClient();
  const withStepUp = useStepUp();
  const confirm = useConfirm();
  const tags = useQuery({ queryKey: TAGS_KEY, queryFn: tagsApi.list });
  const [pkg, setPkg] = useState<ProvisioningPackage>();
  const [eventsOf, setEventsOf] = useState<NfcTag>();

  const refresh = () => void queryClient.invalidateQueries({ queryKey: TAGS_KEY });
  const toggle = useMutation({
    mutationFn: (tag: NfcTag) => (tag.status === 'ACTIVE' ? tagsApi.disable(tag.id ?? '') : tagsApi.enable(tag.id ?? '')),
    onSuccess: refresh,
  });
  const rotate = useMutation({ mutationFn: (tag: NfcTag) => withStepUp(() => tagsApi.rotate(tag.id ?? '')), onSuccess: refresh });
  const exportPkg = useMutation({
    mutationFn: (tag: NfcTag) => withStepUp(() => tagsApi.provisioningPackage(tag.id ?? '')),
    onSuccess: (data) => {
      setPkg(data);
      refresh();
    },
  });

  const askToggle = (tag: NfcTag) => {
    if (tag.status !== 'ACTIVE') {
      toggle.mutate(tag);
      return;
    }
    void confirm({ message: t('admin:tags.confirm.disable', { label: tag.label }), danger: true }).then((ok) => ok && toggle.mutate(tag));
  };
  const askRotate = (tag: NfcTag) =>
    void confirm({ message: t('admin:tags.confirm.rotate', { label: tag.label }) }).then((ok) => ok && rotate.mutate(tag));

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-semibold">{t('admin:tags.title')}</h1>
          <p className="mt-1 text-muted-foreground">{t('admin:tags.subtitle')}</p>
        </div>
        {isOwner ? <RegisterDialog onCreated={refresh} /> : null}
      </div>
      <ApiErrorAlert error={tags.error ?? toggle.error ?? rotate.error ?? exportPkg.error} />
      {tags.isPending ? <InlineLoading /> : null}
      {tags.data && tags.data.length === 0 ? (
        <Card>
          <CardContent className="py-10 text-center text-sm text-muted-foreground">{t('admin:tags.empty')}</CardContent>
        </Card>
      ) : null}
      <div className="grid gap-4 lg:grid-cols-2">
        {tags.data?.map((tag) => {
          const active = tag.status === 'ACTIVE';
          return (
            <Card key={tag.id}>
              <CardContent className="flex flex-col gap-4 pt-5 sm:pt-6">
                <div className="flex items-center gap-3">
                  <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary" aria-hidden="true">
                    <Nfc className="size-5" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="flex flex-wrap items-center gap-2 font-semibold">
                      {tag.label}
                      <Badge tone={active ? 'success' : 'danger'}>{t(`admin:tags.status.${active ? 'ACTIVE' : 'DISABLED'}`)}</Badge>
                      {tag.type === 'SIMULATED' ? <Badge>{t('admin:tags.simulated')}</Badge> : null}
                    </p>
                    <p className="font-mono text-xs text-muted-foreground">{tag.uid}</p>
                    <p className="text-sm text-muted-foreground">{nameOf(tag.branchId)}</p>
                  </div>
                </div>
                <dl className="grid grid-cols-2 gap-3 text-sm">
                  <div>
                    <dt className="text-muted-foreground">{t('admin:tags.keys')}</dt>
                    <dd className="font-medium">
                      {tag.type === 'SIMULATED'
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
                <div className="flex flex-wrap gap-2 border-t border-border pt-4">
                  <Button variant="outline" size="sm" onClick={() => setEventsOf(tag)}>
                    <History className="size-4" aria-hidden="true" />
                    {t('admin:tags.actions.events')}
                  </Button>
                  {isOwner && tag.pendingKeyVersion && !tag.pendingExported ? (
                    <Button size="sm" onClick={() => exportPkg.mutate(tag)} disabled={exportPkg.isPending}>
                      <Download className="size-4" aria-hidden="true" />
                      {t('admin:tags.actions.package')}
                    </Button>
                  ) : null}
                  {isOwner && tag.type !== 'SIMULATED' ? (
                    <Button variant="outline" size="sm" onClick={() => askRotate(tag)} disabled={rotate.isPending}>
                      <KeyRound className="size-4" aria-hidden="true" />
                      {t('admin:tags.actions.rotate')}
                    </Button>
                  ) : null}
                  {isOwner ? (
                    <Button
                      variant="ghost"
                      size="sm"
                      className={active ? 'text-destructive hover:bg-destructive/10 hover:text-destructive' : undefined}
                      onClick={() => askToggle(tag)}
                      disabled={toggle.isPending}
                    >
                      {active ? <PowerOff className="size-4" aria-hidden="true" /> : <Power className="size-4" aria-hidden="true" />}
                      {active ? t('admin:tags.actions.disable') : t('admin:tags.actions.enable')}
                    </Button>
                  ) : null}
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>
      {pkg ? <PackageDialog pkg={pkg} onClose={() => setPkg(undefined)} /> : null}
      {eventsOf ? <EventsDialog tag={eventsOf} onClose={() => setEventsOf(undefined)} /> : null}
    </div>
  );
}

function RegisterDialog({ onCreated }: { onCreated: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const { branches } = useBranchNames();
  const [open, setOpen] = useState(false);
  const [uid, setUid] = useState('');
  const [label, setLabel] = useState('');
  const [branchId, setBranchId] = useState('');
  const [invalid, setInvalid] = useState(false);
  const register = useMutation({
    mutationFn: () => tagsApi.register({ uid: uid.trim(), label: label.trim(), branchId }),
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
      <Button onClick={start}>
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
function PackageDialog({ pkg, onClose }: { pkg: ProvisioningPackage; onClose: () => void }) {
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

function EventsDialog({ tag, onClose }: { tag: NfcTag; onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const events = useQuery({ queryKey: ['tag-events', tag.id], queryFn: () => tagsApi.events(tag.id ?? '') });
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
