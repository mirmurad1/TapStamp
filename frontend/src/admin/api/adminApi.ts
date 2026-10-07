import type { Client } from 'openapi-fetch';

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
  /** 2FA aktivdirsə code (TOTP), deyilsə password (cari şifrə). */
  stepUp: (realm: Realm, body: { code?: string; password?: string }) =>
    unwrap(
      isPlatform(realm)
        ? api.POST('/api/v1/platform/auth/step-up', { body })
        : api.POST('/api/v1/admin/auth/step-up', { body }),
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
  /** 2FA-nın müvəqqəti söndürülməsi (cari şifrə ilə; sirr saxlanılır). */
  pauseMfa: (realm: Realm, password: string) =>
    unwrap(
      isPlatform(realm)
        ? api.POST('/api/v1/platform/me/mfa/pause', { body: { password } })
        : api.POST('/api/v1/admin/me/mfa/pause', { body: { password } }),
    ),
  resumeMfa: (realm: Realm, code: string) =>
    unwrap(
      isPlatform(realm)
        ? api.POST('/api/v1/platform/me/mfa/resume', { body: { code } })
        : api.POST('/api/v1/admin/me/mfa/resume', { body: { code } }),
    ),
  /** 2FA-nın tam silinməsi (sirr və bərpa kodları). */
  removeMfa: (realm: Realm, password: string) =>
    unwrap(
      isPlatform(realm)
        ? api.POST('/api/v1/platform/me/mfa/remove', { body: { password } })
        : api.POST('/api/v1/admin/me/mfa/remove', { body: { password } }),
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
  /** Yumşaq silinmə: hesab bazada qalır, giriş və siyahıdan çıxır. */
  deleteMember: (userId: string) =>
    unwrap(api.DELETE('/api/v1/admin/team/members/{userId}', { params: { path: { userId } } })),
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

export type PendingStamp = Schemas['PendingStampResponse'];

/** İşçi konsolu: təsdiq gözləyən möhürlər (filial üzrə). */
export const staffConsoleApi = {
  pendingStamps: (branchId: string) =>
    unwrap(api.GET('/api/v1/admin/staff-console/pending-stamps', { params: { query: { branchId } } })),
  confirmStamp: (stampId: string) =>
    unwrap(
      api.POST('/api/v1/admin/staff-console/pending-stamps/{stampId}/confirm', { params: { path: { stampId } } }),
    ),
  rejectStamp: (stampId: string, reason: string) =>
    unwrap(
      api.POST('/api/v1/admin/staff-console/pending-stamps/{stampId}/reject', {
        params: { path: { stampId } },
        body: { reason },
      }),
    ),
};

/** Simulyatorun çipi (dev endpoint-i OpenAPI sənədində yoxdur — sənəd prod konfiqurasiyası ilə yaradılır). */
export interface SimulatedTag {
  id: string;
  uid: string;
  label: string;
  type: 'NTAG424_SUN' | 'SIMULATED';
  active: boolean;
  lastCounter: number;
  branchId: string;
  branchName?: Schemas['LocalizedText'];
}

export interface SimulatedTap {
  path: string;
  counter: number;
}

interface DevPaths {
  '/api/v1/dev/simulator/tags': {
    get: { responses: { 200: { content: { 'application/json': SimulatedTag[] } } } };
  };
  '/api/v1/dev/simulator/taps': {
    post: {
      requestBody: { content: { 'application/json': { tagId: string } } };
      responses: { 200: { content: { 'application/json': SimulatedTap } } };
    };
  };
}

// Eyni klient (CSRF, sessiya yeniləməsi) — yalnız yol tipləri əl ilə verilib
const devApi = api as unknown as Client<DevPaths>;

/** NFC simulyatoru — yalnız dev mühitində mövcuddur (prod-da 403). */
export const simulatorApi = {
  tags: () => unwrap(devApi.GET('/api/v1/dev/simulator/tags')),
  tap: (tagId: string) => unwrap(devApi.POST('/api/v1/dev/simulator/taps', { body: { tagId } })),
};

export type StampPolicy = Schemas['StampPolicyResponse'];
export type StampPolicyRequest = Schemas['StampPolicyRequest'];
export type RewardSettings = Schemas['RewardSettingsResponse'];
export type RewardSettingsRequest = Schemas['RewardSettingsRequest'];

/** Loyallıq parametrləri (yalnız sahib): möhür qaydası və hədiyyə. */
export const settingsApi = {
  stampPolicy: () => unwrap(api.GET('/api/v1/admin/settings/stamp-policy')),
  updateStampPolicy: (body: StampPolicyRequest) => unwrap(api.PUT('/api/v1/admin/settings/stamp-policy', { body })),
  rewardRule: () => unwrap(api.GET('/api/v1/admin/settings/reward-rule')),
  updateRewardRule: (body: RewardSettingsRequest) => unwrap(api.PUT('/api/v1/admin/settings/reward-rule', { body })),
};

export type GiftCardCheck = Schemas['GiftCardCheckResponse'];

/** İşçi konsolu: hədiyyə kartının yoxlanması və təhvili. */
export const giftCardConsoleApi = {
  check: (code: string) => unwrap(api.POST('/api/v1/admin/staff-console/gift-cards/check', { body: { code } })),
  redeem: (code: string, branchId: string) =>
    unwrap(api.POST('/api/v1/admin/staff-console/gift-cards/redeem', { body: { code, branchId } })),
  recent: (branchId: string) =>
    unwrap(api.GET('/api/v1/admin/staff-console/gift-cards/recent', { params: { query: { branchId } } })),
};

export interface AnalyticsFilter {
  from?: string;
  to?: string;
  branchId?: string;
}

export type AnalyticsOverview = Schemas['Overview'];
export type SeriesPoint = Schemas['SeriesPoint'];
export type HeatCell = Schemas['HeatCell'];
export type Retention = Schemas['Retention'];
export type LanguageShare = Schemas['LanguageShare'];

/** Analitika (sahib və menecer); boş filtr — son 30 gün, bütün icazəli filiallar. */
export const analyticsApi = {
  overview: (query: AnalyticsFilter) => unwrap(api.GET('/api/v1/admin/analytics/overview', { params: { query } })),
  series: (query: AnalyticsFilter & { granularity: 'DAY' | 'WEEK' }) =>
    unwrap(api.GET('/api/v1/admin/analytics/stamps-series', { params: { query } })),
  heatmap: (query: AnalyticsFilter) => unwrap(api.GET('/api/v1/admin/analytics/heatmap', { params: { query } })),
  retention: (query: AnalyticsFilter) => unwrap(api.GET('/api/v1/admin/analytics/retention', { params: { query } })),
  languages: (query: AnalyticsFilter) =>
    unwrap(api.GET('/api/v1/admin/analytics/languages', { params: { query: { branchId: query.branchId } } })),
};
