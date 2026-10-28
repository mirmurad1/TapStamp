import { api, unwrap } from '@/shared/api/client';
import type { components } from '@/shared/api/schema';

type Schemas = components['schemas'];

export type Me = Schemas['CustomerMeResponse'];
export type Device = Schemas['DeviceResponse'];
export type Company = Schemas['PublicCompanyResponse'];
export type LegalSummary = Schemas['LegalDocumentSummary'];
export type LegalDocument = Schemas['LegalDocumentResponse'];
export type OtpChallenge = Schemas['OtpChallengeResponse'];
export type OtpVerifyResult = Schemas['OtpVerifyResponse'];
export type DocumentAcceptance = Schemas['DocumentAcceptance'];
export type TapStatus = Schemas['TapStatusResponse'];
export type ClaimResult = Schemas['ClaimResponse'];
export type GiftCard = Schemas['GiftCardResponse'];
export type CustomerSessionState = 'ACTIVE' | 'REFRESH_REQUIRED' | 'NONE';

/** Hüquqi sənədlərin URL adları (/legal/privacy, /legal/terms). */
export const LEGAL_SLUGS = ['privacy', 'terms'] as const;
export type LegalSlug = (typeof LEGAL_SLUGS)[number];

export function legalSlug(type: string | undefined): LegalSlug {
  return type === 'PRIVACY_POLICY' ? 'privacy' : 'terms';
}

export const publicApi = {
  company: () => unwrap(api.GET('/api/v1/public/company')),
  legalDocuments: () => unwrap(api.GET('/api/v1/public/legal')),
  legalDocument: (slug: string) => unwrap(api.GET('/api/v1/public/legal/{slug}', { params: { path: { slug } } })),
  /** Bu günün bonus qaydaları (xoşbəxt saatlar, kampaniyalar) — kartın üstündəki banner. */
  bonusRules: () => unwrap(api.GET('/api/v1/public/bonus-rules')),
};

export const customerAuthApi = {
  session: async (): Promise<CustomerSessionState> =>
    ((await unwrap(api.GET('/api/v1/auth/session'))).state ?? 'NONE') as CustomerSessionState,
  requestOtp: (phone: string, language: string, tapToken?: string) =>
    unwrap(api.POST('/api/v1/auth/otp/request', { body: { phone, language, tapToken } })),
  verifyOtp: (challengeId: string, code: string) =>
    unwrap(api.POST('/api/v1/auth/otp/verify', { body: { challengeId, code } })),
  register: (body: Schemas['RegisterRequest']) => unwrap(api.POST('/api/v1/auth/register', { body })),
  acceptConsents: (acceptedDocuments: DocumentAcceptance[]) =>
    unwrap(api.POST('/api/v1/auth/consents', { body: { acceptedDocuments } })),
  logout: () => unwrap(api.POST('/api/v1/auth/logout')),
  requestStepUp: () => unwrap(api.POST('/api/v1/auth/step-up/otp/request')),
  verifyStepUp: (challengeId: string, code: string) =>
    unwrap(api.POST('/api/v1/auth/step-up/otp/verify', { body: { challengeId, code } })),
};

/** Web push: serverin açıq açarı (giriş tələb etmir) və bu cihazın abunəsi. */
export const pushApi = {
  config: () => unwrap(api.GET('/api/v1/public/push')),
  subscribe: (body: Schemas['SubscriptionRequest']) => unwrap(api.POST('/api/v1/me/push-subscriptions', { body })),
  unsubscribe: (endpoint: string) => unwrap(api.POST('/api/v1/me/push-subscriptions/remove', { body: { endpoint } })),
};

/** Dost dəvəti: proqram (giriş tələb etmir) və müştərinin öz kodu ilə nəticələri. */
export const referralApi = {
  program: () => unwrap(api.GET('/api/v1/public/referral-program')),
  mine: () => unwrap(api.GET('/api/v1/me/referral')),
};

export const meApi = {
  /** Müştərinin səviyyəsi və növbəti səviyyəyə irəliləyiş. */
  tier: () => unwrap(api.GET('/api/v1/me/tier')),
  me: () => unwrap(api.GET('/api/v1/me')),
  update: (body: Schemas['UpdateRequest']) => unwrap(api.PATCH('/api/v1/me', { body })),
  deleteAccount: () => unwrap(api.DELETE('/api/v1/me')),
  devices: () => unwrap(api.GET('/api/v1/me/sessions')),
  revokeDevice: (sessionId: string) =>
    unwrap(api.DELETE('/api/v1/me/sessions/{sessionId}', { params: { path: { sessionId } } })),
  revokeOtherDevices: () => unwrap(api.POST('/api/v1/me/sessions/revoke-others')),
};

/** NFC toxunuşundan sonrakı səhifə: tokenin vəziyyəti və möhürün alınması. */
export const tapApi = {
  status: (token: string) => unwrap(api.GET('/api/v1/taps/{token}', { params: { path: { token } } })),
  claim: (token: string) => unwrap(api.POST('/api/v1/taps/{token}/claim', { params: { path: { token } } })),
};

export const giftCardApi = {
  list: () => unwrap(api.GET('/api/v1/me/gift-cards')),
};
