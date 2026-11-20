/** Başlanğıc siyahısının addımları (server qaytarır): məcburi və tövsiyə olunanlar. */
export const SETUP_STEPS = [
  'BRANDING',
  'REWARD_RULE',
  'BRANCH',
  'TEAM',
  'NFC_TAG',
  'FEEDBACK',
  'GOOGLE_REVIEW',
  'REFERRALS',
  'TIERS',
  'REMINDERS',
  'BONUS_RULES',
  'CATALOG',
] as const;

export type SetupStepKey = (typeof SETUP_STEPS)[number];

/** Naməlum addım (server yeni addım əlavə edib, frontend hələ bilmir) göstərilmir. */
export function setupStepKey(value: string | undefined): SetupStepKey | undefined {
  return SETUP_STEPS.find((key) => key === value);
}
