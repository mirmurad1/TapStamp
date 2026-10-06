import { api, unwrap } from '@/shared/api/client';
import type { components } from '@/shared/api/schema';

/**
 * Admin API. İki "realm" eyni endpoint-lərə malikdir: şirkət domenində işçilər ({@code /api/v1/admin}),
 * platforma domenində platforma adminləri ({@code /api/v1/platform}). Yollar tipli qalsın deyə hər realm üçün
 * ayrıca literal yol yazılır.
 */
export type Realm = 'STAFF' | 'PLATFORM';

type Schemas = components['schemas'];
export type LoginStatus = NonNullable<Schemas['LoginResponse']['status']>;
export type SessionState = 'NONE' | 'PENDING_MFA' | 'MFA_ENROLLMENT' | 'ACTIVE';
export type Me = Schemas['MeResponse'];
export type AdminSession = Schemas['SessionResponse'];
export type Member = Schemas['MemberResponse'];
export type Invitation = Schemas['InvitationResponse'];
export type InviteRequest = Schemas['InviteRequest'];
export type InvitationDetails = Schemas['InvitationDetailsResponse'];
export type Tenant = Schemas['TenantResponse'];
export type TenantOwner = Schemas['OwnerResponse'];
export type TenantStats = Schemas['StatsResponse'];
export type ProvisionRequest = Schemas['ProvisionRequest'];
export type Branch = Schemas['BranchResponse'];
export type ContextScope = NonNullable<Schemas['ContextResponse']['scope']>;

const isPlatform = (realm: Realm) => realm === 'PLATFORM';

export const contextApi = {
  context: () => unwrap(api.GET('/api/v1/context')),
  company: () => unwrap(api.GET('/api/v1/public/company')),
};

export const authApi = {
  session: async (realm: Realm): Promise<SessionState> => {
    const response = await unwrap(
      isPlatform(realm) ? api.GET('/api/v1/platform/auth/session') : api.GET('/api/v1/admin/auth/session'),
    );
    return (response.state ?? 'NONE') as SessionState;
  },
  login: (realm: Realm, body: Schemas['LoginRequest']) =>
    unwrap(
      isPlatform(realm)
        ? api.POST('/api/v1/platform/auth/login', { body })
        : api.POST('/api/v1/admin/auth/login', { body }),
    ),
  verifyMfa: (realm: Realm, code: string) =>
    unwrap(
      isPlatform(realm)
        ? api.POST('/api/v1/platform/auth/mfa/verify', { body: { code } })
        : api.POST('/api/v1/admin/auth/mfa/verify', { body: { code } }),
    ),
  startEnrollment: (realm: Realm) =>
    unwrap(
      isPlatform(realm) ? api.POST('/api/v1/platform/auth/mfa/enrollment') : api.POST('/api/v1/admin/auth/mfa/enrollment'),
    ),
  confirmEnrollment: (realm: Realm, code: string) =>
    unwrap(
      isPlatform(realm)
        ? api.POST('/api/v1/platform/auth/mfa/enrollment/confirm', { body: { code } })
        : api.POST('/api/v1/admin/auth/mfa/enrollment/confirm', { body: { code } }),
    ),
  stepUp: (realm: Realm, code: string) =>
    unwrap(
      isPlatform(realm)
        ? api.POST('/api/v1/platform/auth/step-up', { body: { code } })
        : api.POST('/api/v1/admin/auth/step-up', { body: { code } }),
    ),
  logout: (realm: Realm) =>
    unwrap(isPlatform(realm) ? api.POST('/api/v1/platform/auth/logout') : api.POST('/api/v1/admin/auth/logout')),
  forgotPassword: (realm: Realm, email: string) =>
    unwrap(
      isPlatform(realm)
        ? api.POST('/api/v1/platform/auth/password/forgot', { body: { email } })
        : api.POST('/api/v1/admin/auth/password/forgot', { body: { email } }),
    ),
  resetPassword: (realm: Realm, body: Schemas['PasswordResetRequest']) =>
    unwrap(
      isPlatform(realm)
        ? api.POST('/api/v1/platform/auth/password/reset', { body })
        : api.POST('/api/v1/admin/auth/password/reset', { body }),
    ),
  inspectInvitation: (token: string) =>
    unwrap(api.POST('/api/v1/admin/auth/invitations/inspect', { body: { token } })),
  acceptInvitation: (body: Schemas['AcceptInvitationRequest']) =>
    unwrap(api.POST('/api/v1/admin/auth/invitations/accept', { body })),
};

export const accountApi = {
  me: (realm: Realm) => unwrap(isPlatform(realm) ? api.GET('/api/v1/platform/me') : api.GET('/api/v1/admin/me')),
  updateMe: (realm: Realm, body: Schemas['UpdateProfileRequest']) =>
    unwrap(isPlatform(realm) ? api.PATCH('/api/v1/platform/me', { body }) : api.PATCH('/api/v1/admin/me', { body })),
  changePassword: (realm: Realm, body: Schemas['ChangePasswordRequest']) =>
    unwrap(
      isPlatform(realm)
        ? api.POST('/api/v1/platform/me/password', { body })
        : api.POST('/api/v1/admin/me/password', { body }),
    ),
  sessions: (realm: Realm) =>
    unwrap(isPlatform(realm) ? api.GET('/api/v1/platform/me/sessions') : api.GET('/api/v1/admin/me/sessions')),
  revokeSession: (realm: Realm, sessionId: string) => {
    const params = { path: { sessionId } };
    return unwrap(
      isPlatform(realm)
        ? api.DELETE('/api/v1/platform/me/sessions/{sessionId}', { params })
        : api.DELETE('/api/v1/admin/me/sessions/{sessionId}', { params }),
    );
  },
  regenerateRecoveryCodes: (realm: Realm) =>
    unwrap(
      isPlatform(realm)
        ? api.POST('/api/v1/platform/me/mfa/recovery-codes')
        : api.POST('/api/v1/admin/me/mfa/recovery-codes'),
    ),
};

export const teamApi = {
  members: () => unwrap(api.GET('/api/v1/admin/team')),
  invitations: () => unwrap(api.GET('/api/v1/admin/team/invitations')),
  invite: (body: InviteRequest) => unwrap(api.POST('/api/v1/admin/team/invitations', { body })),
  revokeInvitation: (invitationId: string) =>
    unwrap(api.DELETE('/api/v1/admin/team/invitations/{invitationId}', { params: { path: { invitationId } } })),
  updateStatus: (userId: string, status: 'ACTIVE' | 'DISABLED') =>
    unwrap(
      api.PATCH('/api/v1/admin/team/members/{userId}/status', { params: { path: { userId } }, body: { status } }),
    ),
  resetMfa: (userId: string) =>
    unwrap(api.POST('/api/v1/admin/team/members/{userId}/mfa-reset', { params: { path: { userId } } })),
};

export const platformApi = {
  tenants: () => unwrap(api.GET('/api/v1/platform/tenants')),
  tenant: (tenantId: string) => unwrap(api.GET('/api/v1/platform/tenants/{tenantId}', { params: { path: { tenantId } } })),
  provision: (body: ProvisionRequest) => unwrap(api.POST('/api/v1/platform/tenants', { body })),
  updateStatus: (tenantId: string, status: 'ACTIVE' | 'SUSPENDED') =>
    unwrap(api.PATCH('/api/v1/platform/tenants/{tenantId}/status', { params: { path: { tenantId } }, body: { status } })),
  stats: (tenantId: string) =>
    unwrap(api.GET('/api/v1/platform/tenants/{tenantId}/stats', { params: { path: { tenantId } } })),
  owners: (tenantId: string) =>
    unwrap(api.GET('/api/v1/platform/tenants/{tenantId}/owners', { params: { path: { tenantId } } })),
  inviteOwner: (tenantId: string, body: Schemas['OwnerInvitationRequest']) =>
    unwrap(api.POST('/api/v1/platform/tenants/{tenantId}/owner-invitations', { params: { path: { tenantId } }, body })),
  resetOwnerMfa: (tenantId: string, userId: string) =>
    unwrap(
      api.POST('/api/v1/platform/tenants/{tenantId}/owners/{userId}/mfa-reset', { params: { path: { tenantId, userId } } }),
    ),
};
