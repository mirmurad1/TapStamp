import type { Client } from 'openapi-fetch';

import { api, unwrap } from '@/shared/api/client';
import type { components } from '@/shared/api/schema';

/**
 * Admin API. Ä°ki "realm" eyni endpoint-lÉ™rÉ™ malikdir: ÅŸirkÉ™t domenindÉ™ iÅŸÃ§ilÉ™r ({@code /api/v1/admin}),
 * platforma domenindÉ™ platforma adminlÉ™ri ({@code /api/v1/platform}). Yollar tipli qalsÄ±n deyÉ™ hÉ™r realm Ã¼Ã§Ã¼n
 * ayrÄ±ca literal yol yazÄ±lÄ±r.
 */
export type Realm = 'STAFF' | 'PLATFORM';

type Schemas = components['schemas'];

/** SÉ™hifÉ™ parametrlÉ™ri: `page` 0-dan, `size` â€” 5, 10, 20 vÉ™ ya 50. */
export interface PageParams {
  page: number;
  size: number;
}
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
  /** 2FA aktivdirsÉ™ code (TOTP), deyilsÉ™ password (cari ÅŸifrÉ™). */
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
  /** 2FA-nÄ±n mÃ¼vÉ™qqÉ™ti sÃ¶ndÃ¼rÃ¼lmÉ™si (cari ÅŸifrÉ™ ilÉ™; sirr saxlanÄ±lÄ±r). */
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
  /** 2FA-nÄ±n tam silinmÉ™si (sirr vÉ™ bÉ™rpa kodlarÄ±). */
  removeMfa: (realm: Realm, password: string) =>
    unwrap(
      isPlatform(realm)
        ? api.POST('/api/v1/platform/me/mfa/remove', { body: { password } })
        : api.POST('/api/v1/admin/me/mfa/remove', { body: { password } }),
    ),
};

export const teamApi = {
  members: (query: PageParams) => unwrap(api.GET('/api/v1/admin/team', { params: { query } })),
  invitations: (query: PageParams) => unwrap(api.GET('/api/v1/admin/team/invitations', { params: { query } })),
  invite: (body: InviteRequest) => unwrap(api.POST('/api/v1/admin/team/invitations', { body })),
  revokeInvitation: (invitationId: string) =>
    unwrap(api.DELETE('/api/v1/admin/team/invitations/{invitationId}', { params: { path: { invitationId } } })),
  updateStatus: (userId: string, status: 'ACTIVE' | 'DISABLED') =>
    unwrap(
      api.PATCH('/api/v1/admin/team/members/{userId}/status', { params: { path: { userId } }, body: { status } }),
    ),
  resetMfa: (userId: string) =>
    unwrap(api.POST('/api/v1/admin/team/members/{userId}/mfa-reset', { params: { path: { userId } } })),
  /** YumÅŸaq silinmÉ™: hesab bazada qalÄ±r, giriÅŸ vÉ™ siyahÄ±dan Ã§Ä±xÄ±r. */
  deleteMember: (userId: string) =>
    unwrap(api.DELETE('/api/v1/admin/team/members/{userId}', { params: { path: { userId } } })),
};

export const platformApi = {
  tenants: (query: PageParams) => unwrap(api.GET('/api/v1/platform/tenants', { params: { query } })),
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

/** Ä°ÅŸÃ§i konsolu: tÉ™sdiq gÃ¶zlÉ™yÉ™n mÃ¶hÃ¼rlÉ™r (filial Ã¼zrÉ™). */
export const staffConsoleApi = {
  pendingStamps: (branchId: string, paging: PageParams) =>
    unwrap(api.GET('/api/v1/admin/staff-console/pending-stamps', { params: { query: { branchId, ...paging } } })),
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

/** Simulyatorun Ã§ipi (dev endpoint-i OpenAPI sÉ™nÉ™dindÉ™ yoxdur â€” sÉ™nÉ™d prod konfiqurasiyasÄ± ilÉ™ yaradÄ±lÄ±r). */
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

// Eyni klient (CSRF, sessiya yenilÉ™mÉ™si) â€” yalnÄ±z yol tiplÉ™ri É™l ilÉ™ verilib
const devApi = api as unknown as Client<DevPaths>;

/** NFC simulyatoru â€” yalnÄ±z dev mÃ¼hitindÉ™ mÃ¶vcuddur (prod-da 403). */
export const simulatorApi = {
  tags: () => unwrap(devApi.GET('/api/v1/dev/simulator/tags')),
  tap: (tagId: string) => unwrap(devApi.POST('/api/v1/dev/simulator/taps', { body: { tagId } })),
};

export type StampPolicy = Schemas['StampPolicyResponse'];
export type StampPolicyRequest = Schemas['StampPolicyRequest'];
export type RewardSettings = Schemas['RewardSettingsResponse'];
export type RewardSettingsRequest = Schemas['RewardSettingsRequest'];
export type RewardImpact = Schemas['RewardImpactResponse'];
export type ExistingCards = NonNullable<RewardSettingsRequest['existingCards']>;

/** LoyallÄ±q parametrlÉ™ri (yalnÄ±z sahib): mÃ¶hÃ¼r qaydasÄ± vÉ™ hÉ™diyyÉ™. */
export const settingsApi = {
  stampPolicy: () => unwrap(api.GET('/api/v1/admin/settings/stamp-policy')),
  updateStampPolicy: (body: StampPolicyRequest) => unwrap(api.PUT('/api/v1/admin/settings/stamp-policy', { body })),
  /** Yeni ÅŸirkÉ™tdÉ™ hÉ™diyyÉ™ qaydasÄ± hÉ™lÉ™ yoxdur: boÅŸ cavab null olur (react-query undefined-i xÉ™ta sayÄ±r). */
  rewardRule: async () => (await unwrap(api.GET('/api/v1/admin/settings/reward-rule'))) ?? null,
  updateRewardRule: (body: RewardSettingsRequest) => unwrap(api.PUT('/api/v1/admin/settings/reward-rule', { body })),
  rewardImpact: (stampsRequired: number) =>
    unwrap(api.GET('/api/v1/admin/settings/reward-rule/impact', { params: { query: { stampsRequired } } })),
};

export type GiftCardCheck = Schemas['GiftCardCheckResponse'];

/** Ä°ÅŸÃ§i konsolu: hÉ™diyyÉ™ kartÄ±nÄ±n yoxlanmasÄ± vÉ™ tÉ™hvili. */
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

/** Analitika (sahib vÉ™ menecer); boÅŸ filtr â€” son 30 gÃ¼n, bÃ¼tÃ¼n icazÉ™li filiallar. */
export const analyticsApi = {
  overview: (query: AnalyticsFilter) => unwrap(api.GET('/api/v1/admin/analytics/overview', { params: { query } })),
  series: (query: AnalyticsFilter & { granularity: 'DAY' | 'WEEK' }) =>
    unwrap(api.GET('/api/v1/admin/analytics/stamps-series', { params: { query } })),
  heatmap: (query: AnalyticsFilter) => unwrap(api.GET('/api/v1/admin/analytics/heatmap', { params: { query } })),
  retention: (query: AnalyticsFilter) => unwrap(api.GET('/api/v1/admin/analytics/retention', { params: { query } })),
  languages: (query: AnalyticsFilter) =>
    unwrap(api.GET('/api/v1/admin/analytics/languages', { params: { query: { branchId: query.branchId } } })),
};

export type CustomerRow = Schemas['Row'];
export type CustomerPage = Schemas['Page'];
export type TimelineItem = Schemas['TimelineItem'];
export type FraudFlag = Schemas['FraudFlag'];
export type AdjustmentResult = Schemas['AdjustmentResult'];

export interface CustomerFilter {
  q?: string;
  language?: string;
  status?: string;
  inactiveDays?: number;
  branchId?: string;
  registeredFrom?: string;
  registeredTo?: string;
}

export type CustomerSort = 'REGISTERED_AT' | 'LAST_VISIT_AT' | 'NAME' | 'TOTAL_STAMPS';

/** MÃ¼ÅŸtÉ™rilÉ™r (sahib vÉ™ menecer). Tam nÃ¶mrÉ™, bloklama vÉ™ CSV yalnÄ±z sahibÉ™ aÃ§Ä±qdÄ±r. */
export const customersApi = {
  list: (query: CustomerFilter & { sort: CustomerSort; direction: 'asc' | 'desc'; page: number; size: number }) =>
    unwrap(api.GET('/api/v1/admin/customers', { params: { query } })),
  get: (customerId: string) => unwrap(api.GET('/api/v1/admin/customers/{customerId}', { params: { path: { customerId } } })),
  timeline: (customerId: string, query: PageParams) =>
    unwrap(api.GET('/api/v1/admin/customers/{customerId}/timeline', { params: { path: { customerId }, query } })),
  adjust: (customerId: string, body: { delta: number; reason: string; branchId?: string }, idempotencyKey: string) =>
    unwrap(
      api.POST('/api/v1/admin/customers/{customerId}/stamp-adjustments', {
        params: { path: { customerId }, header: { 'Idempotency-Key': idempotencyKey } },
        body,
      }),
    ),
  updateStatus: (customerId: string, status: 'ACTIVE' | 'BLOCKED') =>
    unwrap(api.PATCH('/api/v1/admin/customers/{customerId}/status', { params: { path: { customerId } }, body: { status } })),
  fraudFlags: (customerId: string, query: PageParams) =>
    unwrap(api.GET('/api/v1/admin/customers/{customerId}/fraud-flags', { params: { path: { customerId }, query } })),
  resolveFlag: (flagId: string, note: string) =>
    unwrap(api.POST('/api/v1/admin/customers/fraud-flags/{flagId}/resolve', { params: { path: { flagId } }, body: { note } })),
  /** CSV faylÄ± (Blob); step-up tÉ™lÉ™b edir. */
  exportCsv: (query: CustomerFilter) =>
    unwrap(api.POST('/api/v1/admin/customers/export', { params: { query }, parseAs: 'blob' })) as Promise<Blob>,
};

export type NfcTag = Schemas['TagView'];
export type TapEvent = Schemas['TapEvent'];
export type ProvisioningPackage = Schemas['ProvisioningPackage'];
export type AuditEntry = Schemas['Entry'];

/** NFC Ã§iplÉ™ri ÅŸirkÉ™t panelindÉ™ yalnÄ±z baxÄ±ÅŸ Ã¼Ã§Ã¼ndÃ¼r (sahib â€” hamÄ±sÄ±, menecer â€” Ã¶z filiallarÄ±). */
export const tagsApi = {
  list: (query: PageParams) => unwrap(api.GET('/api/v1/admin/tags', { params: { query } })),
  events: (tagId: string, query: PageParams) =>
    unwrap(api.GET('/api/v1/admin/tags/{tagId}/events', { params: { path: { tagId }, query } })),
};

/** ÅžirkÉ™tin Ã§iplÉ™ri platforma panelindÉ™n: qeydiyyat, idarÉ™, say. Rotasiya vÉ™ provisioning paketi step-up tÉ™lÉ™b edir. */
export const platformTagsApi = {
  overview: (tenantId: string, query: PageParams) =>
    unwrap(api.GET('/api/v1/platform/tenants/{tenantId}/tags', { params: { path: { tenantId }, query } })),
  register: (tenantId: string, body: { uid: string; branchId: string; label: string }) =>
    unwrap(api.POST('/api/v1/platform/tenants/{tenantId}/tags', { params: { path: { tenantId } }, body })),
  disable: (tenantId: string, tagId: string) =>
    unwrap(api.POST('/api/v1/platform/tenants/{tenantId}/tags/{tagId}/disable', { params: { path: { tenantId, tagId } } })),
  enable: (tenantId: string, tagId: string) =>
    unwrap(api.POST('/api/v1/platform/tenants/{tenantId}/tags/{tagId}/enable', { params: { path: { tenantId, tagId } } })),
  rotate: (tenantId: string, tagId: string) =>
    unwrap(api.POST('/api/v1/platform/tenants/{tenantId}/tags/{tagId}/key-rotations', { params: { path: { tenantId, tagId } } })),
  provisioningPackage: (tenantId: string, tagId: string) =>
    unwrap(
      api.POST('/api/v1/platform/tenants/{tenantId}/tags/{tagId}/provisioning-package', { params: { path: { tenantId, tagId } } }),
    ),
  events: (tenantId: string, tagId: string, query: PageParams) =>
    unwrap(api.GET('/api/v1/platform/tenants/{tenantId}/tags/{tagId}/events', { params: { path: { tenantId, tagId }, query } })),
};

export const auditApi = {
  list: (query: { action?: string; from?: string; to?: string } & PageParams) =>
    unwrap(api.GET('/api/v1/admin/audit-logs', { params: { query } })),
  actions: () => unwrap(api.GET('/api/v1/admin/audit-logs/actions')),
};

export type CompanyContent = Schemas['Company'];
export type BranchSettings = Schemas['BranchView'];
export type BranchPolicy = Schemas['BranchPolicy'];
export type BusinessHours = Record<string, string[][]>;
export interface Localized {
  az: string;
  en?: string;
  ru?: string;
}

/** ÅžirkÉ™tin mÉ™zmunu, filiallar vÉ™ filial Ã¼zrÉ™ qayda (yalnÄ±z sahib). */
export const companyApi = {
  get: () => unwrap(api.GET('/api/v1/admin/settings/company')),
  update: (body: Schemas['CompanyRequest']) => unwrap(api.PUT('/api/v1/admin/settings/company', { body })),
  /** Loqo multipart ilÉ™ gÃ¶ndÉ™rilir (fayl brauzerdÉ™ Ã¶lÃ§Ã¼ vÉ™ nÃ¶v Ã¼zrÉ™ É™vvÉ™lcÉ™dÉ™n yoxlanÄ±lÄ±r). */
  uploadLogo: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return unwrap(api.PUT('/api/v1/admin/settings/company/logo', { body: { file: '' }, bodySerializer: () => form }));
  },
  removeLogo: () => unwrap(api.DELETE('/api/v1/admin/settings/company/logo')),
  branches: () => unwrap(api.GET('/api/v1/admin/settings/branches')),
  createBranch: (body: Schemas['BranchRequest']) => unwrap(api.POST('/api/v1/admin/settings/branches', { body })),
  updateBranch: (branchId: string, body: Schemas['BranchRequest']) =>
    unwrap(api.PATCH('/api/v1/admin/settings/branches/{branchId}', { params: { path: { branchId } }, body })),
  branchPolicy: (branchId: string) =>
    unwrap(api.GET('/api/v1/admin/settings/branches/{branchId}/stamp-policy', { params: { path: { branchId } } })),
  overridePolicy: (branchId: string, body: Schemas['BranchPolicyRequest']) =>
    unwrap(api.PUT('/api/v1/admin/settings/branches/{branchId}/stamp-policy', { params: { path: { branchId } }, body })),
  resetPolicy: (branchId: string) =>
    unwrap(api.DELETE('/api/v1/admin/settings/branches/{branchId}/stamp-policy', { params: { path: { branchId } } })),
};

export type TenantDomain = Schemas['Domain'];
export type PlatformAuditEntry = Schemas['PlatformAuditEntry'];

/** Platforma: ÅŸirkÉ™tin domenlÉ™ri (É™sas domen vÉ™ silmÉ™ step-up tÉ™lÉ™b edir) vÉ™ platforma audit jurnalÄ±. */
export const platformDomainsApi = {
  list: (tenantId: string) => unwrap(api.GET('/api/v1/platform/tenants/{tenantId}/domains', { params: { path: { tenantId } } })),
  add: (tenantId: string, hostname: string) =>
    unwrap(api.POST('/api/v1/platform/tenants/{tenantId}/domains', { params: { path: { tenantId } }, body: { hostname } })),
  makePrimary: (tenantId: string, domainId: string) =>
    unwrap(
      api.POST('/api/v1/platform/tenants/{tenantId}/domains/{domainId}/primary', { params: { path: { tenantId, domainId } } }),
    ),
  remove: (tenantId: string, domainId: string) =>
    unwrap(api.DELETE('/api/v1/platform/tenants/{tenantId}/domains/{domainId}', { params: { path: { tenantId, domainId } } })),
};

export const platformAuditApi = {
  list: (query: { action?: string } & PageParams) => unwrap(api.GET('/api/v1/platform/audit-logs', { params: { query } })),
};

export type SmsUsageReport = Schemas['SmsUsageReport'];

export type ReminderSettings = Schemas['ReminderSettings'];

/** Avtomatik xatırlatmalar (şirkət sahibi): parametrlər və statistika. */
export const remindersApi = {
  settings: () => unwrap(api.GET('/api/v1/admin/reminders/settings')),
  updateSettings: (body: Schemas['ReminderSettingsRequest']) => unwrap(api.PUT('/api/v1/admin/reminders/settings', { body })),
  stats: () => unwrap(api.GET('/api/v1/admin/reminders/stats')),
};

export type ReferralSettings = Schemas['ReferralSettings'];
export type ReferralActivity = 'ALL' | 'NONE' | 'ONCE' | 'RETURNED';

/** Dost dÉ™vÉ™ti (ÅŸirkÉ™t sahibi): parametrlÉ™r, xÃ¼lasÉ™ vÉ™ dÉ™vÉ™tlÉ™r. */
export const referralsApi = {
  settings: () => unwrap(api.GET('/api/v1/admin/referrals/settings')),
  updateSettings: (body: Schemas['ReferralSettingsRequest']) => unwrap(api.PUT('/api/v1/admin/referrals/settings', { body })),
  summary: () => unwrap(api.GET('/api/v1/admin/referrals/summary')),
  /** {@code activity}: ALL, NONE (gəlməyib), ONCE (bir gün), RETURNED (2+ gün). */
  list: (query: PageParams & { activity: ReferralActivity }) => unwrap(api.GET('/api/v1/admin/referrals', { params: { query } })),
  /** İdarəetmə paneli: dövrün statistikası və ən çox dost gətirənlər. */
  dashboard: (query: AnalyticsFilter) => unwrap(api.GET('/api/v1/admin/referrals/dashboard', { params: { query } })),
};

export type CampaignRequest = Schemas['CampaignRequest'];
export type CampaignView = Schemas['CampaignView'];
export type CampaignPreview = Schemas['Preview'];
export type PlatformCampaign = Schemas['PlatformCampaignView'];

/** Kampaniya SMS-lÉ™ri (ÅŸirkÉ™t sahibi). */
export const campaignsApi = {
  preview: (body: CampaignRequest) => unwrap(api.POST('/api/v1/admin/campaigns/preview', { body })),
  create: (body: CampaignRequest) => unwrap(api.POST('/api/v1/admin/campaigns', { body })),
  list: (type: 'SMS' | 'STAMPS', paging: PageParams) =>
    unwrap(api.GET('/api/v1/admin/campaigns', { params: { query: { type, ...paging } } })),
  /** SMS-i gÃ¶ndÉ™rilmÉ™yÉ™n alÄ±cÄ±lar (ad, nÃ¶mrÉ™, sÉ™bÉ™b). */
  failures: (campaignId: string, query: PageParams) =>
    unwrap(api.GET('/api/v1/admin/campaigns/{campaignId}/failures', { params: { path: { campaignId }, query } })),
  /** YalnÄ±z tÉ™sdiq gÃ¶zlÉ™yÉ™n kampaniya. */
  update: (campaignId: string, body: CampaignRequest) =>
    unwrap(api.PUT('/api/v1/admin/campaigns/{campaignId}', { params: { path: { campaignId } }, body })),
  cancel: (campaignId: string) =>
    unwrap(api.POST('/api/v1/admin/campaigns/{campaignId}/cancel', { params: { path: { campaignId } } })),
};

/** Kampaniyalar (platforma): tÉ™sdiq gÃ¶zlÉ™yÉ™nlÉ™r vÉ™ qÉ™rar. */
export const platformCampaignsApi = {
  list: (status: 'PENDING' | 'ALL', paging: PageParams) =>
    unwrap(api.GET('/api/v1/platform/campaigns', { params: { query: { status, ...paging } } })),
  approve: (tenantId: string, campaignId: string) =>
    unwrap(api.POST('/api/v1/platform/campaigns/{tenantId}/{campaignId}/approve', { params: { path: { tenantId, campaignId } } })),
  /** Hesab xÉ™tasÄ±na gÃ¶rÉ™ dayandÄ±rÄ±lmÄ±ÅŸ kampaniyanÄ± davam etdirmÉ™k. */
  resume: (tenantId: string, campaignId: string) =>
    unwrap(api.POST('/api/v1/platform/campaigns/{tenantId}/{campaignId}/resume', { params: { path: { tenantId, campaignId } } })),
  reject: (tenantId: string, campaignId: string, reason: string) =>
    unwrap(
      api.POST('/api/v1/platform/campaigns/{tenantId}/{campaignId}/reject', {
        params: { path: { tenantId, campaignId } },
        body: { reason },
      }),
    ),
};

export type TenantSmsLimit = Schemas['TenantSmsLimit'];

export const platformSmsApi = {
  /** Şirkətin gündəlik SMS limiti (yeni nömrələrə giriş kodları) və bu günkü istifadə. */
  tenantLimit: (tenantId: string) =>
    unwrap(api.GET('/api/v1/platform/tenants/{tenantId}/sms-limit', { params: { path: { tenantId } } })),
  /** {@code null} — standart dəyərə qaytarmaq. */
  updateTenantLimit: (tenantId: string, limit: number | null) =>
    unwrap(
      api.PUT('/api/v1/platform/tenants/{tenantId}/sms-limit', {
        params: { path: { tenantId } },
        body: { limit: limit ?? undefined },
      }),
    ),
  /** Platformanın gündəlik büdcəsi və bu günkü istifadə. */
  budget: () => unwrap(api.GET('/api/v1/platform/sms-usage/budget')),
  /** GÃ¼nlÉ™r daxil (YYYY-MM-DD, BakÄ± vaxtÄ±). */
  usage: (from: string, to: string) => unwrap(api.GET('/api/v1/platform/sms-usage', { params: { query: { from, to } } })),
  /** SMS axtarÄ±ÅŸÄ±: status (SENT/FAILED) vÉ™ nÃ¶mrÉ™ (vÉ™ ya hissÉ™si) istÉ™yÉ™ baÄŸlÄ±; 20-lik sÉ™hifÉ™lÉ™r (page 0-dan). */
  messages: (query: { from: string; to: string; status?: 'SENT' | 'FAILED'; phone?: string } & PageParams) =>
    unwrap(api.GET('/api/v1/platform/sms-usage/messages', { params: { query } })),
};

export type TenantPlan = Schemas['TenantPlanResponse'];
export type PlanName = Schemas['TenantPlanRequest']['plan'];

/** Platforma paneli: şirkətin paketi və sınaq müddəti (hər paket üçün bir dəfə). */
export const platformPlanApi = {
  get: (tenantId: string) =>
    unwrap(api.GET('/api/v1/platform/tenants/{tenantId}/plan', { params: { path: { tenantId } } })),
  update: (tenantId: string, body: Schemas['TenantPlanRequest']) =>
    unwrap(api.PUT('/api/v1/platform/tenants/{tenantId}/plan', { params: { path: { tenantId } }, body })),
};
