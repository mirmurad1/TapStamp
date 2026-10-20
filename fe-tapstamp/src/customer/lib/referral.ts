/**
 * Dost dəvətinin kodu dəvət linkindən ({@code /r/:code}) qeydiyyata qədər brauzerdə saxlanılır: müştəri linki açıb
 * sonra (məs. kafedə NFC toxunuşu ilə) qeydiyyatdan keçə bilər. 7 gündən köhnə kod nəzərə alınmır. Yaddaş əlçatan
 * deyilsə (gizli rejim), kod sadəcə itir — qeydiyyat adi qaydada davam edir.
 */
const KEY = 'tapstamp.referral';
const MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;
const CODE = /^[A-Za-z2-9]{8}$/;

export function isReferralCode(value: string | undefined): value is string {
  return Boolean(value && CODE.test(value));
}

export function storeReferralCode(code: string): void {
  try {
    localStorage.setItem(KEY, JSON.stringify({ code: code.toUpperCase(), at: Date.now() }));
  } catch {
    // Yaddaş bağlıdır — dəvət nəzərə alınmayacaq
  }
}

export function readReferralCode(): string | undefined {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) {
      return undefined;
    }
    const saved = JSON.parse(raw) as { code?: string; at?: number };
    if (!isReferralCode(saved.code) || !saved.at || Date.now() - saved.at > MAX_AGE_MS) {
      return undefined;
    }
    return saved.code;
  } catch {
    return undefined;
  }
}

export function clearReferralCode(): void {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // Yaddaş bağlıdır
  }
}
