import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { UserPlus } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';

import { SUPPORTED_LANGUAGES } from '@/shared/i18n/languages';
import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatDateTime } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { useConfirm } from '@/shared/ui/ConfirmProvider';
import { Input, Select } from '@/shared/ui/input';

import { contextApi, teamApi, type Member } from '../../api/adminApi';
import { useMe } from '../../auth/AdminAuthProvider';
import { useStepUp } from '../../auth/StepUpProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { RoleLabel } from '../../components/RoleLabel';
import { hasAnyRole } from '../../layout/navigation';
import { emailField, useValidationMessage } from '../../lib/forms';

const TEAM_KEY = ['team'] as const;
const INVITATIONS_KEY = ['team-invitations'] as const;

function useBranchNames() {
  const company = useQuery({ queryKey: ['public-company'], queryFn: contextApi.company });
  const lang = currentLanguage();
  const branches = (company.data?.branches ?? []).map((branch) => ({ id: branch.id ?? '', name: resolveText(branch.name, lang) }));
  const nameOf = (id: string | undefined) => branches.find((branch) => branch.id === id)?.name;
  return { branches, nameOf };
}

export function TeamPage() {
  const { t } = useTranslation('admin');
  const [notice, setNotice] = useState<string>();

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-semibold">{t('team.title')}</h1>
          <p className="mt-1 text-muted-foreground">{t('team.subtitle')}</p>
        </div>
        <InviteDialog onSent={(email) => setNotice(t('team.invite.sent', { email }))} />
      </div>
      {notice ? <Alert tone="success">{notice}</Alert> : null}
      <MembersCard onNotice={setNotice} />
      <InvitationsCard onNotice={setNotice} />
    </div>
  );
}

function MembersCard({ onNotice }: { onNotice: (message: string) => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const me = useMe();
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const withStepUp = useStepUp();
  const { nameOf } = useBranchNames();
  const members = useQuery({ queryKey: TEAM_KEY, queryFn: teamApi.members });
  const isOwner = hasAnyRole(me, ['COMPANY_OWNER']);

  const status = useMutation({
    mutationFn: ({ member, next }: { member: Member; next: 'ACTIVE' | 'DISABLED' }) => teamApi.updateStatus(member.id ?? '', next),
    onSuccess: (_, { next }) => {
      onNotice(next === 'DISABLED' ? t('admin:team.done.disabled') : t('admin:team.done.enabled'));
      void queryClient.invalidateQueries({ queryKey: TEAM_KEY });
    },
  });
  const resetMfa = useMutation({
    mutationFn: (member: Member) => withStepUp(() => teamApi.resetMfa(member.id ?? '')),
    onSuccess: () => {
      onNotice(t('admin:team.done.mfaReset'));
      void queryClient.invalidateQueries({ queryKey: TEAM_KEY });
    },
  });

  const confirm = useConfirm();
  const confirmAndRun = (message: string, action: () => void) => {
    void confirm({ message, danger: true }).then((ok) => ok && action());
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('admin:team.members')}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <ApiErrorAlert error={members.error ?? status.error ?? resetMfa.error} />
        {members.isPending ? <InlineLoading /> : null}
        {members.data && members.data.length === 0 ? <p className="text-sm text-muted-foreground">{t('admin:team.empty')}</p> : null}
        {members.data && members.data.length > 0 ? (
          <div className="-mx-5 overflow-x-auto sm:-mx-6">
            <table className="w-full min-w-[44rem] text-left text-sm">
              <thead className="border-b border-border text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('admin:team.columns.name')}</th>
                  <th scope="col" className="px-3 py-2 font-medium">{t('admin:team.columns.role')}</th>
                  <th scope="col" className="px-3 py-2 font-medium">{t('admin:team.columns.status')}</th>
                  <th scope="col" className="px-3 py-2 font-medium">{t('admin:team.columns.twoFactor')}</th>
                  <th scope="col" className="px-3 py-2 font-medium">{t('admin:team.columns.lastLogin')}</th>
                  <th scope="col" className="px-5 py-2 text-right font-medium sm:px-6">{t('admin:team.columns.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {members.data.map((member) => {
                  const self = member.id === me.id;
                  const name = member.displayName || member.email || '';
                  return (
                    <tr key={member.id} className="align-top">
                      <td className="px-5 py-3 sm:px-6">
                        <p className="font-medium">
                          {name}
                          {self ? <Badge className="ms-2">{t('admin:team.you')}</Badge> : null}
                        </p>
                        <p className="break-all text-muted-foreground">{member.email}</p>
                      </td>
                      <td className="px-3 py-3">
                        {(member.roles ?? []).map((grant) => (
                          <p key={`${grant.role}-${grant.branchId ?? 'all'}`}>
                            {grant.role ? <RoleLabel role={grant.role} /> : null}
                            <span className="text-muted-foreground">
                              {' · '}
                              {grant.branchId ? nameOf(grant.branchId) : t('admin:team.allBranches')}
                            </span>
                          </p>
                        ))}
                      </td>
                      <td className="px-3 py-3">
                        <Badge tone={member.status === 'ACTIVE' ? 'success' : 'danger'}>
                          {member.status === 'DISABLED' ? t('admin:team.status.DISABLED') : t('admin:team.status.ACTIVE')}
                        </Badge>
                      </td>
                      <td className="px-3 py-3">
                        {member.totpEnabled ? t('admin:team.twoFactorOn') : t('admin:team.twoFactorOff')}
                      </td>
                      <td className="px-3 py-3 text-muted-foreground">
                        {member.lastLoginAt ? formatDateTime(member.lastLoginAt, lang) : t('admin:team.never')}
                      </td>
                      <td className="px-5 py-3 text-right sm:px-6">
                        {!self ? (
                          <div className="flex flex-wrap justify-end gap-2">
                            {member.status === 'ACTIVE' ? (
                              <Button
                                variant="outline"
                                size="sm"
                                disabled={status.isPending}
                                onClick={() =>
                                  confirmAndRun(t('admin:team.confirm.disable', { name }), () =>
                                    status.mutate({ member, next: 'DISABLED' }),
                                  )
                                }
                              >
                                {t('admin:team.actions.disable')}
                              </Button>
                            ) : (
                              <Button
                                variant="outline"
                                size="sm"
                                disabled={status.isPending}
                                onClick={() => status.mutate({ member, next: 'ACTIVE' })}
                              >
                                {t('admin:team.actions.enable')}
                              </Button>
                            )}
                            {isOwner && member.totpEnabled ? (
                              <Button
                                variant="ghost"
                                size="sm"
                                disabled={resetMfa.isPending}
                                onClick={() =>
                                  confirmAndRun(t('admin:team.confirm.resetMfa', { name }), () => resetMfa.mutate(member))
                                }
                              >
                                {t('admin:team.actions.resetMfa')}
                              </Button>
                            ) : null}
                          </div>
                        ) : null}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

function InvitationsCard({ onNotice }: { onNotice: (message: string) => void }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const { nameOf } = useBranchNames();
  const invitations = useQuery({ queryKey: INVITATIONS_KEY, queryFn: teamApi.invitations });
  const revoke = useMutation({
    mutationFn: (id: string) => teamApi.revokeInvitation(id),
    onSuccess: () => {
      onNotice(t('team.done.revoked'));
      void queryClient.invalidateQueries({ queryKey: INVITATIONS_KEY });
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('team.invitations')}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <ApiErrorAlert error={invitations.error ?? revoke.error} />
        {invitations.isPending ? <InlineLoading /> : null}
        {invitations.data?.length === 0 ? <p className="text-sm text-muted-foreground">{t('team.noInvitations')}</p> : null}
        <ul className="flex flex-col divide-y divide-border">
          {(invitations.data ?? []).map((invitation) => (
            <li key={invitation.id} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
              <div className="min-w-0">
                <p className="break-all font-medium">{invitation.email}</p>
                <p className="text-muted-foreground">
                  {invitation.role ? <RoleLabel role={invitation.role} /> : null}
                  {' · '}
                  {invitation.branchId ? nameOf(invitation.branchId) : t('team.allBranches')}
                  {invitation.expiresAt ? ` · ${t('team.columns.expires')}: ${formatDateTime(invitation.expiresAt, lang)}` : null}
                </p>
              </div>
              <Button variant="outline" size="sm" disabled={revoke.isPending} onClick={() => revoke.mutate(invitation.id ?? '')}>
                {t('team.actions.revokeInvitation')}
              </Button>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}

const ROLES = ['STAFF', 'BRANCH_MANAGER', 'COMPANY_OWNER'] as const;

const inviteSchema = z
  .object({
    email: emailField,
    role: z.enum(ROLES),
    branchId: z.string(),
    language: z.enum(SUPPORTED_LANGUAGES),
  })
  .refine((values) => values.role === 'COMPANY_OWNER' || values.branchId !== '', {
    path: ['branchId'],
    message: 'required',
  });
type InviteValues = z.infer<typeof inviteSchema>;

function InviteDialog({ onSent }: { onSent: (email: string) => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const validation = useValidationMessage();
  const me = useMe();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const { branches } = useBranchNames();
  const isOwner = hasAnyRole(me, ['COMPANY_OWNER']);
  const managed = new Set((me.roles ?? []).filter((grant) => grant.role === 'BRANCH_MANAGER').map((grant) => grant.branchId));
  const allowedRoles = isOwner ? ROLES : (['STAFF'] as const);
  const allowedBranches = isOwner ? branches : branches.filter((branch) => managed.has(branch.id));

  const form = useForm<InviteValues>({
    resolver: zodResolver(inviteSchema),
    defaultValues: { email: '', role: 'STAFF', branchId: allowedBranches[0]?.id ?? '', language: currentLanguage() },
  });
  const role = form.watch('role');
  const invite = useMutation({
    mutationFn: (values: InviteValues) =>
      teamApi.invite({
        email: values.email,
        role: values.role,
        branchId: values.role === 'COMPANY_OWNER' ? undefined : values.branchId,
        language: values.language,
      }),
    onSuccess: (invitation) => {
      setOpen(false);
      form.reset();
      onSent(invitation.email ?? '');
      void queryClient.invalidateQueries({ queryKey: INVITATIONS_KEY });
    },
  });

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) {
          invite.reset();
          form.reset({ email: '', role: 'STAFF', branchId: allowedBranches[0]?.id ?? '', language: currentLanguage() });
        }
      }}
    >
      <DialogTrigger asChild>
        <Button>
          <UserPlus aria-hidden="true" />
          {t('admin:team.invite.open')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('admin:team.invite.title')}</DialogTitle>
          <DialogDescription>{t('admin:team.invite.description')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit((values) => invite.mutate(values))} className="flex flex-col gap-4" noValidate>
          <ApiErrorAlert error={invite.error} />
          <Field id="invite-email" label={t('admin:team.invite.email')} error={validation(form.formState.errors.email?.message)}>
            <Input {...form.register('email')} type="email" inputMode="email" autoComplete="off" />
          </Field>
          <Field id="invite-role" label={t('admin:team.invite.role')}>
            <Select {...form.register('role')}>
              {allowedRoles.map((value) => (
                <option key={value} value={value}>
                  {t(`admin:roles.${value}`)}
                </option>
              ))}
            </Select>
          </Field>
          {role !== 'COMPANY_OWNER' ? (
            <Field id="invite-branch" label={t('admin:team.invite.branch')} error={validation(form.formState.errors.branchId?.message)}>
              <Select {...form.register('branchId')}>
                {allowedBranches.map((branch) => (
                  <option key={branch.id} value={branch.id}>
                    {branch.name}
                  </option>
                ))}
              </Select>
            </Field>
          ) : null}
          <Field id="invite-language" label={t('admin:team.invite.language')}>
            <Select {...form.register('language')}>
              {SUPPORTED_LANGUAGES.map((lang) => (
                <option key={lang} value={lang} lang={lang}>
                  {t(`common:language.${lang}`)}
                </option>
              ))}
            </Select>
          </Field>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              {t('common:actions.cancel')}
            </Button>
            <Button type="submit" disabled={invite.isPending}>
              {t('admin:team.invite.submit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
