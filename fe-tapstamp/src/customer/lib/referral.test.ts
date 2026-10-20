import { afterEach, describe, expect, it, vi } from 'vitest';

import { clearReferralCode, isReferralCode, readReferralCode, storeReferralCode } from './referral';

describe('dəvət kodunun saxlanması', () => {
  afterEach(() => {
    clearReferralCode();
    vi.useRealTimers();
  });

  it('kod böyük hərflə saxlanılır, qeydiyyatdan sonra silinir', () => {
    storeReferralCode('k7p2qxab');
    expect(readReferralCode()).toBe('K7P2QXAB');
    clearReferralCode();
    expect(readReferralCode()).toBeUndefined();
  });

  it('7 gündən köhnə kod nəzərə alınmır', () => {
    vi.useFakeTimers();
    storeReferralCode('K7P2QXAB');
    vi.advanceTimersByTime(8 * 24 * 60 * 60 * 1000);
    expect(readReferralCode()).toBeUndefined();
  });

  it('format yoxlanılır (8 simvol, 0/1/O/I yoxdur)', () => {
    expect(isReferralCode('K7P2QXAB')).toBe(true);
    expect(isReferralCode('K7P2QXA')).toBe(false);
    expect(isReferralCode('K7P2QXA0')).toBe(false);
    expect(isReferralCode(undefined)).toBe(false);
  });
});
