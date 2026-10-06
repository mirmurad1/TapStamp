import type { SessionState } from '../api/adminApi';

/** Sessiyanın vəziyyətinə uyğun səhifə: giriş → TOTP → TOTP qeydiyyatı → panel. */
export function pathForState(state: SessionState | undefined): string {
  switch (state) {
    case 'PENDING_MFA':
      return '/mfa';
    case 'MFA_ENROLLMENT':
      return '/mfa/setup';
    case 'ACTIVE':
      return '/';
    default:
      return '/login';
  }
}
